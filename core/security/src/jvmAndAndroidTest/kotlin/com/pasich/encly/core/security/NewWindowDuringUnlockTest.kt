package com.pasich.encly.core.security

import androidx.lifecycle.LifecycleOwner
import com.pasich.encly.testutil.DuressVaultFixture
import com.pasich.encly.testutil.DuressVaultFixture.Companion.PIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * A window that opens while an unlock is being checked is no lock request, and "Erase all data" reaches every window.
 * Real managers over a fake database and a fake key store.
 */
class NewWindowDuringUnlockTest {
    private val owner = mock(LifecycleOwner::class.java)
    private lateinit var vault: DuressVaultFixture
    private lateinit var manager: SessionLockManager

    @Before
    fun setUp() {
        vault = DuressVaultFixture()
        manager = SessionLockManager(vault.security)
        manager.requireUnlock(countsAsLockRequest = false)
        manager.onStart(owner)
    }

    @Test
    fun aWindowThatOpensWhileTheUnlockIsCheckedDoesNotThrowItAway() {
        val started = manager.lockRequests()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))

        // The second window's start: the vault is still not open for it as far as it knows.
        manager.requireUnlock(countsAsLockRequest = false)

        assertEquals(started, manager.lockRequests())
        assertTrue("the right unlock is published", manager.onUnlocked(started))
        assertFalse(manager.locked.value)
    }

    @Test
    fun aRealLockDuringTheUnlockStillWins() {
        val started = manager.lockRequests()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        manager.requireUnlock(countsAsLockRequest = false)

        manager.lockNow()

        assertFalse(manager.onUnlocked(started))
        assertTrue(manager.locked.value)
        assertFalse(vault.security.isDatabaseUnlocked())
    }

    @Test
    fun theDefaultStartStillCountsAsALockRequest() {
        val started = manager.lockRequests()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))

        manager.requireUnlock()

        assertFalse(manager.onUnlocked(started))
    }

    @Test
    fun aWindowThatOpensOnAnOpenVaultChangesNothing() {
        val started = manager.lockRequests()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        assertTrue(manager.onUnlocked(started))

        manager.requireUnlock(countsAsLockRequest = false)

        assertFalse("the open vault is not locked again", manager.locked.value)
    }

    @Test
    fun anEraseIsToldToEveryWindowWithoutLockingTheNewVaultThatFollows() {
        val started = manager.lockRequests()
        assertEquals(VaultUnlockResult.SUCCESS, vault.security.unlockWithPin(PIN.toCharArray()))
        assertTrue(manager.onUnlocked(started))
        assertEquals(0L, manager.erasedCount.value)
        val before = manager.lockRequests()

        assertTrue(vault.security.wipeAndReset())
        manager.onVaultErased()

        assertFalse("onboarding of the new vault is not behind a lock screen", manager.locked.value)
        assertEquals(1L, manager.erasedCount.value)
        assertEquals("an unlock that was running is not published", before + 1, manager.lockRequests())
    }
}
