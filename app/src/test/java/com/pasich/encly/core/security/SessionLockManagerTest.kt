package com.pasich.encly.core.security

import androidx.lifecycle.LifecycleOwner
import org.junit.Assert.assertFalse
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
}
