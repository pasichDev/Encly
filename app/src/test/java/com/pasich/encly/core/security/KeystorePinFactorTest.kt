package com.pasich.encly.core.security

import android.security.keystore.KeyGenParameterSpec
import com.pasich.encly.testutil.FakeAndroidKeyStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException

/**
 * The Keystore-backed PIN factor against an in-memory Keystore that fails on demand: what it
 * generates, which failures mean "the key is gone for good" and which may pass, and that a
 * failed generation never leaves a half-made key behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeystorePinFactorTest {
    private val factor = KeystorePinFactor()
    private val aliasA = "encly_pin_factor_v3"
    private val aliasB = "encly_pin_factor_v3b"

    @Before
    fun setUp() = FakeAndroidKeyStore.install()

    @After
    fun tearDown() = FakeAndroidKeyStore.uninstall()

    private fun failure(block: () -> Unit): PinFactorException {
        try {
            block()
        } catch (e: PinFactorException) {
            return e
        }
        fail("expected a PinFactorException")
        error("unreachable")
    }

    @Test
    fun theFirstCallCreatesTheKeyAndLaterCallsKeepIt() {
        assertTrue(factor.ensureKey(PinKeySlot.A))
        val first = factor.mac(PinKeySlot.A, byteArrayOf(1, 2, 3))

        assertFalse(factor.ensureKey(PinKeySlot.A))
        assertArrayEquals(first, factor.mac(PinKeySlot.A, byteArrayOf(1, 2, 3)))
        assertEquals(32, first.size)
    }

    @Test
    fun theTwoSlotsKeepTheirOwnKeysUnderTheirOwnAliases() {
        factor.ensureKey(PinKeySlot.A)
        factor.ensureKey(PinKeySlot.B)

        assertEquals(setOf(aliasA, aliasB), FakeAndroidKeyStore.aliases())
        assertFalse(factor.mac(PinKeySlot.A, byteArrayOf(9)).contentEquals(factor.mac(PinKeySlot.B, byteArrayOf(9))))
    }

    @Test
    fun resettingReplacesTheKeySoOldMacsNoLongerMatch() {
        factor.ensureKey(PinKeySlot.A)
        val before = factor.mac(PinKeySlot.A, byteArrayOf(1))

        factor.reset(PinKeySlot.A)

        assertFalse(before.contentEquals(factor.mac(PinKeySlot.A, byteArrayOf(1))))
    }

    @Test
    fun deletingAKeyRemovesItAndMacingWithoutOneIsALostKey() {
        factor.ensureKey(PinKeySlot.A)

        factor.delete(PinKeySlot.A)

        assertFalse(FakeAndroidKeyStore.has(aliasA))
        assertTrue(failure { factor.mac(PinKeySlot.A, byteArrayOf(1)) }.lost)
        assertTrue("a key deleted behind our back is reported as newly created", factor.ensureKey(PinKeySlot.A))
    }

    @Test
    fun deletingAMissingKeyIsHarmless() {
        factor.delete(PinKeySlot.B)

        assertTrue(FakeAndroidKeyStore.aliases().isEmpty())
    }

    @Test
    fun strongBoxIsTriedFirstAndRequiresAnUnlockedDevice() {
        factor.ensureKey(PinKeySlot.A)

        val spec = FakeAndroidKeyStore.specs.single()
        assertTrue(spec.isStrongBoxBacked)
        assertTrue(spec.isUnlockedDeviceRequired)
        assertEquals(256, spec.keySize)
        assertEquals(android.security.keystore.KeyProperties.PURPOSE_SIGN, spec.purposes)
    }

    @Test
    fun aDeviceWithoutStrongBoxFallsBackToTheTeeStillRequiringAnUnlockedDevice() {
        FakeAndroidKeyStore.onGenerate = { if (it.isStrongBoxBacked) throw ProviderException("no StrongBox") }

        assertTrue(factor.ensureKey(PinKeySlot.A))

        val specs = FakeAndroidKeyStore.specs
        assertEquals(listOf(true, false), specs.map(KeyGenParameterSpec::isStrongBoxBacked))
        assertTrue(specs.last().isUnlockedDeviceRequired)
        assertTrue(FakeAndroidKeyStore.has(aliasA))
    }

    @Test
    fun theUnlockedDeviceRequirementIsDroppedOnlyWhenThePlatformRejectsIt() {
        FakeAndroidKeyStore.onGenerate = { if (it.isUnlockedDeviceRequired) throw ProviderException("rejected") }

        factor.ensureKey(PinKeySlot.A)

        val specs = FakeAndroidKeyStore.specs
        assertEquals(
            listOf(true, false, false),
            specs.map(KeyGenParameterSpec::isStrongBoxBacked),
        )
        assertFalse(specs.last().isStrongBoxBacked)
        assertFalse(specs.last().isUnlockedDeviceRequired)
        assertTrue(FakeAndroidKeyStore.has(aliasA))
    }

    @Test
    fun whenNoOptionWorksTheFailureIsTransientAndNoKeyIsLeftBehind() {
        FakeAndroidKeyStore.onGenerate = { throw ProviderException("keystore down") }

        val e = failure { factor.ensureKey(PinKeySlot.A) }

        assertFalse("a failed generation may succeed later", e.lost)
        assertEquals(3, FakeAndroidKeyStore.specs.size)
        assertFalse(FakeAndroidKeyStore.has(aliasA))
    }

    @Test
    fun aKeystoreThatCannotAnswerContainsAliasIsATransientFailure() {
        FakeAndroidKeyStore.aliasFailure = KeyStoreException("locked")

        val e = failure { factor.ensureKey(PinKeySlot.A) }

        assertFalse(e.lost)
        assertTrue(e.cause is KeyStoreException)
    }

    @Test
    fun aRuntimeErrorFromTheKeystoreIsMappedNotLeakedWhenCheckingForTheKey() {
        FakeAndroidKeyStore.aliasFailure = ProviderException("binder died")

        val e = failure { factor.ensureKey(PinKeySlot.A) }

        assertFalse(e.lost)
        assertTrue(e.cause is ProviderException)
    }

    /** The platform's own Keystore error (hidden constructor), as the cause of a failed `getKey`. */
    private fun keystoreError(code: Int): Throwable = Class.forName("android.security.KeyStoreException")
        .getConstructor(Int::class.javaPrimitiveType, String::class.java)
        .newInstance(code, "keystore error") as Throwable

    @Test
    fun anUnrecoverableKeyMeansTheKeyIsLost() {
        factor.ensureKey(PinKeySlot.A)
        // What AndroidKeyStoreSpi.engineGetKey makes of a permanently invalidated key: a message, no cause.
        FakeAndroidKeyStore.getKeyFailure = UnrecoverableKeyException("User changed or deleted their auth credentials")

        assertTrue(failure { factor.mac(PinKeySlot.A, byteArrayOf(1)) }.lost)
    }

    @Test
    fun aKeystoreErrorWhileReadingTheKeyIsNotALostKey() {
        factor.ensureKey(PinKeySlot.A)
        // What the platform makes of any other Keystore error (here SYSTEM_ERROR): the key itself may be intact.
        val failed = UnrecoverableKeyException("Failed to obtain information about key")
        failed.initCause(keystoreError(code = 4))
        FakeAndroidKeyStore.getKeyFailure = failed

        val e = failure { factor.mac(PinKeySlot.A, byteArrayOf(1)) }

        assertFalse("a system error is retried, the vault is not declared lost", e.lost)
        assertTrue(e.cause is UnrecoverableKeyException)

        // The Keystore answers again: the same key still signs.
        FakeAndroidKeyStore.getKeyFailure = null
        assertEquals(32, factor.mac(PinKeySlot.A, byteArrayOf(1)).size)
    }

    @Test
    fun anUnrecoverableKeyWithAnotherCauseIsStillLost() {
        factor.ensureKey(PinKeySlot.A)
        // "Unsupported secret key type": the entry itself is unusable, which no retry mends.
        val failed = UnrecoverableKeyException("Unsupported secret key type")
        failed.initCause(IllegalArgumentException("algorithm"))
        FakeAndroidKeyStore.getKeyFailure = failed

        assertTrue(failure { factor.mac(PinKeySlot.A, byteArrayOf(1)) }.lost)
    }

    @Test
    fun anotherKeystoreErrorWhileReadingTheKeyIsTransient() {
        factor.ensureKey(PinKeySlot.A)
        FakeAndroidKeyStore.getKeyFailure = KeyStoreException("busy")

        val e = failure { factor.mac(PinKeySlot.A, byteArrayOf(1)) }

        assertFalse(e.lost)
        assertNotEquals(null, e.cause)
    }

    @Test
    fun aKeyTheSystemInvalidatedIsLostForGood() {
        factor.ensureKey(PinKeySlot.A)
        FakeAndroidKeyStore.invalidate(aliasA)

        assertTrue(failure { factor.mac(PinKeySlot.A, byteArrayOf(1)) }.lost)
    }

    @Test
    fun deleteSwallowsKeystoreErrorsAndNeverThrows() {
        factor.ensureKey(PinKeySlot.A)
        FakeAndroidKeyStore.aliasFailure = KeyStoreException("locked")
        factor.delete(PinKeySlot.A)

        FakeAndroidKeyStore.aliasFailure = ProviderException("binder died")
        factor.delete(PinKeySlot.A)

        assertTrue("the key could not be removed, and nothing pretended it was", FakeAndroidKeyStore.has(aliasA))
    }

    @Test
    fun theOtherSlotIsTheOtherSlot() {
        assertEquals(PinKeySlot.B, PinKeySlot.A.other)
        assertEquals(PinKeySlot.A, PinKeySlot.B.other)
    }
}
