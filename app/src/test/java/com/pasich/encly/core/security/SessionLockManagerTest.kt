package com.pasich.encly.core.security

import androidx.lifecycle.LifecycleOwner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class SessionLockManagerTest {

    private val owner = mock(LifecycleOwner::class.java)
    private lateinit var security: SecurityManager
    private lateinit var manager: SessionLockManager

    @Before
    fun setUp() {
        security = mock(SecurityManager::class.java)
        `when`(security.isLockable()).thenReturn(true)
        manager = SessionLockManager(security)
        manager.onStart(owner)
    }

    @Test
    fun backgroundingAnOpenVaultLocksIt() {
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.onStop(owner)

        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    @Test
    fun backgroundingWithoutAnOpenVaultDoesNothing() {
        manager.onStop(owner)

        verify(security, never()).lock()
        assertFalse(manager.locked.value)
    }

    @Test
    fun backgroundingBeforeAnyLockIsConfiguredDoesNothing() {
        `when`(security.isLockable()).thenReturn(false)
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.onStop(owner)

        verify(security, never()).lock()
    }

    @Test
    fun lockNowClosesAnOpenVault() {
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.lockNow()

        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    @Test
    fun lockNowBeforeOnboardingIsCommittedDoesNothing() {
        `when`(security.isLockable()).thenReturn(false)
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.lockNow()

        verify(security, never()).lock()
        assertFalse(manager.locked.value)
    }

    @Test
    fun unlockInTheForegroundIsPublished() {
        `when`(security.isDatabaseUnlocked()).thenReturn(true)
        manager.onStop(owner)
        manager.onStart(owner)

        assertTrue(manager.onUnlocked())

        assertFalse(manager.locked.value)
    }

    @Test
    fun unlockThatFinishesAfterBackgroundingIsClosedAgain() {
        // PBKDF2 still running: ON_STOP sees no open database and does nothing...
        manager.onStop(owner)
        verify(security, never()).lock()

        // ...then the vault opens while the app is in the background.
        val published = manager.onUnlocked()

        assertFalse(published)
        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    @Test
    fun unlockRacingOnStopStaysLocked() {
        // The database opened just before ON_STOP: ON_STOP closes it, and the late
        // onUnlocked() must not reopen the UI.
        `when`(security.isDatabaseUnlocked()).thenReturn(true)
        manager.onStop(owner)

        assertFalse(manager.onUnlocked())

        verify(security, times(2)).lock()
        assertTrue(manager.locked.value)
    }

    @Test
    fun unlockBeforeTheProcessEverStartedIsNotPublished() {
        val fresh = SessionLockManager(security)

        assertFalse(fresh.onUnlocked())
        assertTrue(fresh.locked.value)
    }

    // --- system file picker (backup export/import) --------------------------------------

    private var now = 0L
    private var pendingTimer: (() -> Unit)? = null

    private fun withFakeTime() {
        manager.clock = { now }
        manager.timer = { _, action ->
            pendingTimer = action
            val cancel: () -> Unit = { pendingTimer = null }
            cancel
        }
    }

    @Test
    fun openingTheFilePickerKeepsTheVaultOpen() {
        withFakeTime()
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.allowSystemPicker()
        now += 1_000
        manager.onStop(owner)
        now += 30_000
        manager.onStart(owner)

        verify(security, never()).lock()
        assertFalse(manager.locked.value)
    }

    @Test
    fun theAllowanceIsSingleUseAndShortLived() {
        withFakeTime()
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.allowSystemPicker()
        now += 6_000 // the picker never opened in time
        manager.onStop(owner)

        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    @Test
    fun stayingAwayFromThePickerTooLongLocksOnReturn() {
        withFakeTime()
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.allowSystemPicker()
        manager.onStop(owner)
        now += 6 * 60_000
        manager.onStart(owner)

        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    @Test
    fun leavingThePickerForAnotherAppLocksAfterTheTimeout() {
        withFakeTime()
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.allowSystemPicker()
        manager.onStop(owner)
        pendingTimer!!.invoke()

        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    // --- picker grace: capped, monotonic, ended by screen-off / keyguard / return -------

    private val watcher = FakeDeviceLock()

    private fun withWatcher() {
        manager = SessionLockManager(security, watcher)
        manager.onStart(owner)
        withFakeTime()
    }

    @Test
    fun thePickerGraceIsCappedAtAMinute() {
        withFakeTime()
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.allowSystemPicker()
        manager.onStop(owner)
        now += 61_000
        manager.onStart(owner)

        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    @Test
    fun deepSleepCountsAgainstTheGraceEvenIfTheTimerNeverFired() {
        // Handler timers stop in deep sleep; elapsedRealtime does not. The return re-checks.
        withFakeTime()
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.allowSystemPicker()
        manager.onStop(owner)
        now += 3 * 60 * 60_000L // three hours, screen off, timer never ran
        manager.onStart(owner)

        verify(security).lock()
    }

    @Test
    fun theScreenTurningOffDuringThePickerLocksAtOnce() {
        withWatcher()
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.allowSystemPicker()
        manager.onStop(owner)
        watcher.screenOff()

        verify(security).lock()
        assertTrue(manager.locked.value)
        assertFalse("the receiver is gone once the grace ended", watcher.watching)
    }

    @Test
    fun aKeyguardShownMeanwhileLocksOnReturn() {
        withWatcher()
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.allowSystemPicker()
        manager.onStop(owner)
        now += 10_000
        watcher.locked = true
        manager.onStart(owner)

        verify(security).lock()
    }

    @Test
    fun aQuickReturnFromThePickerKeepsTheVaultAndStopsWatching() {
        withWatcher()
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.allowSystemPicker()
        manager.onStop(owner)
        assertTrue(watcher.watching)
        now += 10_000
        manager.onStart(owner)

        verify(security, never()).lock()
        assertFalse(watcher.watching)
        assertNull(pendingTimer)
    }

    @Test
    fun thePickerReturningEndsTheAllowance() {
        withFakeTime()
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.allowSystemPicker()
        manager.endSystemPicker() // e.g. the picker could not open
        manager.onStop(owner) // Home, within the launch window

        verify(security).lock()
    }

    @Test
    fun startupWithACommittedVaultStartsLocked() {
        manager.requireUnlock()

        assertTrue(manager.locked.value)
        assertTrue(manager.onUnlocked())
        assertFalse(manager.locked.value)
    }

    @Test
    fun anAbandonedUnlockIsClosedAgain() {
        `when`(security.isDatabaseUnlocked()).thenReturn(true)

        manager.onUnlockAbandoned()

        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    // --- auto-lock delay ------------------------------------------------------------------

    private fun withDelay(millis: Long) {
        manager = SessionLockManager(
            security,
            watcher,
            autoLock = object : AutoLockPolicy {
                override val delayMillis: Long = millis
            },
        )
        manager.onStart(owner)
        withFakeTime()
        `when`(security.isDatabaseUnlocked()).thenReturn(true)
    }

    @Test
    fun comingBackWithinTheAutoLockDelayKeepsTheVaultOpen() {
        withDelay(15_000)

        manager.onStop(owner)
        now += 10_000
        manager.onStart(owner)

        verify(security, never()).lock()
        assertFalse(manager.locked.value)
        assertFalse(watcher.watching)
    }

    @Test
    fun theAutoLockDelayRunningOutLocksInTheBackground() {
        withDelay(15_000)

        manager.onStop(owner)
        pendingTimer!!.invoke()

        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    @Test
    fun comingBackAfterTheAutoLockDelayLocks() {
        withDelay(30_000)

        manager.onStop(owner)
        now += 31_000 // deep sleep: the timer never fired
        manager.onStart(owner)

        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    @Test
    fun theScreenTurningOffIgnoresTheAutoLockDelay() {
        withDelay(120_000)

        manager.onStop(owner)
        watcher.screenOff()

        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    @Test
    fun anImmediateAutoLockLocksOnLeaving() {
        withDelay(0)

        manager.onStop(owner)

        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    private class FakeDeviceLock : DeviceLockWatcher {
        var locked = false
        var watching = false
        private var onScreenOff: (() -> Unit)? = null

        override fun isDeviceLocked(): Boolean = locked

        override fun watchScreenOff(onScreenOff: () -> Unit): () -> Unit {
            watching = true
            this.onScreenOff = onScreenOff
            return {
                watching = false
                this.onScreenOff = null
            }
        }

        fun screenOff() {
            onScreenOff?.invoke()
        }
    }
}
