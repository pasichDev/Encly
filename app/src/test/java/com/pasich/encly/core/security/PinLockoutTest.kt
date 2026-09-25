package com.pasich.encly.core.security

import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.tempVaultFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** PIN rules around the key slot: format, lockout escalation and what a right PIN resets. */
class PinLockoutTest {
    private val file = tempVaultFile()
    private val store = VaultStore(file)
    private val factor = FakePinFactor()
    private val clock = FakeLockoutClock()
    private val manager = AuthenticationManager(store, factor, clock)
    private val dek = ByteArray(DEK_LENGTH) { 0x4C }

    @Test
    fun onlySixDigitsAndAFullLengthKeyMakeAPinSlot() {
        assertFalse(manager.configurePin(pin("12345"), dek))
        assertFalse(manager.configurePin(pin("1234567"), dek))
        assertFalse(manager.configurePin(pin("12345a"), dek))
        assertFalse(manager.configurePin(pin(PIN), ByteArray(DEK_LENGTH - 1)))

        assertFalse(manager.hasPinSlot())
        assertEquals(AuthType.NONE, manager.getAuthType())
    }

    @Test
    fun aMalformedPinCountsAsAFailedAttempt() {
        assertTrue(manager.configurePin(pin(PIN), dek))

        repeat(MAX_ATTEMPTS) { assertEquals(PinUnlock.WrongPin, manager.unlockWithPin(pin("12ab"))) }

        assertEquals(FIRST_LOCKOUT_MS, manager.remainingLockoutMillis())
    }

    @Test
    fun aRunningLockoutRefusesEvenTheRightPinWithoutCountingIt() {
        assertTrue(manager.configurePin(pin(PIN), dek))
        failTimes(MAX_ATTEMPTS)

        assertEquals(PinUnlock.LockedOut, manager.unlockWithPin(pin(PIN)))
        assertEquals(FIRST_LOCKOUT_MS, manager.remainingLockoutMillis())
    }

    @Test
    fun eachFailureAfterALockoutDoublesTheNextOne() {
        assertTrue(manager.configurePin(pin(PIN), dek))
        failTimes(MAX_ATTEMPTS)

        waitOutLockout()
        manager.unlockWithPin(pin("x"))

        assertEquals(FIRST_LOCKOUT_MS * 2, manager.remainingLockoutMillis())
    }

    @Test
    fun theLockoutKeepsEscalatingPastFifteenMinutesUpToADay() {
        assertEquals(0L, AuthenticationManager.penaltyFor(MAX_ATTEMPTS - 1))
        assertEquals(30_000L, AuthenticationManager.penaltyFor(MAX_ATTEMPTS))
        assertEquals(16 * 60_000L, AuthenticationManager.penaltyFor(MAX_ATTEMPTS + 5))
        assertEquals(32 * 60_000L, AuthenticationManager.penaltyFor(MAX_ATTEMPTS + 6))
        assertEquals(MAX_LOCKOUT_MS, AuthenticationManager.penaltyFor(MAX_ATTEMPTS + 12))
        assertEquals(MAX_LOCKOUT_MS, AuthenticationManager.penaltyFor(Int.MAX_VALUE))

        assertTrue(manager.configurePin(pin(PIN), dek))
        repeat(MAX_ATTEMPTS + 20) {
            waitOutLockout()
            manager.unlockWithPin(pin("x"))
        }
        assertEquals(MAX_LOCKOUT_MS, manager.remainingLockoutMillis())
    }

    @Test
    fun theLockoutCountsDownOnTheMonotonicClock() {
        assertTrue(manager.configurePin(pin(PIN), dek))
        failTimes(MAX_ATTEMPTS)

        clock.elapsed += 10_000L

        assertEquals(FIRST_LOCKOUT_MS - 10_000L, manager.remainingLockoutMillis())
        clock.elapsed += FIRST_LOCKOUT_MS
        assertEquals(0L, manager.remainingLockoutMillis())
    }

    @Test
    fun aRebootRestartsTheRemainingPenaltyInsteadOfEndingIt() {
        assertTrue(manager.configurePin(pin(PIN), dek))
        failTimes(MAX_ATTEMPTS)
        clock.elapsed += 10_000L

        // Reboot: elapsedRealtime starts again near zero, in a new boot.
        clock.boot++
        clock.elapsed = 5_000L
        val rebooted = AuthenticationManager(VaultStore(file), factor, clock)

        assertEquals(FIRST_LOCKOUT_MS, rebooted.remainingLockoutMillis())
        clock.elapsed += 1_000L
        assertEquals(FIRST_LOCKOUT_MS - 1_000L, rebooted.remainingLockoutMillis())
    }

    @Test
    fun aClockBehindTheAnchorIsTreatedAsAReboot() {
        assertTrue(manager.configurePin(pin(PIN), dek))
        failTimes(MAX_ATTEMPTS)

        clock.elapsed -= 500_000L

        assertEquals(FIRST_LOCKOUT_MS, manager.remainingLockoutMillis())
    }

    @Test
    fun theAttemptIsRecordedBeforeTheKeyIsDerived() {
        assertTrue(manager.configurePin(pin(PIN), dek))
        failTimes(MAX_ATTEMPTS - 1)
        // The fifth attempt dies inside the key derivation (the process is killed there).
        var lockoutOnDiskDuringKdf = -1L
        val dying = object : PinHardwareFactor by factor {
            override fun mac(data: ByteArray): ByteArray {
                // What a fresh process would read from disk at this moment.
                lockoutOnDiskDuringKdf = AuthenticationManager(VaultStore(file), factor, clock).remainingLockoutMillis()
                throw ProcessDeath()
            }
        }

        runCatching { AuthenticationManager(VaultStore(file), dying, clock).unlockWithPin(pin("000000")) }

        assertEquals(FIRST_LOCKOUT_MS, lockoutOnDiskDuringKdf)
        assertEquals(FIRST_LOCKOUT_MS, AuthenticationManager(VaultStore(file), factor, clock).remainingLockoutMillis())
    }

    @Test
    fun concurrentAttemptsAreAllCounted() {
        assertTrue(manager.configurePin(pin(PIN), dek))
        val pool = Executors.newFixedThreadPool(4)
        val done = CountDownLatch(MAX_ATTEMPTS - 1)
        repeat(MAX_ATTEMPTS - 1) {
            pool.execute {
                manager.unlockWithPin(pin("00000$it"))
                done.countDown()
            }
        }
        assertTrue(done.await(2, TimeUnit.MINUTES))
        pool.shutdown()

        manager.unlockWithPin(pin("999999"))

        assertEquals(FIRST_LOCKOUT_MS, manager.remainingLockoutMillis())
    }

    @Test
    fun theRightPinAfterALockoutResetsTheCount() {
        assertTrue(manager.configurePin(pin(PIN), dek))
        failTimes(MAX_ATTEMPTS)
        waitOutLockout()

        assertTrue(manager.unlockWithPin(pin(PIN)) is PinUnlock.Success)
        manager.unlockWithPin(pin("x"))

        assertEquals("one miss after a reset is not a lockout", 0L, manager.remainingLockoutMillis())
    }

    @Test
    fun aNewPinClearsAPendingLockout() {
        assertTrue(manager.configurePin(pin(PIN), dek))
        failTimes(MAX_ATTEMPTS)

        assertTrue(manager.configurePin(pin(NEW_PIN), dek))

        assertEquals(0L, manager.remainingLockoutMillis())
        assertTrue(manager.verifyPinAuth(pin(NEW_PIN)))
    }

    @Test
    fun theStrategyFollowsTheSlotsAndBiometrics() {
        assertEquals(AuthStrategy.NONE, manager.isAuthStrategy())

        assertTrue(manager.configurePin(pin(PIN), dek))
        assertEquals(AuthStrategy.PIN, manager.isAuthStrategy())

        manager.markBiometricEnabled(true)
        assertTrue(manager.isBiometricEnabled())
        assertEquals(AuthStrategy.PIN_BIOMETRIC, manager.isAuthStrategy())

        manager.deactivateBiometricAuth()
        assertFalse(manager.isBiometricEnabled())
    }

    @Test
    fun aPinTypeWithoutItsSlotAsksForTheRecoveryData() {
        store.edit { putInt(AUTH_TYPE_KEY, AuthType.PIN.ordinal) }

        assertEquals(AuthStrategy.RECOVERY_DATA, manager.isAuthStrategy())
    }

    @Test
    fun aSeedVaultUnlocksWithTheWordsWithOrWithoutBiometrics() {
        store.edit { putInt(AUTH_TYPE_KEY, AuthType.SEED_PHRASE.ordinal) }
        assertEquals(AuthStrategy.SEED_PHRASE, manager.isAuthStrategy())

        manager.markBiometricEnabled(true)
        assertEquals(AuthStrategy.SEED_PHRASE_BIOMETRIC, manager.isAuthStrategy())
    }

    @Test
    fun anUnknownStoredTypeIsNone() {
        store.edit { putInt(AUTH_TYPE_KEY, UNKNOWN_ORDINAL) }

        assertEquals(AuthType.NONE, manager.getAuthType())
    }

    @Test
    fun wipeForgetsTheSlotAndTheLockout() {
        assertTrue(manager.configurePin(pin(PIN), dek))
        failTimes(MAX_ATTEMPTS)

        manager.wipe()

        assertFalse(manager.hasPinSlot())
        assertEquals(0L, manager.remainingLockoutMillis())
        assertEquals(AuthType.NONE, manager.getAuthType())
    }

    @Test
    fun anAttemptThatCannotBeRecordedIsNotMade() {
        assertTrue(manager.configurePin(pin(PIN), dek))
        val dir = file.parentFile!!
        dir.setWritable(false)
        val before = factor.calls
        try {
            assertEquals(PinUnlock.Failed, manager.unlockWithPin(pin(PIN)))
        } finally {
            dir.setWritable(true)
        }
        assertEquals("no guess without a recorded attempt", before, factor.calls)
    }

    private fun failTimes(times: Int) = repeat(times) { manager.unlockWithPin(pin("x")) }

    private fun waitOutLockout() {
        clock.elapsed += manager.remainingLockoutMillis()
    }

    private fun pin(value: String) = value.toCharArray()

    private class ProcessDeath : RuntimeException()

    private companion object {
        const val PIN = "482915"
        const val NEW_PIN = "730164"
        const val DEK_LENGTH = 32
        const val MAX_ATTEMPTS = AuthenticationManager.MAX_ATTEMPTS
        const val FIRST_LOCKOUT_MS = AuthenticationManager.FIRST_LOCKOUT_MS
        const val MAX_LOCKOUT_MS = AuthenticationManager.MAX_LOCKOUT_MS
        const val UNKNOWN_ORDINAL = 42
        const val AUTH_TYPE_KEY = "auth.type"
    }
}
