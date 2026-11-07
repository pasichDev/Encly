package com.pasich.encly.core.security

import com.pasich.encly.core.platform.KeyValueStore
import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.testutil.anyByteArray
import com.pasich.encly.testutil.tempVaultFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

/**
 * SecurityManager over real slot stores and a mocked database: where first-run setup, startup
 * routing and session locking send the user, and what a locked or half-set-up vault refuses.
 */
class SecurityManagerLifecycleTest {
    private lateinit var file: File
    private lateinit var store: VaultStore
    private lateinit var prefs: InMemoryKeyValueStore
    private lateinit var seed: SeedPhraseManager
    private lateinit var auth: AuthenticationManager
    private val pinFactor = FakePinFactor()
    private val lockoutClock = FakeLockoutClock()
    private lateinit var database: VaultDatabase
    private lateinit var biometric: BiometricManager
    private var slotPresent = false
    private var enrollResult = true
    private var enrollCalls = 0
    private var disableCalls = 0
    private var prompt: BiometricManager.BiometricCallback? = null
    private lateinit var manager: SecurityManager

    @Before
    fun setUp() {
        file = tempVaultFile()
        store = VaultStore(file)
        prefs = InMemoryKeyValueStore()
        seed = SeedPhraseManager(store)
        auth = AuthenticationManager(store, pinFactor, lockoutClock)
        database = mock(VaultDatabase::class.java)
        biometric = mock(BiometricManager::class.java) { call ->
            when (call.method.name) {
                "hasSlot" -> slotPresent

                "disable" -> {
                    disableCalls++
                    null
                }

                "enroll" -> {
                    enrollCalls++
                    @Suppress("UNCHECKED_CAST")
                    (call.getArgument<Any>(2) as (Boolean) -> Unit)(enrollResult)
                    null
                }

                "authenticate" -> {
                    prompt = call.getArgument(2)
                    null
                }

                else -> Mockito.RETURNS_DEFAULTS.answer(call)
            }
        }
        manager = SecurityManager(prefs, store, seed, database, auth, biometric) {}
        `when`(database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)
    }

    /** First-run setup up to and including the committed onboarding, with a recovery phrase. */
    private fun committedVault(withRecoveryPhrase: Boolean = true): CharArray {
        val phrase = seed.generateMnemonic()
        assertTrue(manager.initializeNewVault(if (withRecoveryPhrase) phrase.copyOf() else null))
        assertTrue(manager.configurePin(PIN.toCharArray()))
        assertTrue(manager.finishInitialSetup())
        manager.lock()
        `when`(database.hasEncryptedDatabase()).thenReturn(true)
        return phrase
    }

    // --- startup routing ------------------------------------------------------------------

    @Test
    fun aDamagedStateFileRoutesToTheDamagedVaultScreen() {
        file.writeBytes(ByteArray(50) { 1 })

        assertEquals(InitialStatus.LOSS_CRYPTO, manager.resolveInitialStatus())
        assertEquals(InitialStatus.LOSS_CRYPTO, manager.securityStatus)
    }

    @Test
    fun anEncryptedDatabaseWithoutVaultMetadataIsALegacyVault() {
        `when`(database.hasEncryptedDatabase()).thenReturn(true)

        assertEquals(InitialStatus.LEGACY_VAULT, manager.resolveInitialStatus())
    }

    @Test
    fun aCommittedVaultWithoutItsDatabaseRoutesToDatabaseLoss() {
        committedVault()
        `when`(database.hasEncryptedDatabase()).thenReturn(false)

        assertEquals(InitialStatus.LOSS_DATABASE, manager.resolveInitialStatus())
    }

    @Test
    fun aCommittedVaultAsksForTheLockScreen() {
        committedVault()

        assertEquals(InitialStatus.AUTH, manager.resolveInitialStatus())
        assertTrue(manager.isLockable())
    }

    @Test
    fun aVaultWithoutAPinSlotAndWithoutARecoveryPhraseHasNoWayIn() {
        committedVault(withRecoveryPhrase = false)
        auth.wipe()

        assertEquals(InitialStatus.LOSS_CRYPTO, manager.resolveInitialStatus())
    }

    @Test
    fun aVaultWithoutAPinSlotStillAsksForTheRecoveryPhrase() {
        committedVault()
        auth.wipe()

        assertEquals(InitialStatus.AUTH, manager.resolveInitialStatus())
    }

    @Test
    fun anOpenVaultStaysOpenWhenTheActivityAsksAgain() {
        committedVault()
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPin(PIN.toCharArray()))
        `when`(database.isDatabaseUnlocked()).thenReturn(true)

        assertEquals(InitialStatus.MAIN, manager.resolveInitialStatus())
    }

    // --- first-run setup --------------------------------------------------------------------

    @Test
    fun aFreshVaultIsNotLockableBeforeOnboardingIsCommitted() {
        assertTrue(manager.initializeNewVault(null))
        assertTrue(manager.configurePin(PIN.toCharArray()))

        assertFalse(manager.isLockable())
        assertTrue(manager.isOnboardingShow())
    }

    @Test
    fun onboardingCanNotBeCommittedWithoutAPinSlot() {
        assertTrue(manager.initializeNewVault(null))

        assertFalse(manager.setOnboardingShown())
        assertFalse(manager.finishInitialSetup())
        assertTrue(manager.isOnboardingShow())
    }

    @Test
    fun theInitialVaultIsNotOpenedWithoutAPinSlot() {
        assertTrue(manager.initializeNewVault(null))

        assertFalse(manager.openInitialVault())
        verify(database, never()).unlockDatabase(anyByteArray(), anyBoolean())
    }

    @Test
    fun aCommittedVaultCanNotBeRecreatedOverItsData() {
        committedVault()

        assertFalse(manager.initializeNewVault(null))
        assertTrue(manager.hasRecoverySeed())
    }

    @Test
    fun anInvalidRecoveryPhraseDoesNotStartAVault() {
        assertFalse(manager.initializeNewVault("not a real phrase".toCharArray()))
        assertFalse(manager.isValidRecoveryPhrase("not a real phrase".toCharArray()))
    }

    @Test
    fun committingSetupDropsTheBootstrapKeySoNothingCanBeEnrolledFromIt() {
        committedVault()
        var enrolled: Boolean? = null

        manager.enrollBiometric(object : BiometricHost {}) { enrolled = it }

        assertEquals(false, enrolled)
        assertEquals(0, enrollCalls)
    }

    // --- unlocking and locking ------------------------------------------------------------

    @Test
    fun aDatabaseThatDoesNotOpenFailsThePinUnlockWithoutOpeningASession() {
        committedVault()
        `when`(database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(false)

        assertEquals(VaultUnlockResult.DB_ERROR, manager.unlockWithPin(PIN.toCharArray()))
        assertNull(manager.copyBackupRootKey())
        assertFalse(manager.canResetPinWithoutCurrent())
    }

    @Test
    fun aWrongPinIsAnInvalidCredential() {
        committedVault()

        assertEquals(VaultUnlockResult.INVALID_CREDENTIAL, manager.unlockWithPin(WRONG_PIN.toCharArray()))
    }

    @Test
    fun aWrongRecoveryPhraseIsAnInvalidCredential() {
        committedVault()
        val other = seed.generateMnemonic()

        assertEquals(VaultUnlockResult.INVALID_CREDENTIAL, manager.unlockWithSeed(other))
        assertFalse(manager.verifySeed(other))
    }

    @Test
    fun theRecoveryPhraseOpensTheVaultAndLetsThePinBeResetOnce() {
        val phrase = committedVault()
        assertTrue(manager.verifySeed(phrase.copyOf()))

        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithSeed(phrase.copyOf()))
        assertTrue(manager.canResetPinWithoutCurrent())

        assertEquals(PinChange.SET, manager.changePin(NEW_PIN.toCharArray()))
        assertFalse("a new PIN ends the recovery privilege", manager.canResetPinWithoutCurrent())
    }

    @Test
    fun aPinUnlockDoesNotGrantTheForgottenPinPrivilege() {
        committedVault()

        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPin(PIN.toCharArray()))

        assertFalse(manager.canResetPinWithoutCurrent())
    }

    @Test
    fun lockingEndsTheSessionAndWithdrawsTheKeysItHeld() {
        val phrase = committedVault()
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithSeed(phrase.copyOf()))
        assertTrue(manager.hasBackupKey())
        assertNotNull("the open session can read the backup root", manager.copyBackupRootKey())

        clearInvocations(database)

        manager.lock()

        assertEquals(InitialStatus.AUTH, manager.securityStatus)
        assertNull(manager.copyBackupRootKey())
        assertFalse(manager.addRecoverySeed(seed.generateMnemonic()))
        assertFalse(manager.replaceRecoverySeed(seed.generateMnemonic()))
        assertFalse(manager.createBackupKey(phrase.copyOf()))
        assertFalse(manager.canResetPinWithoutCurrent())
        verify(database).reset()
    }

    @Test
    fun aKeyOfTheWrongSizeNeverOpensTheDatabase() {
        assertFalse(manager.unlockWithRawKey(ByteArray(31)))
        assertFalse(manager.unlockWithRawKey(ByteArray(33), allowCreate = true))

        verify(database, never()).unlockDatabase(anyByteArray(), anyBoolean())
    }

    @Test
    fun theLockoutSurfacesThroughTheManager() {
        committedVault()
        repeat(LOCKOUT_AFTER) { manager.unlockWithPin(WRONG_PIN.toCharArray()) }

        assertTrue(manager.pinLockoutRemainingMillis() > 0)
        assertEquals(VaultUnlockResult.LOCKED_OUT, manager.unlockWithPin(PIN.toCharArray()))
    }

    @Test
    fun verifyingThePinCountsAndAcceptsOnlyTheRealOne() {
        committedVault()
        assertEquals(VaultUnlockResult.SUCCESS, manager.unlockWithPin(PIN.toCharArray()))

        assertTrue(manager.verifyPin(PIN.toCharArray()))
        assertFalse(manager.verifyPin(WRONG_PIN.toCharArray()))
    }

    // --- biometric plumbing -----------------------------------------------------------------

    @Test
    fun enrollingRecordsTheResultOfTheBiometricSlot() {
        committedVault()
        manager.unlockWithPin(PIN.toCharArray())
        slotPresent = true
        var result: Boolean? = null

        manager.enrollBiometric(object : BiometricHost {}) { result = it }

        assertEquals(true, result)
        assertTrue(manager.isBiometricEnabled())
        assertTrue(manager.getSettingsAuth().isBiometricEnabled)
    }

    @Test
    fun aFailedEnrollmentLeavesBiometricsOff() {
        committedVault()
        manager.unlockWithPin(PIN.toCharArray())
        slotPresent = true
        enrollResult = false
        var result: Boolean? = null

        manager.enrollBiometric(object : BiometricHost {}) { result = it }

        assertEquals(false, result)
        assertFalse(manager.isBiometricEnabled())
    }

    @Test
    fun disablingBiometricsClearsTheSlotAndTheFlag() {
        committedVault()
        manager.unlockWithPin(PIN.toCharArray())
        auth.markBiometricEnabled(true)
        slotPresent = true
        assertTrue(manager.isBiometricEnabled())
        disableCalls = 0

        manager.disableBiometric()

        assertEquals(1, disableCalls)
        assertFalse(manager.isBiometricEnabled())
    }

    @Test
    fun theFlagAloneIsNotEnoughWithoutTheKeystoreSlot() {
        committedVault()
        auth.markBiometricEnabled(true)
        slotPresent = false

        assertFalse(manager.isBiometricEnabled())
    }

    @Test
    fun theFingerprintConfirmationMapsEveryOutcomeToYesOrNo() {
        committedVault()
        manager.unlockWithPin(PIN.toCharArray())
        val results = mutableListOf<Boolean>()

        manager.confirmBiometric(object : BiometricHost {}) { results += it }
        val callback = checkNotNull(prompt)
        callback.onFailed()
        assertEquals("a single bad scan is not an answer", emptyList<Boolean>(), results)
        callback.onSuccess()
        callback.onError(7, "locked")
        callback.onCancelled()

        assertEquals(listOf(true, false, false), results)
    }

    // --- a lost flags store, and a setup killed at each step ---------------------------------

    /** A new process over the same files: nothing in memory (no bootstrap key) survives. */
    private fun restarted(vaultStore: VaultStore = VaultStore(file), flags: KeyValueStore = prefs): SecurityManager {
        val seeds = SeedPhraseManager(vaultStore)
        val authentication = AuthenticationManager(vaultStore, pinFactor, lockoutClock)
        return SecurityManager(flags, vaultStore, seeds, database, authentication, biometric) {}
    }

    @Test
    fun aSetupKilledBeforeAnythingWasWrittenRestartsOnboarding() {
        assertEquals(InitialStatus.ONBOARDING, restarted().resolveInitialStatus())
    }

    @Test
    fun aSetupKilledAfterTheSeedWasWrittenRestartsOnboarding() {
        assertTrue(manager.initializeNewVault(null))

        val next = restarted()

        assertEquals(InitialStatus.ONBOARDING, next.resolveInitialStatus())
        assertTrue(next.initializeNewVault(null))
    }

    @Test
    fun aSetupKilledAfterTheRecoverySlotWasWrittenRestartsOnboarding() {
        assertTrue(manager.initializeNewVault(seed.generateMnemonic()))

        val next = restarted()

        assertEquals(InitialStatus.ONBOARDING, next.resolveInitialStatus())
        assertTrue(next.initializeNewVault(null))
    }

    @Test
    fun aSetupKilledAfterThePinSlotButBeforeTheDatabaseRestartsOnboarding() {
        assertTrue(manager.initializeNewVault(seed.generateMnemonic()))
        assertTrue(manager.configurePin(PIN.toCharArray()))

        val next = restarted()

        assertEquals(InitialStatus.ONBOARDING, next.resolveInitialStatus())
        assertTrue(next.initializeNewVault(null))
    }

    @Test
    fun aSetupKilledAfterTheDatabaseWasCreatedIsACommittedVaultAndKeepsIt() {
        assertTrue(manager.initializeNewVault(null))
        assertTrue(manager.configurePin(PIN.toCharArray()))
        assertTrue(manager.openInitialVault())
        `when`(database.hasEncryptedDatabase()).thenReturn(true)
        clearInvocations(database)

        val next = restarted()

        assertEquals(InitialStatus.AUTH, next.resolveInitialStatus())
        assertFalse(next.initializeNewVault(null))
        verify(database, never()).wipe()
    }

    @Test
    fun theSetupStillRunningInThisProcessMayStartOverEvenWithItsDatabase() {
        assertTrue(manager.initializeNewVault(null))
        assertTrue(manager.configurePin(PIN.toCharArray()))
        assertTrue(manager.openInitialVault())
        `when`(database.hasEncryptedDatabase()).thenReturn(true)

        assertTrue(manager.initializeNewVault(null))
    }

    @Test
    fun aLostFlagOnACommittedVaultIsRepairedAndTheVaultIsNeverRecreated() {
        committedVault()
        prefs.commit { clear() }
        clearInvocations(database)

        val next = restarted()

        assertEquals(InitialStatus.AUTH, next.resolveInitialStatus())
        assertFalse(next.isOnboardingShow())
        assertFalse(next.initializeNewVault(null))
        verify(database, never()).wipe()
        assertEquals(VaultUnlockResult.SUCCESS, next.unlockWithPin(PIN.toCharArray()))
    }

    @Test
    fun aLostFlagOnACommittedVaultIsRefusedEvenBeforeStartupRoutingRan() {
        committedVault()
        prefs.commit { clear() }
        clearInvocations(database)

        assertFalse(restarted().initializeNewVault(null))
        verify(database, never()).wipe()
        assertTrue(manager.hasRecoverySeed())
    }

    @Test
    fun aLostFlagOnAVaultWithOnlyARecoverySlotIsStillCommitted() {
        committedVault()
        auth.wipe()
        prefs.commit { clear() }

        assertEquals(InitialStatus.AUTH, restarted().resolveInitialStatus())
    }

    @Test
    fun aFlagsStoreThatCanNotBeWrittenStillLocksACommittedVault() {
        committedVault()
        val flagless = object : KeyValueStore {
            override fun getString(key: String, default: String?) = default
            override fun getBoolean(key: String, default: Boolean) = default
            override fun getLong(key: String, default: Long) = default
            override fun contains(key: String) = false
            override fun edit(block: KeyValueStore.Editor.() -> Unit) = Unit
            override fun commit(block: KeyValueStore.Editor.() -> Unit) = false
        }
        val next = restarted(flags = flagless)

        assertEquals(InitialStatus.AUTH, next.resolveInitialStatus())
        assertEquals(VaultUnlockResult.SUCCESS, next.unlockWithPin(PIN.toCharArray()))
        assertTrue("backgrounding and Lock now still close the vault", next.isLockable())
    }

    @Test
    fun aLostFlagAndALostDatabaseHasNothingToProtectAndRestartsOnboarding() {
        committedVault()
        prefs.commit { clear() }
        `when`(database.hasEncryptedDatabase()).thenReturn(false)

        assertEquals(InitialStatus.ONBOARDING, restarted().resolveInitialStatus())
    }

    @Test
    fun aVaultFileThatCanNotBeReadRightNowIsNeitherDamagedNorOnboarding() {
        committedVault()
        prefs.commit { clear() }
        val io = object : VaultFileIo by JvmVaultFileIo(file) {
            var failReads = true
            override fun read(): ByteArray = if (failReads) throw java.io.IOException("locked") else file.readBytes()
        }
        val locked = restarted(VaultStore(io))
        clearInvocations(database)

        assertEquals(InitialStatus.STORAGE_UNREADABLE, locked.resolveInitialStatus())
        assertFalse(locked.initializeNewVault(null))
        verify(database, never()).wipe()

        io.failReads = false

        assertEquals(InitialStatus.AUTH, locked.resolveInitialStatus())
        assertFalse(file.readBytes().isEmpty())
    }

    private companion object {
        const val PIN = "135790"
        const val NEW_PIN = "246813"
        const val WRONG_PIN = "000111"
        const val LOCKOUT_AFTER = 5
    }
}
