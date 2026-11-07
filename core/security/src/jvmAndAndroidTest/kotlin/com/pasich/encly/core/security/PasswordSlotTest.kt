package com.pasich.encly.core.security

import com.pasich.encly.core.security.AuthenticationManager.Companion.MAX_ATTEMPTS
import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.tempVaultFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.text.Normalizer

/**
 * The password slot: its own salt and KDF domain over the PIN's chain and device key, the same lockout
 * counters, and the rule that one of PIN and password always remains.
 */
class PasswordSlotTest {
    private val file = tempVaultFile()
    private val store = VaultStore(file)
    private val factor = FakePinFactor()
    private val clock = FakeLockoutClock()
    private val manager = AuthenticationManager(store, factor, clock)
    private val dek = ByteArray(32) { (it + 3).toByte() }

    // --- setting a password ------------------------------------------------------------------

    @Test
    fun aPasswordUnlocksTheSameDekAsThePin() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))

        val byPassword = manager.unlockWithPassword(pw())
        val byPin = manager.unlockWithPin(pin())

        assertArrayEquals(dek, (byPassword as PinUnlock.Success).dek)
        assertArrayEquals(dek, (byPin as PinUnlock.Success).dek)
        assertTrue(manager.hasPasswordSlot())
    }

    @Test
    fun aVaultWithOnlyAPasswordUnlocksWithIt() {
        // The vault is made around a PIN; the PIN is removed once the password stands next to it.
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        assertTrue(manager.removePin(dek))

        assertFalse(manager.hasPinSlot())
        assertArrayEquals(dek, (manager.unlockWithPassword(pw()) as PinUnlock.Success).dek)
        assertEquals(PinUnlock.WrongPin, manager.unlockWithPin(pin()))
        assertEquals(AuthStrategy.PIN, manager.isAuthStrategy())
    }

    @Test
    fun aWrongPasswordNeverUnwrapsTheDekAndIsCounted() {
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))

        assertEquals(PinUnlock.WrongPin, manager.unlockWithPassword("wrong password".toCharArray()))

        assertEquals(PinUnlock.WrongPin, manager.unlockWithPassword(pw("")))
        assertTrue(manager.unlockWithPassword(pw()) is PinUnlock.Success)
    }

    @Test
    fun aPasswordOfAnotherVaultOrWithoutASlotOpensNothing() {
        assertEquals(PinUnlock.WrongPin, manager.unlockWithPassword(pw()))
        assertEquals(0L, manager.remainingLockoutMillis())
    }

    @Test
    fun thePinDoesNotOpenThePasswordSlotAndTheOtherWayAround() {
        // Eight to twelve digits are a valid password and a valid PIN alike: each opens only its own slot.
        val digits = "12345678"
        assertTrue(manager.configurePin(digits.toCharArray(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword("87654321".toCharArray(), dek))

        assertEquals(PinUnlock.WrongPin, manager.unlockWithPassword(digits.toCharArray()))
        assertEquals(PinUnlock.WrongPin, manager.unlockWithPin("87654321".toCharArray()))
        assertTrue(manager.unlockWithPin(digits.toCharArray()) is PinUnlock.Success)
        assertTrue(manager.unlockWithPassword("87654321".toCharArray()) is PinUnlock.Success)
    }

    @Test
    fun theSlotIsUselessWithoutTheDeviceFactor() {
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        val otherDevice =
            AuthenticationManager(VaultStore(file), FakePinFactor().apply { reset(PinKeySlot.A) }, FakeLockoutClock())

        assertEquals(PinUnlock.WrongPin, otherDevice.unlockWithPassword(pw()))
    }

    @Test
    fun everyGuessRunsThroughTheDeviceFactorOnce() {
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        val before = factor.calls

        repeat(3) { manager.unlockWithPassword("guess number $it".toCharArray()) }

        assertEquals(before + 3, factor.calls)
    }

    @Test
    fun aNewPasswordGetsANewSaltAndReplacesTheOldOne() {
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        val first = store.getBytes("pin.password.slot")!!

        assertEquals(PasswordChange.SET, manager.changePassword("another password".toCharArray(), dek))
        val second = store.getBytes("pin.password.slot")!!

        assertEquals(first.size, second.size)
        assertNotEquals(first.copyOfRange(1, 17).toList(), second.copyOfRange(1, 17).toList())
        assertEquals(PinUnlock.WrongPin, manager.unlockWithPassword(pw()))
        assertTrue(manager.unlockWithPassword("another password".toCharArray()) is PinUnlock.Success)
    }

    @Test
    fun theSlotIsAsLongAsAPinSlotAndSaysItsVersion() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))

        val slot = store.getBytes("pin.password.slot")!!

        assertEquals(store.getBytes("pin.slot")!!.size, slot.size)
        assertEquals(1, slot[0].toInt())
        assertEquals(3, store.getBytes("pin.slot")!![0].toInt())
    }

    // --- what a password is -------------------------------------------------------------------

    @Test
    fun aPasswordNeedsEightToOneHundredTwentyEightCharacters() {
        assertEquals(PasswordChange.INVALID, manager.changePassword("1234567".toCharArray(), dek))
        assertEquals(PasswordChange.INVALID, manager.changePassword("".toCharArray(), dek))
        assertEquals(PasswordChange.INVALID, manager.changePassword("x".repeat(129).toCharArray(), dek))
        assertFalse(manager.hasPasswordSlot())

        assertEquals(PasswordChange.SET, manager.changePassword("x".repeat(8).toCharArray(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword("y".repeat(128).toCharArray(), dek))
        assertTrue(manager.unlockWithPassword("y".repeat(128).toCharArray()) is PinUnlock.Success)
    }

    @Test
    fun charactersAreCodePointsNotUtf16Units() {
        // Four emoji are eight UTF-16 units but four characters: with four more letters they make eight.
        val emoji = "🔑"
        assertEquals(PasswordChange.INVALID, manager.changePassword((emoji + emoji + emoji + "abc").toCharArray(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword((emoji.repeat(4) + "abcd").toCharArray(), dek))
        assertTrue(manager.unlockWithPassword((emoji.repeat(4) + "abcd").toCharArray()) is PinUnlock.Success)
    }

    @Test
    fun theSameWordTypedComposedOrDecomposedIsTheSamePassword() {
        val composed = "Pässwörd-日本語"
        val decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD)
        assertNotEquals(composed.length, decomposed.length)
        assertEquals(PasswordChange.SET, manager.changePassword(decomposed.toCharArray(), dek))

        assertTrue(manager.unlockWithPassword(composed.toCharArray()) is PinUnlock.Success)
        assertTrue(manager.unlockWithPassword(decomposed.toCharArray()) is PinUnlock.Success)
        assertTrue(manager.verifyPasswordAuth(composed.toCharArray()))
    }

    @Test
    fun theCallersPasswordIsLeftForTheCallerToWipe() {
        val typed = pw()
        assertEquals(PasswordChange.SET, manager.changePassword(typed, dek))
        manager.unlockWithPassword(typed)

        assertArrayEquals(pw(), typed)
    }

    @Test
    fun aPasswordNeedsAFullLengthKey() {
        assertEquals(PasswordChange.FAILED, manager.changePassword(pw(), ByteArray(31)))
        assertFalse(manager.hasPasswordSlot())
    }

    // --- lockout, shared with the PIN -----------------------------------------------------------

    @Test
    fun wrongPinsAndWrongPasswordsCountTowardsTheSameLockout() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))

        repeat(MAX_ATTEMPTS - 2) { manager.unlockWithPin("000000".toCharArray()) }
        assertEquals(0L, manager.remainingLockoutMillis())
        repeat(2) { manager.unlockWithPassword("not the password".toCharArray()) }

        assertEquals(AuthenticationManager.FIRST_LOCKOUT_MS, manager.remainingLockoutMillis())
        assertEquals(PinUnlock.LockedOut, manager.unlockWithPin(pin()))
        assertEquals(PinUnlock.LockedOut, manager.unlockWithPassword(pw()))
        assertFalse(manager.verifyPasswordAuth(pw()))
    }

    @Test
    fun aRunningLockoutRefusesTheRightPasswordWithoutCountingIt() {
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        repeat(MAX_ATTEMPTS) { manager.unlockWithPassword("wrong wrong".toCharArray()) }

        assertEquals(PinUnlock.LockedOut, manager.unlockWithPassword(pw()))

        assertEquals(AuthenticationManager.FIRST_LOCKOUT_MS, manager.remainingLockoutMillis())
    }

    @Test
    fun theRightPasswordEndsTheMissesLikeTheRightPin() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        repeat(MAX_ATTEMPTS - 1) { manager.unlockWithPin("000000".toCharArray()) }

        assertTrue(manager.unlockWithPassword(pw()) is PinUnlock.Success)

        repeat(MAX_ATTEMPTS - 1) { manager.unlockWithPassword("wrong wrong".toCharArray()) }
        assertEquals(0L, manager.remainingLockoutMillis())
    }

    @Test
    fun aLostDeviceKeyIsReportedAndNotCountedAsAGuess() {
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        factor.lost = true

        repeat(MAX_ATTEMPTS + 2) { assertEquals(PinUnlock.KeyLost, manager.unlockWithPassword(pw())) }

        assertEquals(0L, manager.remainingLockoutMillis())
    }

    @Test
    fun aTransientKeystoreFailureIsNotCountedEither() {
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        factor.failing = true

        repeat(MAX_ATTEMPTS + 2) { assertEquals(PinUnlock.Failed, manager.unlockWithPassword(pw())) }

        factor.failing = false
        assertEquals(0L, manager.remainingLockoutMillis())
        assertTrue(manager.unlockWithPassword(pw()) is PinUnlock.Success)
    }

    // --- re-authentication ----------------------------------------------------------------------

    @Test
    fun verifyPasswordAuthIsTrueOnlyForTheRightPassword() {
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))

        assertTrue(manager.verifyPasswordAuth(pw()))
        assertFalse(manager.verifyPasswordAuth("wrong password".toCharArray()))
    }

    @Test
    fun aDecoySessionNeverTestsTheRealPasswordButTheAttemptIsCounted() {
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))

        assertFalse(manager.verifyPasswordAuth(pw(), decoy = true))

        repeat(MAX_ATTEMPTS - 1) { manager.verifyPasswordAuth(pw(), decoy = true) }
        assertEquals(AuthenticationManager.FIRST_LOCKOUT_MS, manager.remainingLockoutMillis())
    }

    // --- one of PIN and password always remains ---------------------------------------------------

    @Test
    fun thePasswordCannotBeRemovedWhileItIsTheOnlyWayIn() {
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))

        assertFalse(manager.removePassword())

        assertTrue(manager.unlockWithPassword(pw()) is PinUnlock.Success)
    }

    @Test
    fun thePasswordCanBeRemovedWhileThePinStands() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))

        assertTrue(manager.removePassword())

        assertFalse(manager.hasPasswordSlot())
        assertEquals(PinUnlock.WrongPin, manager.unlockWithPassword(pw()))
        assertTrue(manager.unlockWithPin(pin()) is PinUnlock.Success)
    }

    @Test
    fun thePinCannotBeRemovedWithoutAPassword() {
        assertTrue(manager.configurePin(pin(), dek))

        assertFalse(manager.removePin(dek))

        assertTrue(manager.hasPinSlot())
        assertTrue(manager.unlockWithPin(pin()) is PinUnlock.Success)
    }

    @Test
    fun removingThePinTurnsOffTheWipeAndDuressPinsAndKeepsTheShapeOfTheStore() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(WipePinChange.SET, manager.configureWipePin("987654".toCharArray()))
        assertEquals(
            DuressPinChange.SET_NEW_VAULT,
            manager.configureDuressPin("246810".toCharArray(), dek) { true },
        )
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        val before = store.keys() - "pin.password.slot"
        val sizes = before.associateWith { store.getBytes(it)!!.size }

        assertTrue(manager.removePin(dek))

        assertFalse(manager.hasPinSlot())
        assertEquals(before - "pin.slot", store.keys() - "pin.password.slot")
        for (key in before - "pin.slot") assertEquals(key, sizes[key], store.getBytes(key)!!.size)
        // The decoy vault keeps its notes: its duress PIN is turned off, not forgotten.
        assertTrue(manager.hasDecoyVault(dek))
        assertTrue(manager.duressPinTurnedOff(dek))
        assertTrue(manager.unlockWithPassword(pw()) is PinUnlock.Success)
    }

    @Test
    fun aPinAddedAgainStartsAsAFirstPinWithoutAWipeOrDuressPin() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(WipePinChange.SET, manager.configureWipePin("987654".toCharArray()))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        assertTrue(manager.removePin(dek))

        assertTrue(manager.configurePin("246810".toCharArray(), dek))

        // The old wipe PIN opens nothing: it was bound to the salt of the PIN slot that is gone.
        assertEquals(PinUnlock.WrongPin, manager.unlockWithPin("987654".toCharArray()))
        assertTrue(manager.unlockWithPin("246810".toCharArray()) is PinUnlock.Success)
        assertTrue(manager.unlockWithPassword(pw()) is PinUnlock.Success)
    }

    // --- the device key -------------------------------------------------------------------------

    @Test
    fun aNewPinAfterTheDeviceKeyWasResetDropsThePasswordWithANotice() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        factor.lost = true

        assertTrue(manager.configurePin("246810".toCharArray(), dek))

        assertFalse(manager.hasPasswordSlot())
        assertTrue(manager.passwordTurnedOff())
        assertTrue(manager.unlockWithPin("246810".toCharArray()) is PinUnlock.Success)
        // Setting the password again clears the notice.
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        assertFalse(manager.passwordTurnedOff())
    }

    @Test
    fun aPasswordSetAfterTheDeviceKeyWasResetDropsThePinSlotThatCanNeverOpen() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(WipePinChange.SET, manager.configureWipePin("987654".toCharArray()))
        factor.lost = true

        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))

        assertFalse(manager.hasPinSlot())
        assertTrue(manager.unlockWithPassword(pw()) is PinUnlock.Success)
    }

    @Test
    fun aWipePinEraseDropsThePasswordWithTheRestOfTheOldVault() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(WipePinChange.SET, manager.configureWipePin("987654".toCharArray()))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))

        val erased = manager.unlockWithPin("987654".toCharArray())

        assertTrue(erased is PinUnlock.Erased)
        assertFalse(manager.hasPasswordSlot())
        assertEquals(PinUnlock.WrongPin, manager.unlockWithPassword(pw()))
    }

    @Test
    fun wipeDropsThePasswordAndTheStateAnOlderBuildWouldLeaveAlone() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))

        manager.wipe()

        assertFalse(manager.hasPasswordSlot())
        assertFalse(manager.hasPinSlot())
    }

    @Test
    fun theStoreFileWithAPasswordSlotIsStillTheFormatAnOlderBuildReads() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        val bytes = file.readBytes()

        // Read it the way the earliest build did: java.io, with the slot entry as one more key it does not know.
        val input = DataInputStream(ByteArrayInputStream(bytes, 0, bytes.size - 32))
        val magic = ByteArray(8).also { input.readFully(it) }
        val entries = (0 until input.readInt()).associate {
            val key = input.readUTF()
            key to ByteArray(input.readInt()).also { value -> input.readFully(value) }
        }

        assertEquals("ENCLYVS3", String(magic, Charsets.US_ASCII))
        assertEquals(0, input.available())
        assertTrue("pin.password.slot" in entries)
        assertArrayEquals(store.getBytes("pin.slot"), entries["pin.slot"])
    }

    @Test
    fun anOlderBuildThatDropsThePinStateDropsThePasswordSlotWithIt() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))

        // What the earlier AuthenticationManager.wipe() did to the store.
        assertTrue(store.edit { removePrefix("pin.") })

        assertFalse(manager.hasPasswordSlot())
        assertFalse(manager.hasPinSlot())
    }

    @Test
    fun theMigrationOfOlderVaultsLeavesThePasswordSlotAlone() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        val slot = store.getBytes("pin.password.slot")!!

        assertTrue(manager.ensureWipeSlot())

        assertArrayEquals(slot, store.getBytes("pin.password.slot"))
    }

    // --- after the recovery phrase ----------------------------------------------------------------

    @Test
    fun afterRecoveryAWorkingPinOrPasswordMayBeKept() {
        assertTrue(manager.configurePin(pin(), dek))
        assertEquals(RecoveryFollowUp.KEEP_OR_CHANGE, manager.recoveryFollowUp())

        assertFalse(manager.removePin(dek))
        assertEquals(PasswordChange.SET, manager.changePassword(pw(), dek))
        assertTrue(manager.removePin(dek))
        assertEquals(RecoveryFollowUp.KEEP_OR_CHANGE, manager.recoveryFollowUp())
    }

    @Test
    fun afterRecoveryANewPinIsRequiredWhenNothingCanBeUsed() {
        assertEquals(RecoveryFollowUp.MUST_SET_PIN, manager.recoveryFollowUp())
    }

    @Test
    fun afterRecoveryANewPinIsRequiredWhenTheDeviceKeyIsLostOrALockoutRuns() {
        assertTrue(manager.configurePin(pin(), dek))
        factor.lost = true
        assertEquals(RecoveryFollowUp.MUST_SET_PIN, manager.recoveryFollowUp())

        factor.lost = false
        factor.reset(PinKeySlot.A)
        assertTrue(manager.configurePin(pin(), dek))
        repeat(MAX_ATTEMPTS) { manager.unlockWithPin("000000".toCharArray()) }
        assertEquals(RecoveryFollowUp.MUST_SET_PIN, manager.recoveryFollowUp())
    }

    @Test
    fun aTransientKeystoreFailureIsNotALostPin() {
        assertTrue(manager.configurePin(pin(), dek))
        factor.failing = true

        assertEquals(RecoveryFollowUp.KEEP_OR_CHANGE, manager.recoveryFollowUp())
    }

    private fun pin() = "123456".toCharArray()

    private fun pw(value: String = "correct horse battery") = value.toCharArray()
}
