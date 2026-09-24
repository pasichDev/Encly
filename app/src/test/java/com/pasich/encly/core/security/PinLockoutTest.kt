package com.pasich.encly.core.security

import com.pasich.encly.testutil.InMemorySharedPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIN rules around the key slot: format, lockout escalation and what a right PIN resets. */
class PinLockoutTest {
    private val preferences = InMemorySharedPreferences()
    private val manager = AuthenticationManager(preferences)
    private val dek = ByteArray(DEK_LENGTH) { 0x4C }

    @Test
    fun onlySixDigitsAndAFullLengthKeyMakeAPinSlot() {
        assertFalse(manager.configurePin("12345", dek))
        assertFalse(manager.configurePin("1234567", dek))
        assertFalse(manager.configurePin("12345a", dek))
        assertFalse(manager.configurePin(PIN, ByteArray(DEK_LENGTH - 1)))

        assertFalse(manager.hasPinSlot())
        assertEquals(AuthType.NONE, manager.getAuthType())
    }

    @Test
    fun aMalformedPinCountsAsAFailedAttempt() {
        assertTrue(manager.configurePin(PIN, dek))

        repeat(MAX_ATTEMPTS) { assertNull(manager.unlockWithPin("12ab")) }

        assertTrue(manager.remainingLockoutMillis() in 1..FIRST_LOCKOUT_MS)
    }

    @Test
    fun eachFailureAfterALockoutDoublesTheNextOne() {
        assertTrue(manager.configurePin(PIN, dek))
        repeat(MAX_ATTEMPTS) { manager.unlockWithPin("x") }

        expireLockout()
        manager.unlockWithPin("x")

        assertTrue(manager.remainingLockoutMillis() in FIRST_LOCKOUT_MS + 1..FIRST_LOCKOUT_MS * 2)
    }

    @Test
    fun theLockoutNeverExceedsFifteenMinutes() {
        assertTrue(manager.configurePin(PIN, dek))
        repeat(MAX_ATTEMPTS + 10) {
            expireLockout()
            manager.unlockWithPin("x")
        }

        assertTrue(manager.remainingLockoutMillis() in 1..MAX_LOCKOUT_MS)
    }

    @Test
    fun theRightPinAfterALockoutResetsTheCount() {
        assertTrue(manager.configurePin(PIN, dek))
        repeat(MAX_ATTEMPTS) { manager.unlockWithPin("x") }
        expireLockout()

        assertArrayEquals(dek, manager.unlockWithPin(PIN))
        manager.unlockWithPin("x")

        assertEquals("one miss after a reset is not a lockout", 0L, manager.remainingLockoutMillis())
    }

    @Test
    fun aNewPinClearsAPendingLockout() {
        assertTrue(manager.configurePin(PIN, dek))
        repeat(MAX_ATTEMPTS) { manager.unlockWithPin("x") }

        assertTrue(manager.configurePin(NEW_PIN, dek))

        assertEquals(0L, manager.remainingLockoutMillis())
        assertTrue(manager.verifyPinAuth(NEW_PIN))
    }

    @Test
    fun theStrategyFollowsTheSlotsAndBiometrics() {
        assertEquals(AuthStrategy.NONE, manager.isAuthStrategy())

        assertTrue(manager.configurePin(PIN, dek))
        assertEquals(AuthStrategy.PIN, manager.isAuthStrategy())

        manager.markBiometricEnabled(true)
        assertTrue(manager.isBiometricEnabled())
        assertEquals(AuthStrategy.PIN_BIOMETRIC, manager.isAuthStrategy())

        manager.deactivateBiometricAuth()
        assertFalse(manager.isBiometricEnabled())
    }

    @Test
    fun aPinTypeWithoutItsSlotAsksForTheRecoveryData() {
        preferences.edit().putInt(AUTH_TYPE_KEY, AuthType.PIN.ordinal).commit()

        assertEquals(AuthStrategy.RECOVERY_DATA, manager.isAuthStrategy())
    }

    @Test
    fun aSeedVaultUnlocksWithTheWordsWithOrWithoutBiometrics() {
        preferences.edit().putInt(AUTH_TYPE_KEY, AuthType.SEED_PHRASE.ordinal).commit()
        assertEquals(AuthStrategy.SEED_PHRASE, manager.isAuthStrategy())

        manager.markBiometricEnabled(true)
        assertEquals(AuthStrategy.SEED_PHRASE_BIOMETRIC, manager.isAuthStrategy())
    }

    @Test
    fun anUnknownStoredTypeIsNone() {
        preferences.edit().putInt(AUTH_TYPE_KEY, UNKNOWN_ORDINAL).commit()

        assertEquals(AuthType.NONE, manager.getAuthType())
    }

    @Test
    fun wipeForgetsTheSlotAndTheLockout() {
        assertTrue(manager.configurePin(PIN, dek))
        repeat(MAX_ATTEMPTS) { manager.unlockWithPin("x") }

        manager.wipe()

        assertFalse(manager.hasPinSlot())
        assertEquals(0L, manager.remainingLockoutMillis())
        assertEquals(AuthType.NONE, manager.getAuthType())
    }

    private fun expireLockout() {
        preferences.edit().putLong(LOCKOUT_UNTIL_KEY, 0L).commit()
    }

    private companion object {
        const val PIN = "482915"
        const val NEW_PIN = "730164"
        const val DEK_LENGTH = 32
        const val MAX_ATTEMPTS = 5
        const val FIRST_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 15 * 60_000L
        const val UNKNOWN_ORDINAL = 42
        const val AUTH_TYPE_KEY = "auth_type_v2"
        const val LOCKOUT_UNTIL_KEY = "pin_lockout_until_v2"
    }
}
