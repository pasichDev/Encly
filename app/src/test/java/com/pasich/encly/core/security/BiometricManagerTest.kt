package com.pasich.encly.core.security

import android.app.Application
import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.testutil.tempVaultStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager
import android.hardware.biometrics.BiometricManager as PlatformBiometricManager

/**
 * The biometric slot's guard rails: nothing is ever released or enrolled without a strong
 * sensor, a well-formed slot and the Keystore key behind it. The prompt itself needs a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BiometricManagerTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val store = tempVaultStore()
    private lateinit var manager: KeystoreBiometricManager

    @Before
    fun setUp() {
        manager = KeystoreBiometricManager(context, store)
    }

    private fun sensor(available: Boolean) {
        val platform = context.getSystemService(Context.BIOMETRIC_SERVICE) as PlatformBiometricManager
        Shadow.extract<ShadowBiometricManager>(platform).setCanAuthenticate(available)
    }

    private fun activity(): BiometricHost =
        ActivityBiometricHost(Robolectric.buildActivity(FragmentActivity::class.java).get())

    @Test
    fun withoutASensorBiometricsAreUnavailable() {
        sensor(false)

        assertFalse(manager.isStrongBiometricAvailable())
        assertEquals(BiometricStatus.UNAVAILABLE, manager.strongBiometricStatus())
    }

    @Test
    fun aUsableStrongSensorIsAvailable() {
        sensor(true)

        assertTrue(manager.isStrongBiometricAvailable())
        assertEquals(BiometricStatus.AVAILABLE, manager.strongBiometricStatus())
    }

    @Test
    fun withoutAStoredSlotThereIsNoSlot() {
        assertFalse(manager.hasSlot())
    }

    @Test
    fun aStoredSlotWithoutItsKeystoreKeyIsNotASlot() {
        store.edit { putBytes("bio.slot", ByteArray(60)) }

        assertFalse(manager.hasSlot())
    }

    @Test
    fun enrollingWithoutAStrongSensorFailsAndStoresNothing() {
        sensor(false)
        var result: Boolean? = null

        manager.enroll(activity(), ByteArray(32)) { result = it }

        assertEquals(false, result)
        assertFalse(store.contains("bio.slot"))
    }

    @Test
    fun enrollingAKeyOfTheWrongSizeFailsBeforeAnythingIsTouched() {
        sensor(true)
        store.edit { putBytes("bio.slot", ByteArray(60)) }
        val results = mutableListOf<Boolean>()

        manager.enroll(activity(), ByteArray(31)) { results += it }
        manager.enroll(activity(), ByteArray(33)) { results += it }

        assertEquals(listOf(false, false), results)
        assertTrue("the existing slot is left alone", store.contains("bio.slot"))
    }

    @Test
    fun unlockingWithoutASlotYieldsNothing() {
        sensor(true)
        var called = false
        var dek: ByteArray? = byteArrayOf(1)

        manager.unlock(activity()) {
            called = true
            dek = it
        }

        assertTrue(called)
        assertNull(dek)
    }

    @Test
    fun unlockingWithoutAStrongSensorYieldsNothingAndKeepsTheSlot() {
        sensor(false)
        store.edit { putBytes("bio.slot", ByteArray(60)) }
        var dek: ByteArray? = byteArrayOf(1)

        manager.unlock(activity()) { dek = it }

        assertNull(dek)
        assertTrue(store.contains("bio.slot"))
    }

    @Test
    fun aTruncatedSlotNeverReachesThePrompt() {
        sensor(true)
        store.edit { putBytes("bio.slot", ByteArray(12)) }
        var dek: ByteArray? = byteArrayOf(1)

        manager.unlock(activity()) { dek = it }

        assertNull(dek)
    }

    @Test
    fun aSlotWhoseKeystoreKeyIsGoneUnlocksNothing() {
        sensor(true)
        store.edit { putBytes("bio.slot", ByteArray(60)) }
        var called = false
        var dek: ByteArray? = byteArrayOf(1)

        manager.unlock(activity()) {
            called = true
            dek = it
        }

        assertTrue("the caller is always answered", called)
        assertNull(dek)
    }

    @Test
    fun authenticatingWithoutAStrongSensorReportsAnErrorInsteadOfPrompting() {
        sensor(false)
        val events = mutableListOf<String>()
        val callback = object : BiometricManager.BiometricCallback {
            override fun onSuccess() {
                events += "success"
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                events += "error:$errorCode"
            }

            override fun onFailed() {
                events += "failed"
            }
        }

        manager.authenticate(activity(), BiometricManager.BiometricType.GENERAL, callback)

        assertEquals(listOf("error:-1"), events)
    }

    @Test
    fun disablingDropsEveryBiometricEntryAndNothingElse() {
        store.edit {
            putBytes("bio.slot", ByteArray(60))
            putInt("bio.extra", 1)
            putInt("pin.keep", 2)
        }

        manager.disable()

        assertFalse(store.contains("bio.slot"))
        assertFalse(store.contains("bio.extra"))
        assertEquals(2, store.getInt("pin.keep", 0))
    }
}
