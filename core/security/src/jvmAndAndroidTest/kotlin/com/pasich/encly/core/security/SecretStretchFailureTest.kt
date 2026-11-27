package com.pasich.encly.core.security

import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.FakeV4PasswordPolicy
import com.pasich.encly.testutil.tempVaultStore
import kotlinx.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A stretch that could not be made ([SecretStretchException]: Argon2id out of memory) is "this attempt could not be
 * made", never a wrong guess: it is given back at every place the keys are derived, and the caller gets a failure
 * result, not a crash.
 */
class SecretStretchFailureTest {
    private val store = tempVaultStore()
    private val factor = FakePinFactor()
    private val policy = FakeV4PasswordPolicy()
    private val auth = AuthenticationManager(store, factor, FakeLockoutClock(), policy)
    private val dek = ByteArray(32) { 0x3C }

    @Before
    fun setUp() {
        assertTrue(auth.configurePin(secret(PASSWORD), dek))
    }

    @Test
    fun anUnlockThatCouldNotStretchIsGivenBackAndNotCounted() {
        policy.failStretch = true

        // More than the attempts before a lockout: none of them counts.
        repeat(AuthenticationManager.MAX_ATTEMPTS + 2) {
            assertEquals(PinUnlock.KeyStoreFailed, auth.unlockWithPin(secret(PASSWORD)))
        }

        assertEquals(0, store.getInt("lockout.failures", 0))
        assertEquals(0, store.getInt("pin.strikes", 0))
        assertEquals(0L, auth.remainingLockoutMillis())
        policy.failStretch = false
        assertArrayEquals(dek, (auth.unlockWithPin(secret(PASSWORD)) as PinUnlock.Success).dek)
    }

    @Test
    fun theMissesBeforeAFailedStretchStayExactlyAsTheyWere() {
        repeat(2) { assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(secret("not the password"))) }
        policy.failStretch = true

        assertEquals(PinUnlock.KeyStoreFailed, auth.unlockWithPin(secret(PASSWORD)))

        assertEquals(2, store.getInt("lockout.failures", 0))
        assertEquals(2, store.getInt("pin.strikes", 0))
    }

    @Test
    fun aRunningLockoutIsStillRefusedBeforeAnyStretch() {
        repeat(AuthenticationManager.MAX_ATTEMPTS) { auth.unlockWithPin(secret("not the password")) }
        policy.failStretch = true
        policy.resetCounts()

        assertEquals(PinUnlock.LockedOut, auth.unlockWithPin(secret(PASSWORD)))

        assertEquals(0, policy.stretchCalls)
    }

    @Test
    fun aPasswordCheckInsideTheOpenVaultThatCouldNotStretchIsNotACorrectOneAndNotCounted() {
        policy.failStretch = true

        assertFalse(auth.verifyPinAuth(secret(PASSWORD)))

        assertEquals(0, store.getInt("lockout.failures", 0))
    }

    @Test
    fun aNewSecretThatCouldNotStretchIsRefusedAndTheOldSlotStays() {
        val before = store.getBytes("pin.slot")!!
        policy.failStretch = true

        assertEquals(PinChange.FAILED, auth.changePin(secret("another password here"), dek))

        assertArrayEquals(before, store.getBytes("pin.slot")!!)
        policy.failStretch = false
        assertArrayEquals(dek, (auth.unlockWithPin(secret(PASSWORD)) as PinUnlock.Success).dek)
    }

    @Test
    fun theFirstSecretOfAVaultThatCouldNotStretchWritesNothing() {
        val fresh = tempVaultStore()
        val freshPolicy = FakeV4PasswordPolicy().apply { failStretch = true }
        val freshAuth = AuthenticationManager(fresh, FakePinFactor(), FakeLockoutClock(), freshPolicy)

        assertEquals(PinChange.FAILED, freshAuth.changePin(secret(PASSWORD), dek))

        assertFalse(freshAuth.hasPinSlot())
    }

    @Test
    fun aWipeSecretAndADuressSecretThatCouldNotStretchAreRefusedAndNothingIsCounted() {
        val wipeSlot = store.getBytes("pin.wipe.slot")!!
        val duressSlot = store.getBytes("pin.decoy.slot")!!
        policy.failStretch = true

        assertEquals(WipePinChange.FAILED, auth.configureWipePin(secret(WIPE)))
        assertEquals(DuressPinChange.FAILED, auth.configureDuressPin(secret(DURESS), dek) { true })

        assertArrayEquals(wipeSlot, store.getBytes("pin.wipe.slot")!!)
        assertArrayEquals(duressSlot, store.getBytes("pin.decoy.slot")!!)
        assertEquals(0, store.getInt("lockout.failures", 0))
    }

    @Test
    fun aDecoySessionsRefusalSurvivesAStretchThatFailsAndWipesItsCopy() {
        policy.failStretch = true
        policy.canonicals.clear()

        auth.spendPinCost(secret(PASSWORD))

        assertEquals(1, policy.canonicals.size)
        assertTrue(policy.canonicals.single().all { it == 0.toChar() })
    }

    @Test
    fun theCanonicalCopyIsWipedWhenTheStretchFails() {
        policy.failStretch = true
        policy.canonicals.clear()

        auth.unlockWithPin(secret(PASSWORD))
        auth.changePin(secret("another password here"), dek)
        auth.configureWipePin(secret(WIPE))
        auth.configureDuressPin(secret(DURESS), dek) { true }

        assertEquals(4, policy.canonicals.size)
        policy.canonicals.forEach { copy -> assertTrue(copy.all { it == 0.toChar() }) }
    }

    @Test
    fun anUnexpectedFailureOfThePolicyIsNotSwallowedAndTheAttemptStaysCounted() {
        policy.failStretchWith = IllegalStateException("a bug in the policy")

        assertThrows(IllegalStateException::class.java) { auth.unlockWithPin(secret(PASSWORD)) }

        // Like a kill during the stretch: the attempt was charged before it, and nothing gives it back.
        assertEquals(1, store.getInt("lockout.failures", 0))
        policy.failStretchWith = null
        assertArrayEquals(dek, (auth.unlockWithPin(secret(PASSWORD)) as PinUnlock.Success).dek)
    }

    @Test
    fun aMissIsRefusedAsFailedWhenItsChargeCouldNotBeWritten() {
        val flaky = FlakyVaultFileIo()
        val flakyStore = VaultStore(flaky)
        val flakyAuth = AuthenticationManager(flakyStore, FakePinFactor(), FakeLockoutClock(), FakeV4PasswordPolicy())
        assertTrue(flakyAuth.configurePin(secret(PASSWORD), dek))
        flaky.failWrites = true

        // The wipe secret and the duress secret are the vault's secret: refused, but the charge is not written.
        assertEquals(WipePinChange.FAILED, flakyAuth.configureWipePin(secret(PASSWORD)))
        assertEquals(DuressPinChange.FAILED, flakyAuth.configureDuressPin(secret(PASSWORD), dek) { true })
    }

    @Test
    fun aDuressSecretCanBeSetWhenTheWipeSlotEntryIsMissing() {
        store.edit { remove("pin.wipe.slot") }

        assertEquals(DuressPinChange.SET_NEW_VAULT, auth.configureDuressPin(secret(DURESS), dek) { true })
    }

    @Test
    fun underThePinPolicyAnUnparsableSlotIsACountedMissWithNothingDerived() {
        val shapes = mapOf(
            "empty" to ByteArray(0),
            "unknown first byte" to ByteArray(77).also { it[0] = 7 },
            "wrong length" to ByteArray(76).also { it[0] = 3 },
            "v4-shaped" to ByteArray(85).also {
                it[0] = 4
                it[1] = 2
                it[2] = 1
                it[4] = 1
                it[7] = 3
                it[8] = 1
            },
        )
        for ((name, slot) in shapes) {
            val pinStore = tempVaultStore()
            val pinFactor = FakePinFactor()
            val pinAuth = AuthenticationManager(pinStore, pinFactor, FakeLockoutClock())
            pinStore.edit { putBytes("pin.slot", slot) }

            assertEquals(name, PinUnlock.WrongPin, pinAuth.unlockWithPin(secret("123456")))
            assertEquals(name, 1, pinStore.getInt("lockout.failures", 0))
            assertEquals(name, 1, pinStore.getInt("pin.strikes", 0))
            assertEquals(name, 0, pinFactor.calls)
        }
    }

    @Test
    fun underThePinPolicyAnUnparsableSlotAlsoLocksOutAfterFiveMisses() {
        val pinStore = tempVaultStore()
        val pinAuth = AuthenticationManager(pinStore, FakePinFactor(), FakeLockoutClock())
        pinStore.edit { putBytes("pin.slot", ByteArray(0)) }

        repeat(AuthenticationManager.MAX_ATTEMPTS) { pinAuth.unlockWithPin(secret("123456")) }

        assertEquals(PinUnlock.LockedOut, pinAuth.unlockWithPin(secret("123456")))
    }

    @Test
    fun underThePasswordPolicyAnEmptySlotIsNoSlotAndCostsNothing() {
        store.edit { putBytes("pin.slot", ByteArray(0)) }
        policy.resetCounts()

        assertEquals(PinUnlock.WrongPin, auth.unlockWithPin(secret(PASSWORD)))

        assertEquals(0, store.getInt("lockout.failures", 0))
        assertEquals(0, policy.stretchCalls)
    }

    private fun secret(value: String) = value.toCharArray()

    /** A vault file that can be told to fail its writes. */
    private class FlakyVaultFileIo : VaultFileIo {
        private var bytes: ByteArray? = null
        var failWrites = false

        override fun exists(): Boolean = bytes != null

        override fun read(): ByteArray = bytes!!.copyOf()

        override fun deleteTemp() = Unit

        override fun replace(bytes: ByteArray, beforeRename: (() -> Unit)?) {
            if (failWrites) throw IOException("disk full")
            beforeRename?.invoke()
            this.bytes = bytes.copyOf()
        }

        override fun delete(): Boolean {
            bytes = null
            return true
        }
    }

    private companion object {
        const val PASSWORD = "correct horse battery"
        const val WIPE = "wipe everything now please"
        const val DURESS = "a duress password here"
    }
}
