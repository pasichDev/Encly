package com.pasich.encly.core.security

import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.tempVaultFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A PIN is 6 to 12 digits, for the PIN, the wipe PIN and the duress PIN alike. The KDF is the same whatever
 * the length, so PINs made before this option derive exactly the keys they did (the golden chain test pins that).
 */
class PinLengthTest {
    private val store = VaultStore(tempVaultFile())
    private val factor = FakePinFactor()
    private val manager = AuthenticationManager(store, factor, FakeLockoutClock())
    private val dek = ByteArray(32) { (it * 5).toByte() }

    @Test
    fun theLimitsAreSixAndTwelve() {
        assertEquals(6, MIN_PIN_LENGTH)
        assertEquals(12, MAX_PIN_LENGTH)
    }

    @Test
    fun everyLengthFromSixToTwelveMakesAPinThatUnlocks() {
        for (length in MIN_PIN_LENGTH..MAX_PIN_LENGTH) {
            val digits = "9876543210987".take(length)
            assertTrue("length $length", manager.configurePin(digits.toCharArray(), dek))

            val unlocked = manager.unlockWithPin(digits.toCharArray())

            assertArrayEquals("length $length", dek, (unlocked as PinUnlock.Success).dek)
            assertTrue(manager.verifyPinAuth(digits.toCharArray()))
        }
    }

    @Test
    fun shorterAndLongerPinsAreRefusedAndNothingIsWritten() {
        for (digits in listOf("", "1", "12345", "1234567890123", "12345678901234567890")) {
            assertFalse(digits, manager.configurePin(digits.toCharArray(), dek))
        }
        for (typed in listOf("12345a", "123 456", "١٢٣٤٥٦", "12.3456", "1234567890-1")) {
            assertFalse(typed, manager.configurePin(typed.toCharArray(), dek))
        }

        assertFalse(manager.hasPinSlot())
    }

    @Test
    fun aPinIsNotItsOwnPrefixOrExtension() {
        assertTrue(manager.configurePin("12345678".toCharArray(), dek))

        assertEquals(PinUnlock.WrongPin, manager.unlockWithPin("123456".toCharArray()))
        assertEquals(PinUnlock.WrongPin, manager.unlockWithPin("123456789".toCharArray()))
        assertEquals(PinUnlock.WrongPin, manager.unlockWithPin("0123456".toCharArray()))
        assertTrue(manager.unlockWithPin("12345678".toCharArray()) is PinUnlock.Success)
    }

    @Test
    fun aMalformedLengthCountsAsAFailedAttemptLikeAWrongPin() {
        assertTrue(manager.configurePin("123456".toCharArray(), dek))

        repeat(AuthenticationManager.MAX_ATTEMPTS) {
            assertEquals(PinUnlock.WrongPin, manager.unlockWithPin("12345".toCharArray()))
        }

        assertEquals(AuthenticationManager.FIRST_LOCKOUT_MS, manager.remainingLockoutMillis())
    }

    @Test
    fun thePinWipePinAndDuressPinMayAllHaveDifferentLengths() {
        assertTrue(manager.configurePin("1234567".toCharArray(), dek))
        assertEquals(WipePinChange.SET, manager.configureWipePin("876543210987".toCharArray()))
        assertEquals(DuressPinChange.SET_NEW_VAULT, manager.configureDuressPin("246810".toCharArray(), dek) { true })

        val duress = manager.unlockWithPin("246810".toCharArray())
        val real = manager.unlockWithPin("1234567".toCharArray())

        assertTrue(duress is PinUnlock.Decoy)
        assertArrayEquals(dek, (real as PinUnlock.Success).dek)
        assertEquals(PinUnlock.WrongPin, manager.unlockWithPin("24681".toCharArray()))
        assertEquals(32, (duress as PinUnlock.Decoy).dek.size)
        // Typed last, as the wipe PIN erases the vault.
        assertTrue(manager.unlockWithPin("876543210987".toCharArray()) is PinUnlock.Erased)
    }

    @Test
    fun theSlotsOfPinsOfDifferentLengthsAreTheSameSize() {
        assertTrue(manager.configurePin("123456".toCharArray(), dek))
        val short = store.getBytes("pin.slot")!!.size
        val wipeShort = store.getBytes("pin.wipe.slot")!!.size

        assertTrue(manager.configurePin("123456789012".toCharArray(), dek))
        assertEquals(WipePinChange.SET, manager.configureWipePin("12345678".toCharArray()))

        assertEquals(short, store.getBytes("pin.slot")!!.size)
        assertEquals(wipeShort, store.getBytes("pin.wipe.slot")!!.size)
    }

    @Test
    fun theWipeAndDuressPinsFollowTheSameLengthRule() {
        assertTrue(manager.configurePin("123456".toCharArray(), dek))

        assertEquals(WipePinChange.FAILED, manager.configureWipePin("12345".toCharArray()))
        assertEquals(WipePinChange.FAILED, manager.configureWipePin("1234567890123".toCharArray()))
        assertEquals(DuressPinChange.FAILED, manager.configureDuressPin("12345".toCharArray(), dek) { true })
        assertEquals(DuressPinChange.FAILED, manager.configureDuressPin("1234567890123".toCharArray(), dek) { true })

        assertEquals(WipePinChange.SET, manager.configureWipePin("1234567".toCharArray()))
        assertEquals(DuressPinChange.SET_NEW_VAULT, manager.configureDuressPin("12345678".toCharArray(), dek) { true })
    }

    @Test
    fun aPinIsStillRefusedAsWipeOrDuressPinWhenItIsTheSamePinWhateverItsLength() {
        assertTrue(manager.configurePin("12345678".toCharArray(), dek))

        assertEquals(WipePinChange.SAME_AS_PIN, manager.configureWipePin("12345678".toCharArray()))
        assertEquals(DuressPinChange.SAME_AS_PIN, manager.configureDuressPin("12345678".toCharArray(), dek) { true })
        assertEquals(WipePinChange.SET, manager.configureWipePin("123456789".toCharArray()))
    }
}
