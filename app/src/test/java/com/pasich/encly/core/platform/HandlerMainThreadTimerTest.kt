package com.pasich.encly.core.platform

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** The Android [MainThreadTimer]: runs the action once after the delay on the main looper, and cancel stops it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HandlerMainThreadTimerTest {
    private val timer = HandlerMainThreadTimer()
    private var runs = 0

    private fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    @Test
    fun theActionRunsOnceWhenTheDelayIsOver() {
        timer.schedule(1_000L) { runs++ }

        pass(999L)
        assertEquals(0, runs)
        pass(2L)
        assertEquals(1, runs)
        pass(5_000L)
        assertEquals(1, runs)
    }

    @Test
    fun cancellingStopsIt() {
        val cancel = timer.schedule(1_000L) { runs++ }

        cancel()
        pass(5_000L)

        assertEquals(0, runs)
    }

    @Test
    fun theNoTimerNeverRuns() {
        MainThreadTimer.None.schedule(0L) { runs++ }()

        pass(1_000L)
        assertEquals(0, runs)
    }
}
