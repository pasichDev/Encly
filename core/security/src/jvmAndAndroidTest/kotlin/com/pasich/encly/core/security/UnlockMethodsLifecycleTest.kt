package com.pasich.encly.core.security

import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.testutil.anyByteArray
import com.pasich.encly.testutil.eqValue
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
 * SecurityManager over real slot stores and a mocked database: the password as a way in next to the PIN, and
 * what a recovery-phrase unlock asks of the user afterwards (keep the PIN, or set a new one).
 */
class UnlockMethodsLifecycleTest {
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

    // --- the password at the lock screen ------------------------------------------------------

    @Test
    fun thePasswordUnlocksTheVaultOnItsOwn() {
        openWithPin()
        assertEquals(PasswordChange.SET, manager.changePassword(PASSWORD.toCharArray()))
        manager.lock()

        assertEquals(UnlockMethods(pin = true, password = true), manager.unlockMethods())
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPassword(PASSWORD.toCharArray()))
        assertEquals(InitialStatus.MAIN, manager.securityStatus)
        assertEquals(RecoveryStage.NONE, manager.recoveryStage())
        manager.lock()
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPin(PIN.toCharArray()))
    }

    @Test
    fun aWrongPasswordIsAnInvalidCredentialAndALockoutIsReported() {
        openWithPin()
        assertEquals(PasswordChange.SET, manager.changePassword(PASSWORD.toCharArray()))
        manager.lock()

        repeat(AuthenticationManager.MAX_ATTEMPTS) {
            assertEquals(
                VaultUnlockResult.INVALID_CREDENTIAL,
                manager.unlockWithPassword("not it at all".toCharArray()),
            )
        }

        assertEquals(VaultUnlockResult.LOCKED_OUT, manager.unlockWithPassword(PASSWORD.toCharArray()))
        assertEquals(VaultUnlockResult.LOCKED_OUT, manager.unlockWithPin(PIN.toCharArray()))
        assertTrue(manager.pinLockoutRemainingMillis() > 0L)
    }

    @Test
    fun aLostDeviceKeyIsReportedForThePasswordToo() {
        openWithPin()
        assertEquals(PasswordChange.SET, manager.changePassword(PASSWORD.toCharArray()))
        manager.lock()
        factor.lost = true

        assertEquals(VaultUnlockResult.PIN_KEY_LOST, manager.unlockWithPassword(PASSWORD.toCharArray()))
        assertEquals(VaultUnlockResult.PIN_KEY_LOST, manager.unlockWithPin(PIN.toCharArray()))
    }

    @Test
    fun theCallersPasswordIsWipedByTheUnlock() {
        openWithPin()
        assertEquals(PasswordChange.SET, manager.changePassword(PASSWORD.toCharArray()))
        manager.lock()
        val typed = PASSWORD.toCharArray()

        manager.unlockWithPassword(typed)

        assertTrue(typed.all { it == '\u0000' })
    }

    @Test
    fun aVaultWithOnlyAPasswordStillLocksAndRoutesToTheLockScreen() {
        openWithPin()
        assertEquals(PasswordChange.SET, manager.changePassword(PASSWORD.toCharArray()))
        assertTrue(manager.removePin())
        manager.lock()

        assertTrue(manager.isLockable())
        assertEquals(UnlockMethods(pin = false, password = true), manager.unlockMethods())
        assertEquals(AuthStrategy.PIN, manager.authStrategy())
        assertEquals(InitialStatus.AUTH, manager.resolveInitialStatus())
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPassword(PASSWORD.toCharArray()))
    }

    // --- Settings: add, change, remove ------------------------------------------------------------

    @Test
    fun aLockedVaultHasNoPasswordToChangeOrRemove() {
        assertEquals(PasswordChange.FAILED, manager.changePassword(PASSWORD.toCharArray()))
        assertFalse(manager.removePassword())
        assertFalse(manager.removePin())
    }

    @Test
    fun thePasswordIsReportedToSettings() {
        openWithPin()
        assertFalse(manager.getSettingsAuth().hasPassword)

        assertEquals(PasswordChange.SET, manager.changePassword(PASSWORD.toCharArray()))

        val settings = manager.getSettingsAuth()
        assertTrue(settings.hasPassword)
        assertTrue(settings.hasPinSlot)
        assertFalse(settings.passwordTurnedOff)
    }

    @Test
    fun theVerifiedPasswordConfirmsTheSessionLikeThePin() {
        openWithPin()
        assertEquals(PasswordChange.SET, manager.changePassword(PASSWORD.toCharArray()))

        assertTrue(manager.verifyPassword(PASSWORD.toCharArray()))
        assertFalse(manager.verifyPassword("something else".toCharArray()))
        assertTrue(manager.verifyPin(PIN.toCharArray()))
    }

    @Test
    fun theLastWayInCanNotBeRemoved() {
        openWithPin()
        assertFalse("no password to fall back on", manager.removePin())
        assertEquals(PasswordChange.SET, manager.changePassword(PASSWORD.toCharArray()))
        assertTrue(manager.removePassword())
        assertFalse(manager.getSettingsAuth().hasPassword)
        assertTrue(manager.getSettingsAuth().hasPinSlot)
    }

    @Test
    fun aShortPasswordIsInvalidAndLeavesNoSlot() {
        openWithPin()

        assertEquals(PasswordChange.INVALID, manager.changePassword("short".toCharArray()))
        assertFalse(manager.getSettingsAuth().hasPassword)
    }

    @Test
    fun theCallersNewPasswordIsWiped() {
        openWithPin()
        val typed = PASSWORD.toCharArray()

        manager.changePassword(typed)

        assertTrue(typed.all { it == '\u0000' })
    }

    // --- a decoy session never touches the real vault's methods ------------------------------------

    @Test
    fun aDecoySessionRefusesEverythingAboutThePasswordAndThePin() {
        openWithPin()
        `when`(database.wipeIfClosed(VaultKind.DECOY)).thenReturn(true)
        `when`(database.unlockDatabase(anyByteArray(), anyBoolean(), eqValue(VaultKind.DECOY))).thenReturn(true)
        assertEquals(PasswordChange.SET, manager.changePassword(PASSWORD.toCharArray()))
        assertEquals(DuressPinChange.SET_NEW_VAULT, manager.configureDuressPin(DURESS.toCharArray()))
        manager.lock()
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPin(DURESS.toCharArray()))
        assertTrue(manager.isDecoySession())

        assertEquals(PasswordChange.FAILED, manager.changePassword("a new password".toCharArray()))
        assertFalse(manager.removePassword())
        assertFalse(manager.removePin())
        assertFalse(
            "the real password opens nothing from inside a decoy session",
            manager.verifyPassword(PASSWORD.toCharArray()),
        )
        assertEquals(RecoveryStage.NONE, manager.recoveryStage())
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
    fun aWorkingPasswordCountsAsMuchAsAWorkingPin() {
        openWithPin()
        assertEquals(PasswordChange.SET, manager.changePassword(PASSWORD.toCharArray()))
        assertTrue(manager.removePin())
        manager.lock()

        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithSeed(phrase.copyOf()))

        assertEquals(RecoveryStage.CHOOSE, manager.recoveryStage())
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
    fun aLostDeviceKeyForcesANewPinAfterRecoveryAndTakesThePasswordWithIt() {
        openWithPin()
        assertEquals(PasswordChange.SET, manager.changePassword(PASSWORD.toCharArray()))
        manager.lock()
        factor.lost = true

        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithSeed(phrase.copyOf()))

        assertEquals(RecoveryStage.MUST_SET_PIN, manager.recoveryStage())
        assertEquals(PinChange.SET, manager.changePin(NEW_PIN.toCharArray()))
        val settings = manager.getSettingsAuth()
        assertFalse(settings.hasPassword)
        assertTrue(settings.passwordTurnedOff)
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

    private fun openWithPin() {
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPin(PIN.toCharArray()))
    }

    private companion object {
        const val PIN = "135790"
        const val NEW_PIN = "246813"
        const val DURESS = "908070"
        const val WRONG_PIN = "000111"
        const val PASSWORD = "correct horse battery"
    }
}
