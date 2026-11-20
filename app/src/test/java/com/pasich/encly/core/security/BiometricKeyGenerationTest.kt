package com.pasich.encly.core.security

import android.app.Application
import android.content.Context
import android.security.keystore.KeyProperties
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.testutil.FakeAndroidKeyStore
import com.pasich.encly.testutil.tempVaultStore
import org.junit.After
import org.junit.Assert.assertEquals
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
 * The biometric slot's Keystore key is made per use, and from Android 11 (R) its parameters name the strong class.
 * The prompt itself needs a device, so what is checked is the spec the key was asked for. The branch below R
 * (validity duration -1) is not reached here: Robolectric's androidx.biometric reports no strong biometrics on
 * Android 10, so the enrolment stops before a key is made; that branch is left to the device smoke test (issue #17).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35], application = Application::class)
class BiometricKeyGenerationTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val store = tempVaultStore()
    private val texts = BiometricTextSource().apply {
        val prompt = BiometricManager.PromptConfig("title", "subtitle")
        set(BiometricTexts("unavailable", "cancel", prompt, prompt, prompt, prompt))
    }
    private lateinit var manager: KeystoreBiometricManager

    @Before
    fun setUp() {
        FakeAndroidKeyStore.install()
        manager = KeystoreBiometricManager(context, store, texts)
        val platform = context.getSystemService(Context.BIOMETRIC_SERVICE) as PlatformBiometricManager
        Shadow.extract<ShadowBiometricManager>(platform).setCanAuthenticate(true)
    }

    @After
    fun tearDown() = FakeAndroidKeyStore.uninstall()

    @Test
    fun enrollingAsksForAPerUseStrongBiometricAesKey() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

        // The prompt cannot be answered here; the key is made before it is shown.
        manager.enroll(ActivityBiometricHost(activity), ByteArray(32)) { }

        val spec = FakeAndroidKeyStore.specs.single()
        assertEquals(256, spec.keySize)
        assertEquals(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT, spec.purposes)
        assertTrue(spec.isUserAuthenticationRequired)
        assertTrue(spec.isInvalidatedByBiometricEnrollment)
        assertEquals(0, spec.userAuthenticationValidityDurationSeconds)
        assertEquals(KeyProperties.AUTH_BIOMETRIC_STRONG, spec.userAuthenticationType)
    }
}
