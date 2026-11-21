package com.pasich.encly.core.security

import androidx.lifecycle.LifecycleOwner
import com.pasich.encly.testutil.DuressVaultFixture
import com.pasich.encly.testutil.DuressVaultFixture.Companion.DURESS_PIN
import com.pasich.encly.testutil.DuressVaultFixture.Companion.PIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * An unlock (PIN check, then opening the database, then publishing) that an OS lock interrupts
 * (issue #131): the screen turning off, the lock key, the keyguard. Real [SecurityManager] and
 * [SessionLockManager] over a fake database; the PIN is the real one and the duress one in turn.
 * The app must end locked with no vault open (and no decoy session left), and the next unlock
 * must work.
 */
class UnlockInterruptedByOsLockTest {
    private val owner = mock(LifecycleOwner::class.java)
    private val deviceLock = FakeDeviceLock()
    private lateinit var vault: DuressVaultFixture
    private lateinit var manager: SessionLockManager

    @Before
    fun setUp() {
        vault = DuressVaultFixture(withDuressPin = true)
        manager = SessionLockManager(vault.security, deviceLock = deviceLock)
        manager.requireUnlock()
        manager.onStart(owner)
    }

    private val pins = listOf(PIN to VaultKind.PRIMARY, DURESS_PIN to VaultKind.DECOY)

    /** Checks the PIN and opens the database, as the lock screen's unlock does before it publishes. */
    private fun check(pin: String) = vault.security.unlockWithPin(pin.toCharArray())

    private fun assertLockedWithNothingOpen() {
        assertTrue(manager.locked.value)
        assertFalse(vault.security.isDatabaseUnlocked())
        assertNull(vault.db.open)
        assertFalse(vault.security.isDecoySession())
    }

    private fun assertNextUnlockWorks(pin: String, kind: VaultKind) {
        val started = manager.lockRequests()
        assertEquals(VaultUnlockResult.SUCCESS, check(pin))
        assertTrue(manager.onUnlocked(started))
        assertFalse(manager.locked.value)
        assertEquals(kind, vault.db.open)
        assertEquals(kind == VaultKind.DECOY, vault.security.isDecoySession())
    }

    @Test
    fun anOsLockRequestedBeforeTheDatabaseOpenedKeepsTheAppLocked() {
        pins.forEach { (pin, kind) ->
            val started = manager.lockRequests()
            manager.lockNow() // nothing is open yet: it can only be counted
            assertEquals(VaultUnlockResult.SUCCESS, check(pin))

            assertFalse(manager.onUnlocked(started))

            assertLockedWithNothingOpen()
            assertNextUnlockWorks(pin, kind)
            vault.security.lock()
        }
    }

    @Test
    fun anOsLockRequestedAfterTheDatabaseOpenedClosesItBeforeTheUnlockIsPublished() {
        pins.forEach { (pin, kind) ->
            val started = manager.lockRequests()
            assertEquals(VaultUnlockResult.SUCCESS, check(pin))
            manager.lockNow() // the vault is open now: it is closed

            assertTrue(vault.db.open == null)
            assertFalse(manager.onUnlocked(started))

            assertLockedWithNothingOpen()
            assertNextUnlockWorks(pin, kind)
            vault.security.lock()
        }
    }

    @Test
    fun theKeyguardComingUpDuringTheCheckKeepsTheAppLocked() {
        pins.forEach { (pin, kind) ->
            val started = manager.lockRequests()
            assertEquals(VaultUnlockResult.SUCCESS, check(pin))
            deviceLock.locked = true

            assertFalse(manager.onUnlocked(started))
            assertTrue(manager.isDeviceLocked())

            assertLockedWithNothingOpen()
            deviceLock.locked = false
            assertNextUnlockWorks(pin, kind)
            vault.security.lock()
        }
    }

    @Test
    fun theScreenTurningOffDuringTheCheckKeepsTheAppLockedWhileItStaysOff() {
        pins.forEach { (pin, kind) ->
            val started = manager.lockRequests()
            deviceLock.screenOn = false
            manager.onStop(owner) // the activity stops with the screen off; nothing is open to close
            assertEquals(VaultUnlockResult.SUCCESS, check(pin))

            assertFalse(manager.onUnlocked(started))

            assertLockedWithNothingOpen()
            deviceLock.screenOn = true
            manager.onStart(owner)
            assertNextUnlockWorks(pin, kind)
            vault.security.lock()
        }
    }

    @Test
    fun theScreenGoingOffAndOnDuringTheCheckWithAnInsecureKeyguardKeepsTheAppLocked() {
        pins.forEach { (pin, kind) ->
            val started = manager.lockRequests()
            deviceLock.screenOn = false
            manager.onStop(owner) // nothing is open yet; the lock request must still be counted
            deviceLock.screenOn = true // an insecure keyguard: isDeviceLocked() stays false
            manager.onStart(owner)
            assertFalse(deviceLock.isDeviceLocked())
            assertEquals(VaultUnlockResult.SUCCESS, check(pin))

            assertFalse(manager.onUnlocked(started))

            assertLockedWithNothingOpen()
            assertNextUnlockWorks(pin, kind)
            vault.security.lock()
        }
    }

    @Test
    fun aBackgroundStopDuringTheCheckKeepsTheAppLocked() {
        pins.forEach { (pin, kind) ->
            val started = manager.lockRequests()
            manager.onStop(owner)
            assertEquals(VaultUnlockResult.SUCCESS, check(pin))

            assertFalse(manager.onUnlocked(started))

            assertLockedWithNothingOpen()
            manager.onStart(owner)
            assertNextUnlockWorks(pin, kind)
            vault.security.lock()
        }
    }

    @Test
    fun aRealUnlockInterruptedAfterADecoyOneStillOpensTheRealVaultNotTheDecoyOne() {
        val started = manager.lockRequests()
        assertEquals(VaultUnlockResult.SUCCESS, check(DURESS_PIN))
        manager.lockNow()
        assertFalse(manager.onUnlocked(started))
        assertLockedWithNothingOpen()

        assertNextUnlockWorks(PIN, VaultKind.PRIMARY)
    }

    private class FakeDeviceLock : DeviceLockWatcher {
        var locked = false
        var screenOn = true

        override fun isDeviceLocked(): Boolean = locked

        override fun isScreenOn(): Boolean = screenOn

        override fun watchScreenOff(onScreenOff: () -> Unit): () -> Unit = {}
    }
}
