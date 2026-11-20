package com.pasich.encly.core.platform

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * The lock timer also sets an inexact, idle-allowed wake-up alarm for a process the system froze, and the alarm
 * reaches the same action through the non-exported receiver.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AlarmMainThreadTimerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val alarms: AlarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val timer by lazy { AlarmMainThreadTimer(context, HandlerMainThreadTimer()) }
    private var runs = 0

    private fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    @Test
    fun anAlarmIsSetForTheDelayAsAnInexactWakeUp() {
        timer.schedule(30_000L) { runs++ }

        val alarm = shadowOf(alarms).nextScheduledAlarm
        assertNotNull(alarm)
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm!!.type)
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals(0L, alarm.interval)
        assertEquals(0, runs)
    }

    @Test
    fun theAlarmReachesTheActionWhenTheHandlerWasFrozen() {
        timer.schedule(30_000L) { runs++ }
        val id = alarmId()

        assertTrue(AlarmMainThreadTimer.fire(id))
        assertEquals(1, runs)
        assertFalse("only once", AlarmMainThreadTimer.fire(id))
    }

    @Test
    fun theAlarmTargetsTheNonExportedReceiverOfTheApp() {
        timer.schedule(30_000L) { runs++ }

        val intent = shadowOf(shadowOf(alarms).nextScheduledAlarm!!.operation).savedIntent
        assertEquals(AwayLockReceiver::class.java.name, intent.component?.className)
        assertEquals(context.packageName, intent.component?.packageName)
        assertEquals(AwayLockReceiver.ACTION, intent.action)
    }

    @Test
    fun theHandlerRunningFirstCancelsTheAlarm() {
        timer.schedule(1_000L) { runs++ }

        pass(1_001L)

        assertEquals(1, runs)
        assertNull(shadowOf(alarms).nextScheduledAlarm)
    }

    @Test
    fun cancellingStopsTheHandlerAndTheAlarm() {
        val cancel = timer.schedule(1_000L) { runs++ }

        cancel()
        pass(5_000L)

        assertEquals(0, runs)
        assertNull(shadowOf(alarms).nextScheduledAlarm)
    }

    private fun alarmId(): Int =
        shadowOf(shadowOf(alarms).nextScheduledAlarm!!.operation).savedIntent.getIntExtra(AwayLockReceiver.EXTRA_ID, -1)
}
