package com.pasich.encly.core.security

import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.testutil.anyByteArray
import com.pasich.encly.testutil.tempVaultFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * SecurityManager over real slot stores and a mocked database: what a recovery-phrase unlock asks of the user
 * afterwards (keep the PIN, or set a new one), and a vault file that still holds a dev build's password entry.
 */
class RecoveryFollowUpTest {
    private val store = VaultStore(tempVaultFile())
    private val prefs = InMemoryKeyValueStore()
    private val seed = SeedPhraseManager(store)
    private val factor = FakePinFactor()
    private val clock = FakeLockoutClock()
    private val auth = AuthenticationManager(store, factor, clock)
    private val database = mock(VaultDatabase::class.java)
    private val biometric = mock(BiometricManager::class.java)
    private val manager = SecurityManager(prefs, store, seed, database, auth, biometric) {}
    private lateinit var phrase: CharArray

    @Before
    fun setUp() {
        `when`(database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)
        phrase = seed.generateMnemonic()
        assertTrue(manager.initializeNewVault(phrase.copyOf()))
        assertTrue(manager.configurePin(PIN.toCharArray()))
        assertTrue(manager.finishInitialSetup())
        manager.lock()
        `when`(database.hasEncryptedDatabase()).thenReturn(true)
    }

    // --- after the recovery phrase ---------------------------------------------------------------------

    @Test
    fun aWorkingPinIsOfferedToBeKeptAfterRecovery() {
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithSeed(phrase.copyOf()))

        assertEquals(RecoveryStage.CHOOSE, manager.recoveryStage())
        assertTrue(manager.canResetPinWithoutCurrent())

        assertTrue(manager.keepUnlockMethodsAfterRecovery())

        assertEquals(RecoveryStage.NONE, manager.recoveryStage())
        assertFalse(manager.canResetPinWithoutCurrent())
        manager.lock()
        assertEquals("the kept PIN still unlocks", VaultUnlockResult.SUCCESS, manager.unlockWithPin(PIN.toCharArray()))
    }

    @Test
    fun aNewPinCanBeChosenInsteadWithoutTheOldOne() {
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithSeed(phrase.copyOf()))
        assertEquals(RecoveryStage.CHOOSE, manager.recoveryStage())

        assertEquals(PinChange.SET, manager.changePin(NEW_PIN.toCharArray()))

        assertEquals(RecoveryStage.NONE, manager.recoveryStage())
        manager.lock()
        assertEquals(VaultUnlockResult.INVALID_CREDENTIAL, manager.unlockWithPin(PIN.toCharArray()))
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPin(NEW_PIN.toCharArray()))
    }

    @Test
    fun aMissingPinForcesANewOneAfterRecovery() {
        auth.wipe()

        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithSeed(phrase.copyOf()))

        assertEquals(RecoveryStage.MUST_SET_PIN, manager.recoveryStage())
        assertFalse("a forced reset can not be skipped", manager.keepUnlockMethodsAfterRecovery())
        assertEquals(RecoveryStage.MUST_SET_PIN, manager.recoveryStage())
        assertTrue(manager.canResetPinWithoutCurrent())
        assertEquals(PinChange.SET, manager.changePin(NEW_PIN.toCharArray()))
        assertEquals(RecoveryStage.NONE, manager.recoveryStage())
    }

    @Test
    fun aLostDeviceKeyForcesANewPinAfterRecovery() {
        openWithPin()
        manager.lock()
        factor.lost = true

        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithSeed(phrase.copyOf()))

        assertEquals(RecoveryStage.MUST_SET_PIN, manager.recoveryStage())
        assertEquals(PinChange.SET, manager.changePin(NEW_PIN.toCharArray()))
        manager.lock()
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPin(NEW_PIN.toCharArray()))
    }

    @Test
    fun aRunningLockoutForcesANewPinAfterRecoveryAndTheNewPinEndsIt() {
        repeat(AuthenticationManager.MAX_ATTEMPTS) { manager.unlockWithPin(WRONG_PIN.toCharArray()) }
        assertTrue(manager.pinLockoutRemainingMillis() > 0L)

        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithSeed(phrase.copyOf()))

        assertEquals(RecoveryStage.MUST_SET_PIN, manager.recoveryStage())
        assertEquals(PinChange.SET, manager.changePin(NEW_PIN.toCharArray()))
        assertEquals(0L, manager.pinLockoutRemainingMillis())
    }

    @Test
    fun aPinUnlockOwesNothingAndLockingEndsTheChoice() {
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPin(PIN.toCharArray()))
        assertEquals(RecoveryStage.NONE, manager.recoveryStage())
        assertFalse(manager.keepUnlockMethodsAfterRecovery())
        manager.lock()

        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithSeed(phrase.copyOf()))
        assertEquals(RecoveryStage.CHOOSE, manager.recoveryStage())
        manager.lock()
        assertEquals(RecoveryStage.NONE, manager.recoveryStage())
        assertFalse(manager.canResetPinWithoutCurrent())
    }

    @Test
    fun aWrongPhraseOwesNothing() {
        assertEquals(VaultUnlockResult.INVALID_CREDENTIAL, manager.unlockWithSeed(seed.generateMnemonic()))

        assertEquals(RecoveryStage.NONE, manager.recoveryStage())
        assertNull(manager.copyBackupRootKey())
    }

    // --- a dev build's password entry -------------------------------------------------------------------

    @Test
    fun aLeftoverPasswordEntryIsIgnoredAndTheNextPinWriteDropsIt() {
        store.edit {
            putBytes("pin.password.slot", ByteArray(LEFTOVER_SLOT_SIZE) { 7 })
            putBoolean("pin.password.notice", true)
        }

        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPin(PIN.toCharArray()))
        assertTrue(store.contains("pin.password.slot"))
        assertEquals(PinChange.SET, manager.changePin(NEW_PIN.toCharArray()))

        assertFalse(store.contains("pin.password.slot"))
        assertFalse(store.contains("pin.password.notice"))
        manager.lock()
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPin(NEW_PIN.toCharArray()))
    }

    private fun openWithPin() {
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPin(PIN.toCharArray()))
    }

    private companion object {
        const val PIN = "135790"
        const val NEW_PIN = "246813"
        const val DURESS = "908070"
        const val WRONG_PIN = "000111"
        const val LEFTOVER_SLOT_SIZE = 77
    }
}
