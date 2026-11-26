package com.pasich.encly.core.security

import com.pasich.encly.testutil.FakeLockoutClock
import com.pasich.encly.testutil.FakePinFactor
import com.pasich.encly.testutil.FakeV4PasswordPolicy
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * What the slot checks of [AuthenticationManager] and [SecurityManager] say about a vault whose secret slot the bound
 * policy does not accept: a version 3 slot on a platform with the version 4 policy is "no usable secret slot" (the
 * recovery phrase opens the vault and a new secret is set, or, with no phrase, the vault counts as damaged), and the
 * PIN policy keeps saying "there is a slot" for whatever the entry holds, as it always has.
 */
class SecretSlotStateTest {
    private val store = VaultStore(tempVaultFile())
    private val prefs = InMemoryKeyValueStore()
    private val seed = SeedPhraseManager(store)
    private val factor = FakePinFactor()
    private val clock = FakeLockoutClock()
    private val database = mock(VaultDatabase::class.java)
    private val biometric = mock(BiometricManager::class.java)
    private val pinAuth = AuthenticationManager(store, factor, clock)
    private val policy = FakeV4PasswordPolicy()
    private val passwordAuth = AuthenticationManager(store, factor, clock, policy)
    private val pinManager = SecurityManager(prefs, store, seed, database, pinAuth, biometric) {}
    private val passwordManager = SecurityManager(prefs, store, seed, database, passwordAuth, biometric) {}
    private val phrase = seed.generateMnemonic()

    @Before
    fun setUp() {
        `when`(database.unlockDatabase(anyByteArray(), anyBoolean())).thenReturn(true)
    }

    /** A committed vault with the version 3 PIN, as every vault made so far is. */
    private fun createPinVault(withRecovery: Boolean) {
        assertTrue(pinManager.initializeNewVault(if (withRecovery) phrase.copyOf() else null))
        assertTrue(pinManager.configurePin(PIN.toCharArray()))
        assertTrue(pinManager.finishInitialSetup())
        pinManager.lock()
        `when`(database.hasEncryptedDatabase()).thenReturn(true)
    }

    // --- A version 3 slot under the version 4 policy ----------------------------------------------

    @Test
    fun aVersion3SlotWithARecoveryPhraseOpensByPhraseAndForcesANewSecretOfTheNewFormat() {
        createPinVault(withRecovery = true)
        assertEquals(3, store.getBytes("pin.slot")!![0].toInt())

        assertEquals(InitialStatus.AUTH, passwordManager.resolveInitialStatus())
        assertFalse(passwordAuth.hasPinSlot())
        assertEquals(AuthStrategy.RECOVERY_DATA, passwordManager.authStrategy())
        // The PIN is no secret here: nothing is counted, derived or sent to the device factor.
        policy.resetCounts()
        val macs = factor.calls
        assertEquals(VaultUnlockResult.INVALID_CREDENTIAL, passwordManager.unlockWithPin(PIN.toCharArray()))
        assertEquals(0, policy.stretchCalls)
        assertEquals(macs, factor.calls)
        assertEquals(0, store.getInt("lockout.failures", 0))

        assertEquals(VaultUnlockResult.SUCCESS, passwordManager.unlockWithSeed(phrase.copyOf()))
        assertEquals(RecoveryStage.MUST_SET_PIN, passwordManager.recoveryStage())
        assertEquals(RecoveryFollowUp.MUST_SET_PIN, passwordAuth.recoveryFollowUp())
        assertEquals(PinChange.SET, passwordManager.changePin(PASSWORD.toCharArray()))

        // The new secret is a version 4 slot, with fillers of the same version; nothing of version 3 is left.
        assertEquals(85, store.getBytes("pin.slot")!!.size)
        assertEquals(4, store.getBytes("pin.slot")!![0].toInt())
        assertEquals(4, store.getBytes("pin.wipe.slot")!![0].toInt())
        assertEquals(4, store.getBytes("pin.decoy.slot")!![0].toInt())
        assertTrue(passwordAuth.hasPinSlot())
        assertEquals(RecoveryStage.NONE, passwordManager.recoveryStage())
        assertEquals(AuthStrategy.PIN, passwordManager.authStrategy())
        passwordManager.lock()
        assertEquals(VaultUnlockResult.SUCCESS, passwordManager.unlockWithPin(PASSWORD.toCharArray()))
    }

    @Test
    fun aVersion3SlotWithNoRecoveryPhraseIsADamagedVault() {
        createPinVault(withRecovery = false)

        assertEquals(InitialStatus.LOSS_CRYPTO, passwordManager.resolveInitialStatus())
        assertFalse(passwordManager.isLockable())
        assertFalse(passwordAuth.hasPinSlot())
        assertEquals(AuthStrategy.RECOVERY_DATA, passwordManager.authStrategy())
        assertEquals(RecoveryFollowUp.MUST_SET_PIN, passwordAuth.recoveryFollowUp())
        assertEquals(VaultUnlockResult.INVALID_CREDENTIAL, passwordManager.unlockWithPin(PIN.toCharArray()))
        assertEquals(0, store.getInt("lockout.failures", 0))
    }

    @Test
    fun aVersion3SlotWithNoRecoveryPhraseAndALostFlagIsStillADamagedVaultNeverANewOne() {
        createPinVault(withRecovery = false)
        // The flags file is lost: the vault must be recognised by what it holds, not by a flag.
        prefs.edit { clear() }

        assertTrue(passwordManager.isOnboardingShow())
        assertEquals(InitialStatus.LOSS_CRYPTO, passwordManager.resolveInitialStatus())
        assertFalse(passwordManager.initializeNewVault(null))
        assertEquals(3, store.getBytes("pin.slot")!![0].toInt())
    }

    @Test
    fun theSameVaultUnderThePinPolicyIsUntouched() {
        createPinVault(withRecovery = true)

        assertEquals(InitialStatus.AUTH, pinManager.resolveInitialStatus())
        assertTrue(pinAuth.hasPinSlot())
        assertEquals(AuthStrategy.PIN, pinManager.authStrategy())
        assertEquals(VaultUnlockResult.SUCCESS, pinManager.unlockWithPin(PIN.toCharArray()))
    }

    // --- hasPinSlot -------------------------------------------------------------------------------

    @Test
    fun underThePinPolicyAnEntryIsASlotWhateverItHolds() {
        assertFalse(pinAuth.hasPinSlot())
        val entries = listOf(
            ByteArray(0),
            GoldenVectors.chainPinSlot,
            GoldenVectors.chainPinSlot.copyOf(76),
            byteArrayOf(7, 1, 2, 3),
            ByteArray(85).also { it[0] = 4 },
        )
        for (entry in entries) {
            store.edit { putBytes("pin.slot", entry) }
            assertTrue("${entry.size} bytes, first ${entry.firstOrNull()}", pinAuth.hasPinSlot())
            assertEquals(store.contains("pin.slot"), pinAuth.hasPinSlot())
        }
    }

    @Test
    fun underThePasswordPolicyOnlyAnAcceptedSlotIsASlot() {
        assertFalse(passwordAuth.hasPinSlot())
        assertTrue(passwordAuth.configurePin(PASSWORD.toCharArray(), ByteArray(32) { 1 }))
        val good = store.getBytes("pin.slot")!!
        assertTrue(passwordAuth.hasPinSlot())

        val refused = listOf(
            ByteArray(0),
            GoldenVectors.chainPinSlot,
            good.copyOf(84),
            good.copyOf().also { it[0] = 7 },
            // Costs the policy does not accept: a memory of 2^32 - 1 KiB, and one pass.
            good.copyOf().also { slot -> for (at in 3..6) slot[at] = 0xFF.toByte() },
            good.copyOf().also { it[7] = 1 },
        )
        for (entry in refused) {
            store.edit { putBytes("pin.slot", entry) }
            assertFalse("${entry.size} bytes", passwordAuth.hasPinSlot())
        }
        store.edit { putBytes("pin.slot", good) }
        assertTrue(passwordAuth.hasPinSlot())
    }

    // --- What the UI asks ---------------------------------------------------------------------------

    @Test
    fun theKindAndTheRuleForANewSecretComeFromTheBoundPolicy() {
        assertEquals(SecretKind.PIN, pinAuth.secretKind)
        assertEquals(SecretKind.PIN, pinManager.secretKind)
        assertEquals(12, pinAuth.maxTypedUnits)
        assertEquals(12, pinManager.maxTypedUnits)
        assertEquals(256, passwordAuth.maxTypedUnits)
        assertEquals(256, passwordManager.maxTypedUnits)
        assertEquals(SecretRule.OK, pinAuth.checkNewSecret("123456".toCharArray()))
        // A PIN with a letter, or of another length, can not be a PIN at all.
        assertEquals(SecretRule.INVALID, pinAuth.checkNewSecret("12a456".toCharArray()))
        assertEquals(SecretRule.INVALID, pinManager.checkNewSecret("12345".toCharArray()))
        assertNull(pinAuth.suggestSecret())
        assertNull(pinManager.suggestSecret())

        assertEquals(SecretKind.PASSWORD, passwordAuth.secretKind)
        assertEquals(SecretKind.PASSWORD, passwordManager.secretKind)
        assertEquals(SecretRule.OK, passwordAuth.checkNewSecret(PASSWORD.toCharArray()))
        assertEquals(SecretRule.TOO_SHORT, passwordManager.checkNewSecret("short".toCharArray()))
        assertEquals(SecretRule.TOO_SIMPLE, passwordManager.checkNewSecret("123456789012".toCharArray()))
        assertEquals(SecretRule.INVALID, passwordAuth.checkNewSecret(CharArray(0)))
        // The policy's suggestion is its own (the test double offers none); the manager passes it on unchanged.
        assertNull(passwordManager.suggestSecret())
    }

    @Test
    fun checkingANewSecretWipesTheCopyAndLeavesTheTypedOneAlone() {
        val typed = PASSWORD.toCharArray()
        policy.canonicals.clear()

        passwordAuth.checkNewSecret(typed)

        assertEquals(PASSWORD, String(typed))
        assertNotNull(policy.canonicals.single())
        assertTrue(policy.canonicals.single().all { it == 0.toChar() })
    }

    private companion object {
        const val PIN = "482913"
        const val PASSWORD = "correct horse battery"
    }
}
