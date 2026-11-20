package com.pasich.encly.core.security

import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import com.pasich.encly.core.platform.HandlerMainThreadTimer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** The auto-lock delay on the real main-thread timer: it fires once, and coming back cancels it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionLockTimerTest {
    private val owner = mock(LifecycleOwner::class.java)
    private val security = mock(SecurityManager::class.java).also {
        `when`(it.isLockable()).thenReturn(true)
        `when`(it.isDatabaseUnlocked()).thenReturn(true)
    }
    private val manager = SessionLockManager(
        security,
        autoLock = object : AutoLockPolicy {
            override val delayMillis = DELAY_MS
        },
        mainThread = HandlerMainThreadTimer(),
    )

    private fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    @Test
    fun theVaultStaysOpenForTheDelayAndLocksWhenItRunsOut() {
        manager.onStart(owner)

        manager.onStop(owner)
        pass(DELAY_MS - 1)
        verify(security, never()).lock()
        assertFalse(manager.locked.value)

        pass(2)
        verify(security).lock()
        assertTrue(manager.locked.value)
    }

    @Test
    fun comingBackInTimeCancelsTheTimer() {
        manager.onStart(owner)
        manager.onStop(owner)
        pass(DELAY_MS / 2)

        manager.onStart(owner)
        pass(DELAY_MS * 2)

        verify(security, never()).lock()
        assertFalse(manager.locked.value)
    }

    private companion object {
        const val DELAY_MS = 15_000L
    }
}
