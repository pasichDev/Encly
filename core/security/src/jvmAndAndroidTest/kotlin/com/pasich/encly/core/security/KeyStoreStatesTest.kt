package com.pasich.encly.core.security

import androidx.lifecycle.LifecycleOwner
import com.pasich.encly.testutil.DuressVaultFixture
import com.pasich.encly.testutil.DuressVaultFixture.Companion.PIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * What the device key store can say besides "wrong": not found (a desktop keyring that may be another one), locked,
 * not running, a prompt dismissed; and what the vault does with it. Over the real managers and a fake store.
 */
class KeyStoreStatesTest {
    private val owner = mock(LifecycleOwner::class.java)
    private lateinit var vault: DuressVaultFixture
    private lateinit var auth: AuthenticationManager

    @Before
    fun setUp() {
        vault = DuressVaultFixture()
        auth = AuthenticationManager(vault.store, vault.factor, vault.clock)
    }

    @Test
    fun aKeyThatIsNotFoundIsItsOwnStateAndNothingIsCounted() {
        vault.factor.notFound = true

        assertEquals(PinUnlock.KeyStoreNotFound, auth.unlockWithPin(PIN.toCharArray()))
        assertEquals(VaultUnlockResult.KEY_NOT_FOUND, vault.security.unlockWithPin(PIN.toCharArray()))

        assertEquals(0L, auth.remainingLockoutMillis())
        vault.factor.notFound = false
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
    }

    @Test
    fun aKeyThatIsLostIsStillLostAndNotFoundIsNotTheSame() {
        vault.factor.lost = true

        assertEquals(VaultUnlockResult.PIN_KEY_LOST, vault.security.unlockWithPin(PIN.toCharArray()))
    }

    @Test
    fun aCheckOfTheOpenVaultsSecretSaysWhatItFound() {
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        assertEquals(PinCheck.OK, vault.security.verifyPinResult(PIN.toCharArray()))
        assertEquals(PinCheck.WRONG, vault.security.verifyPinResult("000000".toCharArray()))

        vault.factor.unavailable = true
        assertEquals(PinCheck.KEY_STORE_UNAVAILABLE, vault.security.verifyPinResult(PIN.toCharArray()))
        vault.factor.unavailable = false
        vault.factor.notFound = true
        assertEquals(PinCheck.KEY_NOT_FOUND, vault.security.verifyPinResult(PIN.toCharArray()))
        vault.factor.notFound = false
        vault.factor.failing = true
        assertEquals(PinCheck.KEY_STORE_FAILED, vault.security.verifyPinResult(PIN.toCharArray()))
        vault.factor.failing = false
        vault.factor.lost = true
        assertEquals(PinCheck.KEY_LOST, vault.security.verifyPinResult(PIN.toCharArray()))
        vault.factor.lost = false
        assertEquals(PinCheck.OK, vault.security.verifyPinResult(PIN.toCharArray()))
        // The old Boolean answer is the same thing.
        assertTrue(vault.security.verifyPin(PIN.toCharArray()))
        assertFalse(vault.security.verifyPin("000000".toCharArray()))
    }

    @Test
    fun aDecoySessionsCheckSaysTheSameThings() {
        val duress = DuressVaultFixture(withDuressPin = true)
        assertEquals(
            VaultUnlockResult.SUCCESS,
            duress.security.unlockWithPin(DuressVaultFixture.DURESS_PIN.toCharArray()),
        )
        assertEquals(PinCheck.OK, duress.security.verifyPinResult(DuressVaultFixture.DURESS_PIN.toCharArray()))
        duress.factor.unavailable = true
        assertEquals(
            PinCheck.KEY_STORE_UNAVAILABLE,
            duress.security.verifyPinResult(DuressVaultFixture.DURESS_PIN.toCharArray()),
        )
    }

    @Test
    fun theProblemOfAnUnusableKeyStoreIsKeptForTheMessage() {
        vault.factor.unavailable = true
        vault.factor.problem = KeyStoreProblem.NOT_RUNNING

        assertEquals(VaultUnlockResult.KEY_STORE_UNAVAILABLE, vault.security.unlockWithPin(PIN.toCharArray()))
        assertEquals(KeyStoreProblem.NOT_RUNNING, vault.security.keyStoreProblem())

        // A problem the store did not name is "other"; a later answer that is not "unavailable" clears it.
        vault.factor.problem = null
        vault.security.unlockWithPin(PIN.toCharArray())
        assertEquals(KeyStoreProblem.OTHER, vault.security.keyStoreProblem())
        vault.factor.unavailable = false
        vault.factor.failing = true
        vault.security.unlockWithPin(PIN.toCharArray())
        assertNull(vault.security.keyStoreProblem())
    }

    @Test
    fun aPinChangeThatMeetsAnUnusableKeyStoreSaysWhatIsMissing() {
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        vault.factor.unavailable = true
        vault.factor.problem = KeyStoreProblem.NO_DEFAULT
        vault.factor.delete(PinKeySlot.A)

        assertEquals(PinChange.KEY_STORE_UNAVAILABLE, vault.security.changePin("246813".toCharArray()))
        assertEquals(KeyStoreProblem.NO_DEFAULT, vault.security.keyStoreProblem())
    }

    @Test
    fun theKeyStoreIsPreparedBeforeAVaultDependsOnIt() {
        assertFalse(vault.security.keyStoreMayNeedPreparing())
        vault.factor.missingMayReturn = true
        assertTrue(vault.security.keyStoreMayNeedPreparing())

        assertNull(vault.security.prepareKeyStore())
        assertEquals(1, vault.factor.prepared)

        vault.factor.prepareProblem = KeyStoreProblem.LOCKED
        assertEquals(KeyStoreProblem.LOCKED, vault.security.prepareKeyStore())
        assertEquals(KeyStoreProblem.LOCKED, vault.security.keyStoreProblem())
    }

    @Test
    fun aKeyThatCannotBeFoundSendsARecoveryUnlockToANewSecretLikeALostOne() {
        vault.factor.notFound = true

        assertEquals(RecoveryFollowUp.MUST_SET_PIN, auth.recoveryFollowUp())
    }

    @Test
    fun aNewVaultNeverAdoptsTheDeviceSecretAnErasedOneLeftBehind() {
        // A fresh vault with no secret slot yet, and an item an erase could not delete still in the store.
        val fresh = DuressVaultFixture()
        fresh.security.wipeAndReset()
        val leftover = ByteArray(32) { 9 }
        fresh.factor.leave(PinKeySlot.A, leftover)
        val seed = SeedPhraseManager(fresh.store)
        val fresh2 = AuthenticationManager(fresh.store, fresh.factor, fresh.clock)
        assertTrue(seed.initializeVault(seed.generateMnemonic()))
        val bootstrap = seed.copyBootstrapKey()!!

        assertEquals(PinChange.SET, fresh2.changePin("135790".toCharArray(), bootstrap))

        assertFalse("the old secret is replaced", fresh.factor.keyOf(PinKeySlot.A) == leftover.toList())
    }

    @Test
    fun aPinChangeOfALiveVaultKeepsItsDeviceSecret() {
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        val before = vault.factor.keyOf(PinKeySlot.A)

        assertEquals(PinChange.SET, vault.security.changePin("246813".toCharArray()))

        assertEquals("the wipe and duress slots depend on it", before, vault.factor.keyOf(PinKeySlot.A))
    }

    @Test
    fun aLockEndsAKeyringPromptTheUnlockWaitsOn() {
        val manager = SessionLockManager(vault.security)
        manager.onStart(owner)
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))

        vault.security.lock()
        assertEquals(1, vault.factor.abandoned)

        // A lock request with nothing open ends it too, and so does a relock.
        manager.lockNow()
        assertEquals(2, vault.factor.abandoned)
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        manager.lockNow()
        assertTrue("closing the open vault abandons once more", vault.factor.abandoned >= 4)
        assertTrue(manager.locked.value)
    }

    @Test
    fun aFactorThatDoesNotOverrideAnythingNeedsNoPreparingAndHasNoPromptToEnd() {
        val phone = object : PinHardwareFactor {
            override fun ensureKey(slot: PinKeySlot): Boolean = false

            override fun reset(slot: PinKeySlot) = Unit

            override fun mac(slot: PinKeySlot, data: ByteArray): ByteArray = ByteArray(32)

            override fun delete(slot: PinKeySlot) = Unit
        }

        assertFalse(phone.missingKeyMayReturn)
        phone.prepare()
        phone.abandonPending()
        assertNull(AuthenticationManager(vault.store, phone, vault.clock).prepareKeyStore())
    }

    @Test
    fun aLockWhileTheUnlockWaitsOnAKeyringPromptEndsTheWaitAndTheUnlockIsNotPublished() {
        val manager = SessionLockManager(vault.security)
        manager.onStart(owner)
        vault.factor.promptOpen = java.util.concurrent.CountDownLatch(1)
        val started = manager.lockRequests()
        var result: VaultUnlockResult? = null
        val unlocking = Thread { result = vault.security.unlockWithPin(PIN.toCharArray()) }.apply { start() }
        assertTrue(
            "the unlock is waiting on the prompt",
            vault.factor.asking.await(5, java.util.concurrent.TimeUnit.SECONDS),
        )

        manager.lockNow() // the lock must not wait for the prompt, and must end it
        unlocking.join(5_000)

        assertFalse("the unlock returned long before the prompt's timeout", unlocking.isAlive)
        assertEquals(VaultUnlockResult.KEY_STORE_UNAVAILABLE, result)
        assertEquals(KeyStoreProblem.DISMISSED, vault.security.keyStoreProblem())
        assertEquals("no attempt was counted", 0L, auth.remainingLockoutMillis())
        assertFalse(manager.onUnlocked(started))
        assertFalse(vault.security.isDatabaseUnlocked())
    }

    @Test
    fun anEraseEndsItToo() {
        val manager = SessionLockManager(vault.security)
        manager.onStart(owner)

        manager.onVaultErased()

        assertEquals(1, vault.factor.abandoned)
    }
}
