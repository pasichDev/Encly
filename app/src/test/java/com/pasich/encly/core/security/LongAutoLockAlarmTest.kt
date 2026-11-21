package com.pasich.encly.core.security

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.core.platform.AlarmMainThreadTimer
import com.pasich.encly.core.platform.AwayLockReceiver
import com.pasich.encly.core.platform.HandlerMainThreadTimer
import com.pasich.encly.testutil.InMemoryKeyValueStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * The 15 and 30 minute auto-lock delays (issue #131) on the real alarm timer and the real stored setting: the
 * delay survives a process death, the wake-up alarm locks a frozen process, and the screen turning off or the
 * keyguard coming up ends the wait at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LongAutoLockAlarmTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val alarms: AlarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val owner = mock(LifecycleOwner::class.java)
    private val delays = listOf(AutoLockDelay.MINUTES_15, AutoLockDelay.MINUTES_30)

    private class FakeDeviceLock : DeviceLockWatcher {
        var locked = false
        var screenOn = true
        private var onScreenOff: (() -> Unit)? = null

        override fun isDeviceLocked(): Boolean = locked

        override fun isScreenOn(): Boolean = screenOn

        override fun watchScreenOff(onScreenOff: () -> Unit): () -> Unit {
            this.onScreenOff = onScreenOff
            return { this.onScreenOff = null }
        }

        fun screenOff() {
            screenOn = false
            onScreenOff?.invoke()
        }
    }

    private fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    private fun alarmId(): Int =
        shadowOf(shadowOf(alarms).nextScheduledAlarm!!.operation).savedIntent.getIntExtra(AwayLockReceiver.EXTRA_ID, -1)

    private class Process(val security: SecurityManager, val manager: SessionLockManager)

    /** A new process: the stored delay is read again, the open vault and every timer of the old one are gone. */
    private fun processWith(prefs: InMemoryKeyValueStore, deviceLock: FakeDeviceLock): Process {
        val security = mock(SecurityManager::class.java)
        `when`(security.isLockable()).thenReturn(true)
        val manager = SessionLockManager(
            security,
            deviceLock,
            autoLock = AutoLock(prefs),
            mainThread = AlarmMainThreadTimer(context, HandlerMainThreadTimer()),
        )
        manager.requireUnlock()
        manager.onStart(owner)
        `when`(security.isDatabaseUnlocked()).thenReturn(true)
        assertTrue(manager.onUnlocked())
        return Process(security, manager)
    }

    @Test
    fun theStoredDelayIsTheAlarmThatIsSetAfterAProcessRestart() {
        delays.forEach { delay ->
            val prefs = InMemoryKeyValueStore()
            AutoLock(prefs).setDelay(delay)
            val process = processWith(prefs, FakeDeviceLock())
            val before = SystemClock.elapsedRealtime()

            process.manager.onStop(owner)

            val alarm = shadowOf(alarms).nextScheduledAlarm
            assertNotNull(alarm)
            assertTrue(alarm!!.triggerAtTime - before in delay.millis..delay.millis + 1_000)
            process.manager.onStart(owner)
            assertNull("coming back cancels the alarm", shadowOf(alarms).nextScheduledAlarm)
            verify(process.security, never()).lock()
        }
    }

    @Test
    fun theAlarmLocksAFrozenProcessAndTheHandlerThatFollowsDoesNotLockAgain() {
        delays.forEach { delay ->
            val process = processWith(InMemoryKeyValueStore().also { AutoLock(it).setDelay(delay) }, FakeDeviceLock())
            process.manager.onStop(owner)

            assertTrue(AlarmMainThreadTimer.fire(alarmId()))

            verify(process.security).lock()
            assertTrue(process.manager.locked.value)
            pass(delay.millis + 1_000)
            verify(process.security, times(1)).lock()
        }
    }

    @Test
    fun theHandlerLocksAtTheDelayWhenTheProcessIsNotFrozenAndTheAlarmIsDropped() {
        delays.forEach { delay ->
            val process = processWith(InMemoryKeyValueStore().also { AutoLock(it).setDelay(delay) }, FakeDeviceLock())
            process.manager.onStop(owner)

            pass(delay.millis - 1)
            verify(process.security, never()).lock()
            pass(2)

            verify(process.security).lock()
            assertNull(shadowOf(alarms).nextScheduledAlarm)
        }
    }

    @Test
    fun theScreenTurningOffInsideTheDelayLocksAtOnceAndCancelsTheAlarm() {
        delays.forEach { delay ->
            val deviceLock = FakeDeviceLock()
            val process = processWith(InMemoryKeyValueStore().also { AutoLock(it).setDelay(delay) }, deviceLock)
            process.manager.onStop(owner)
            pass(delay.millis / 2)

            deviceLock.screenOff()

            verify(process.security).lock()
            assertTrue(process.manager.locked.value)
            assertNull(shadowOf(alarms).nextScheduledAlarm)
            pass(delay.millis)
            verify(process.security, times(1)).lock()
        }
    }

    @Test
    fun theKeyguardUpOnReturnInsideTheDelayLocksTheVault() {
        delays.forEach { delay ->
            val deviceLock = FakeDeviceLock()
            val process = processWith(InMemoryKeyValueStore().also { AutoLock(it).setDelay(delay) }, deviceLock)
            process.manager.onStop(owner)
            pass(delay.millis / 3)
            deviceLock.locked = true

            process.manager.onStart(owner)

            verify(process.security).lock()
            assertTrue(process.manager.locked.value)
            assertTrue(process.manager.isDeviceLocked())
            assertNull(shadowOf(alarms).nextScheduledAlarm)
        }
    }
}
