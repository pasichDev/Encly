package com.pasich.encly.core.security

import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** A lock lets the open editors write their pending changes first, and never waits for them for long. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionLockFlushTest {
    private val owner = mock(LifecycleOwner::class.java)
    private val security = mock(SecurityManager::class.java)
    private val scope = TestScope(StandardTestDispatcher())
    private val events = mutableListOf<String>()
    private lateinit var manager: SessionLockManager

    @Before
    fun setUp() {
        `when`(security.isLockable()).thenReturn(true)
        `when`(security.isDatabaseUnlocked()).thenReturn(true)
        doAnswer { events += "closed" }.`when`(security).lock()
        manager = SessionLockManager(security).apply { lockScope = CoroutineScope(scope.coroutineContext) }
        manager.onStart(owner)
    }

    @Test
    fun theVaultIsClosedAfterThePendingSaveFinished() {
        manager.registerPreLockFlush {
            delay(200)
            events += "saved"
        }

        manager.lockNow()
        assertFalse(manager.locked.value)
        scope.advanceTimeBy(250)
        scope.runCurrent()

        assertEquals(listOf("saved", "closed"), events)
        assertTrue(manager.locked.value)
    }

    @Test
    fun aSaveThatNeverFinishesDoesNotHoldTheLockPastTheBound() {
        manager.registerPreLockFlush { awaitCancellation() }

        manager.lockNow()
        scope.advanceTimeBy(LOCK_FLUSH_TIMEOUT_MS - 1)
        scope.runCurrent()
        verify(security, never()).lock()

        scope.advanceTimeBy(2)
        scope.runCurrent()

        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    @Test
    fun aFailingSaveStillLocks() {
        manager.registerPreLockFlush { error("disk") }

        manager.lockNow()
        scope.runCurrent()

        assertEquals(listOf("closed"), events)
        assertTrue(manager.locked.value)
    }

    @Test
    fun theDeviceLockPathWaitsOnlyTheSameBound() {
        manager.registerPreLockFlush { awaitCancellation() }

        manager.onStop(owner)
        scope.advanceTimeBy(LOCK_FLUSH_TIMEOUT_MS + 1)
        scope.runCurrent()

        verify(security).lock()
    }

    @Test
    fun twoLocksWhileWaitingCloseTheVaultOnce() {
        manager.registerPreLockFlush { delay(100) }

        manager.lockNow()
        manager.lockNow()
        scope.advanceTimeBy(150)
        scope.runCurrent()

        assertEquals(listOf("closed"), events)
    }

    @Test
    fun anUnregisteredFlushIsNotWaitedFor() {
        val unregister = manager.registerPreLockFlush { events += "saved" }
        unregister()

        manager.lockNow()

        assertEquals(listOf("closed"), events)
        assertTrue(manager.locked.value)
    }
}
