package com.pasich.encly.core.security

import com.pasich.encly.data.database.SecureDatabaseManager
import com.pasich.encly.testutil.InMemorySharedPreferences
import com.pasich.encly.testutil.anyByteArray
import com.pasich.encly.testutil.anyCharArray
import com.pasich.encly.testutil.tempVaultFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

class SecurityManagerTest {

    private lateinit var prefs: InMemorySharedPreferences
    private lateinit var seed: SeedPhraseManager
    private lateinit var database: SecureDatabaseManager
    private lateinit var auth: AuthenticationManager
    private lateinit var biometric: BiometricManager
    private lateinit var file: File
    private lateinit var store: VaultStore
    private lateinit var manager: SecurityManager

    @Before
    fun setUp() {
        prefs = InMemorySharedPreferences()
        seed = mock(SeedPhraseManager::class.java)
        database = mock(SecureDatabaseManager::class.java)
        auth = mock(AuthenticationManager::class.java)
        biometric = mock(BiometricManager::class.java)
        file = tempVaultFile()
        store = VaultStore(file)
        manager = SecurityManager(prefs, store, seed, database, auth, biometric)
    }

    // --- startup routing -------------------------------------------------------------------

    @Test
    fun freshInstallStartsOnboarding() {
        assertEquals(InitialStatus.ONBOARDING, manager.resolveInitialStatus())
    }

    @Test
    fun encryptedDatabaseWithoutAnyVaultMetadataIsAnOlderVersionVaultNeedingExplicitWipe() {
        // Encly 1.x left an encrypted database and no v2 vault metadata. 2.0 cannot open it:
        // the start screen says it is from an older version, not that it is damaged.
        `when`(database.hasEncryptedDatabase()).thenReturn(true)

        assertEquals(InitialStatus.LEGACY_VAULT, manager.resolveInitialStatus())
    }

    @Test
    fun interruptedFirstRunRestartsOnboarding() {
        `when`(database.hasEncryptedDatabase()).thenReturn(true)
        `when`(seed.hasStoredSeed()).thenReturn(true)

        assertEquals(InitialStatus.ONBOARDING, manager.resolveInitialStatus())
    }

    @Test
    fun committedVaultWithPinSlotAsksForAuthentication() {
        commitVault()
        `when`(auth.hasPinSlot()).thenReturn(true)

        assertEquals(InitialStatus.AUTH, manager.resolveInitialStatus())
    }

    @Test
    fun committedVaultWithOnlyRecoverySlotStillAsksForAuthentication() {
        commitVault()
        `when`(seed.hasRecoverySeed()).thenReturn(true)

        assertEquals(InitialStatus.AUTH, manager.resolveInitialStatus())
    }

    @Test
    fun committedVaultWithoutDatabaseIsDatabaseLoss() {
        commitVault()
        `when`(database.hasEncryptedDatabase()).thenReturn(false)

        assertEquals(InitialStatus.LOSS_DATABASE, manager.resolveInitialStatus())
    }

    @Test
    fun committedVaultWithoutAnyUnlockSlotIsCryptoLoss() {
        commitVault()

        assertEquals(InitialStatus.LOSS_CRYPTO, manager.resolveInitialStatus())
    }

    @Test
    fun committedVaultWithBrokenMetadataIsCryptoLoss() {
        commitVault()
        `when`(seed.verificationKeyData()).thenReturn(false)
        `when`(auth.hasPinSlot()).thenReturn(true)

        assertEquals(InitialStatus.LOSS_CRYPTO, manager.resolveInitialStatus())
    }

    // --- new vault creation never destroys data ---------------------------------------------

    @Test
    fun newVaultIsRefusedOverACommittedVault() {
        commitVault()

        assertFalse(manager.initializeNewVault(null))

        verify(database, never()).wipe()
    }

    @Test
    fun newVaultIsRefusedOverAnOrphanEncryptedDatabase() {
        `when`(database.hasEncryptedDatabase()).thenReturn(true)

        assertFalse(manager.initializeNewVault(null))

        verify(database, never()).wipe()
    }

    @Test
    fun newVaultOnFreshInstallCreatesTheVault() {
        `when`(seed.initializeVault(null)).thenReturn(true)

        assertTrue(manager.initializeNewVault(null))

        verify(database).wipe()
        verify(seed).initializeVault(null)
    }

    // --- first-run commit ordering --------------------------------------------------------

    @Test
    fun initialSetupNeedsAPinSlotFirst() {
        `when`(auth.hasPinSlot()).thenReturn(false)
        `when`(seed.copyBootstrapKey()).thenReturn(ByteArray(32) { 1 })

        assertFalse(manager.finishInitialSetup())

        verify(database, never()).unlockDatabase(anyByteArray(), anyBoolean())
        assertFalse(prefs.getBoolean(ONBOARDING_KEY, false))
    }

    @Test
    fun initialSetupIsNotCommittedWhenTheDatabaseDoesNotOpen() {
        readyForSetup()
        `when`(database.unlockDatabase(anyByteArray(), eq(true))).thenReturn(false)

        assertFalse(manager.finishInitialSetup())

        assertFalse(prefs.getBoolean(ONBOARDING_KEY, false))
        verify(seed, never()).clearBootstrapKey()
    }

    @Test
    fun initialSetupOpensTheDatabaseThenCommitsThenDropsTheBootstrapKey() {
        readyForSetup()
        `when`(database.unlockDatabase(anyByteArray(), eq(true))).thenReturn(true)

        assertTrue(manager.finishInitialSetup())

        assertTrue(prefs.getBoolean(ONBOARDING_KEY, false))
        val order = inOrder(database, seed)
        order.verify(database).unlockDatabase(anyByteArray(), eq(true))
        order.verify(seed).clearBootstrapKey()
        assertEquals(InitialStatus.MAIN, manager.securityStatus)
    }

    @Test
    fun openingTheInitialVaultDoesNotCommitOnboarding() {
        readyForSetup()
        `when`(database.unlockDatabase(anyByteArray(), eq(true))).thenReturn(true)

        assertTrue(manager.openInitialVault())

        // A staged restore is imported now; a process killed here restarts onboarding.
        assertFalse(prefs.getBoolean(ONBOARDING_KEY, false))
        verify(seed, never()).clearBootstrapKey()

        assertTrue(manager.commitInitialSetup())
        assertTrue(prefs.getBoolean(ONBOARDING_KEY, false))
        verify(seed).clearBootstrapKey()
    }

    @Test
    fun anUncommittedSetupVaultIsNotRelockedByBackgrounding() {
        readyForSetup()
        `when`(database.unlockDatabase(anyByteArray(), eq(true))).thenReturn(true)
        assertTrue(manager.openInitialVault())

        assertFalse(manager.isLockable())

        assertTrue(manager.commitInitialSetup())
        assertTrue(manager.isLockable())
    }

    // --- lock -----------------------------------------------------------------------------

    @Test
    fun lockClosesTheDatabaseAndDropsTheSessionKey() {
        `when`(database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)
        `when`(auth.configurePin(anyCharArray(), anyByteArray())).thenReturn(true)
        assertTrue(manager.unlockWithRawKey(ByteArray(32) { 7 }))
        assertTrue(manager.configurePin("123456".toCharArray())) // the live session DEK is available

        manager.lock()

        verify(database).reset()
        assertEquals(InitialStatus.AUTH, manager.securityStatus)
        // No session DEK (and no bootstrap key) remains to re-wrap.
        assertFalse(manager.configurePin("123456".toCharArray()))
    }

    // --- forgotten PIN --------------------------------------------------------------------

    @Test
    fun recoveryPhraseUnlockAllowsSettingANewPinWithoutTheOldOne() {
        val phrase = "words".toCharArray()
        `when`(seed.unlockWithSeed(phrase)).thenReturn(ByteArray(32) { 5 })
        `when`(database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)
        `when`(auth.configurePin(anyCharArray(), anyByteArray())).thenReturn(true)

        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithSeed(phrase))
        assertTrue(manager.canResetPinWithoutCurrent())

        // Once the new PIN is set the exemption ends.
        assertTrue(manager.configurePin("654321".toCharArray()))
        assertFalse(manager.canResetPinWithoutCurrent())
    }

    @Test
    fun pinUnlockOrLockEndsThePinResetExemption() {
        val phrase = "words".toCharArray()
        `when`(seed.unlockWithSeed(phrase)).thenReturn(ByteArray(32) { 5 })
        `when`(database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)
        `when`(auth.unlockWithPin("123456".toCharArray())).thenReturn(PinUnlock.Success(ByteArray(32) { 5 }))

        manager.unlockWithSeed(phrase)
        manager.lock()
        assertFalse(manager.canResetPinWithoutCurrent())

        manager.unlockWithSeed(phrase)
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPin("123456".toCharArray()))
        assertFalse(manager.canResetPinWithoutCurrent())
    }

    @Test
    fun aWrongRecoveryPhraseGrantsNothing() {
        assertEquals(VaultUnlockResult.INVALID_CREDENTIAL, manager.unlockWithSeed("bad".toCharArray()))
        assertFalse(manager.canResetPinWithoutCurrent())
    }

    // --- damaged storage and a lost PIN key -----------------------------------------------

    @Test
    fun aDamagedStateFileRoutesToTheDamagedVaultScreen() {
        commitVault()
        `when`(auth.hasPinSlot()).thenReturn(true)
        file.writeBytes(ByteArray(64) { 3 })
        val onDamagedFile = SecurityManager(prefs, VaultStore(file), seed, database, auth, biometric)

        assertEquals(InitialStatus.LOSS_CRYPTO, onDamagedFile.resolveInitialStatus())
    }

    @Test
    fun aStorageFailureAtStartupRoutesToTheDamagedVaultScreenInsteadOfCrashing() {
        commitVault()
        `when`(auth.hasPinSlot()).thenThrow(IllegalStateException("keystore"))

        assertEquals(InitialStatus.LOSS_CRYPTO, manager.resolveInitialStatus())
    }

    @Test
    fun wipingADamagedVaultStartsOverWithAClearStore() {
        file.writeBytes(ByteArray(64) { 3 })
        val onDamagedFile = SecurityManager(prefs, VaultStore(file), seed, database, auth, biometric)
        prefs.edit().putBoolean(ONBOARDING_KEY, true).commit()

        onDamagedFile.wipeAndReset()

        assertFalse(file.exists())
        assertFalse(prefs.getBoolean(ONBOARDING_KEY, false))
        assertEquals(InitialStatus.ONBOARDING, onDamagedFile.resolveInitialStatus())
    }

    @Test
    fun aLostPinKeyIsReportedAndThePinIsWiped() {
        val typed = "123456".toCharArray()
        `when`(auth.unlockWithPin(typed)).thenReturn(PinUnlock.KeyLost)

        assertEquals(VaultUnlockResult.PIN_KEY_LOST, manager.unlockWithPin(typed))
        assertTrue(typed.all { it == '\u0000' })
        verify(database, never()).unlockDatabase(anyByteArray(), anyBoolean())
    }

    @Test
    fun aRunningLockoutIsReportedAsSuch() {
        `when`(auth.unlockWithPin(anyCharArray())).thenReturn(PinUnlock.LockedOut)

        assertEquals(VaultUnlockResult.LOCKED_OUT, manager.unlockWithPin("123456".toCharArray()))
    }

    // --- replacing the recovery phrase ----------------------------------------------------

    @Test
    fun replacingThePhraseNeedsAnOpenSession() {
        `when`(seed.replaceRecoverySeed(anyCharArray(), anyByteArray())).thenReturn(true)
        assertFalse(manager.replaceRecoverySeed("words".toCharArray()))

        `when`(database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)
        assertTrue(manager.unlockWithRawKey(ByteArray(32) { 7 }))
        assertTrue(manager.replaceRecoverySeed("words".toCharArray()))
        // The session DEK copy is wiped right after the call, so only the words can be matched.
        verify(seed).replaceRecoverySeed(eqChars("words"), anyByteArray())

        manager.lock()
        assertFalse(manager.replaceRecoverySeed("words".toCharArray()))
    }

    private fun eqChars(value: String): CharArray =
        org.mockito.AdditionalMatchers.aryEq(value.toCharArray()) ?: CharArray(0)

    private fun commitVault() {
        prefs.edit().putBoolean(ONBOARDING_KEY, true).commit()
        `when`(seed.verificationKeyData()).thenReturn(true)
        `when`(seed.hasStoredSeed()).thenReturn(true)
        `when`(database.hasEncryptedDatabase()).thenReturn(true)
    }

    private fun readyForSetup() {
        `when`(auth.hasPinSlot()).thenReturn(true)
        `when`(seed.verificationKeyData()).thenReturn(true)
        `when`(seed.copyBootstrapKey()).thenReturn(ByteArray(32) { 3 })
    }

    private companion object {
        const val ONBOARDING_KEY = "onboarding_shown_v3"
    }
}
