package com.pasich.encly.desktop

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pasich.encly.core.security.AutoLockPolicy
import com.pasich.encly.core.security.BiometricHost
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.BiometricStatus
import com.pasich.encly.desktop.security.DesktopAppLifecycle
import com.pasich.encly.desktop.security.DesktopDeviceLockWatcher
import com.pasich.encly.desktop.security.IdleLock
import com.pasich.encly.desktop.security.NoBiometricManager
import com.pasich.encly.desktop.security.OsLockSignals
import com.pasich.encly.desktop.security.OsUptimeClock
import com.pasich.encly.desktop.security.PollingLockSignals
import com.pasich.encly.desktop.security.SwingMainThreadTimer
import com.pasich.encly.desktop.security.parseProcUptime
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAppLifecycleTest {
    private fun events(lifecycle: DesktopAppLifecycle): MutableList<Lifecycle.Event> {
        val seen = mutableListOf<Lifecycle.Event>()
        lifecycle.lifecycle.addObserver(LifecycleEventObserver { _, event -> seen += event })
        seen.clear()
        return seen
    }

    @Test
    fun `the app starts in front`() {
        assertTrue(DesktopAppLifecycle().inForeground)
    }

    @Test
    fun `leaving the window stops the app and coming back starts it`() {
        val lifecycle = DesktopAppLifecycle()
        val seen = events(lifecycle)
        lifecycle.windowDeactivated()
        assertFalse(lifecycle.inForeground)
        assertEquals(listOf(Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP), seen)
        seen.clear()
        lifecycle.windowActivated()
        assertTrue(lifecycle.inForeground)
        assertEquals(listOf(Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME), seen)
    }

    @Test
    fun `minimising leaves even while the window is still active`() {
        val lifecycle = DesktopAppLifecycle()
        lifecycle.windowMinimised()
        assertFalse(lifecycle.inForeground)
        lifecycle.windowRestored()
        assertTrue(lifecycle.inForeground)
    }

    @Test
    fun `an own dialog taking the focus is not leaving`() {
        val lifecycle = DesktopAppLifecycle()
        lifecycle.beginOwnDialog()
        lifecycle.windowDeactivated()
        assertTrue(lifecycle.inForeground)
        // The dialog closes before the window is told it is active again: still in front.
        lifecycle.endOwnDialog()
        assertTrue(lifecycle.inForeground)
        lifecycle.windowActivated()
        assertTrue(lifecycle.inForeground)
    }

    @Test
    fun `a dialog end without a begin changes nothing`() {
        val lifecycle = DesktopAppLifecycle()
        lifecycle.endOwnDialog()
        assertTrue(lifecycle.inForeground)
    }

    @Test
    fun `repeated events do not repeat the transition`() {
        val lifecycle = DesktopAppLifecycle()
        val seen = events(lifecycle)
        lifecycle.windowDeactivated()
        lifecycle.windowDeactivated()
        lifecycle.windowMinimised()
        assertEquals(2, seen.size)
    }

    @Test
    fun `destroying ends the lifecycle and later events are ignored`() {
        val lifecycle = DesktopAppLifecycle()
        val seen = events(lifecycle)
        lifecycle.destroy()
        assertEquals(listOf(Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY), seen)
        lifecycle.windowActivated()
        assertFalse(lifecycle.inForeground)
        // Destroying a stopped app only ends it.
        DesktopAppLifecycle().apply {
            windowDeactivated()
            destroy()
        }
    }
}

class IdleLockTest {
    private class Rig(delay: Long) {
        val timer = ManualTimer()
        var nowNanos = 0L
        var locks = 0
        val idle = IdleLock(
            timer = timer,
            policy = object : AutoLockPolicy {
                override val delayMillis: Long = delay
            },
            lockNow = { locks++ },
            clock = { nowNanos },
        )

        fun advance(millis: Long) {
            nowNanos += millis * NANOS_PER_MILLI
        }

        private companion object {
            const val NANOS_PER_MILLI = 1_000_000L
        }
    }

    @Test
    fun `the limit is never below the minimum, whatever the short auto-lock delay`() {
        val rig = Rig(delay = 15_000)
        rig.idle.start()
        assertEquals(IdleLock.MIN_IDLE_MILLIS, rig.timer.pending.single().delayMs)
    }

    @Test
    fun `a long auto-lock delay is the limit`() {
        val rig = Rig(delay = 30 * 60_000L)
        rig.idle.start()
        assertEquals(30 * 60_000L, rig.timer.pending.single().delayMs)
    }

    @Test
    fun `locks after the limit without input and keeps watching`() {
        val rig = Rig(delay = 0)
        rig.idle.start()
        rig.advance(IdleLock.MIN_IDLE_MILLIS)
        rig.timer.fireAll()
        assertEquals(1, rig.locks)
        assertEquals(IdleLock.MIN_IDLE_MILLIS, rig.timer.pending.single().delayMs)
    }

    @Test
    fun `input moves the deadline`() {
        val rig = Rig(delay = 0)
        rig.idle.start()
        rig.advance(IdleLock.MIN_IDLE_MILLIS - 1_000)
        rig.idle.touch()
        rig.advance(2_000)
        rig.timer.fireAll()
        assertEquals(0, rig.locks)
        // Re-armed for exactly the time that is left.
        assertEquals(IdleLock.MIN_IDLE_MILLIS - 2_000, rig.timer.pending.single().delayMs)
    }

    @Test
    fun `stop cancels the check`() {
        val rig = Rig(delay = 0)
        rig.idle.start()
        rig.idle.stop()
        assertTrue(rig.timer.pending.isEmpty())
        rig.idle.stop()
    }
}

class DesktopDeviceLockWatcherTest {
    private class Signals : OsLockSignals {
        var report: ((Boolean) -> Unit)? = null
        var closed = false

        override fun start(onChange: (locked: Boolean) -> Unit): AutoCloseable {
            report = onChange
            return AutoCloseable { closed = true }
        }
    }

    private fun watcher(signals: OsLockSignals) = DesktopDeviceLockWatcher(signals) { it() }

    @Test
    fun `the lock state follows the OS`() {
        val signals = Signals()
        val watcher = watcher(signals).also { it.start() }
        assertFalse(watcher.isDeviceLocked())
        signals.report!!(true)
        assertTrue(watcher.isDeviceLocked())
        signals.report!!(false)
        assertFalse(watcher.isDeviceLocked())
    }

    @Test
    fun `becoming locked tells the listeners once`() {
        val signals = Signals()
        val watcher = watcher(signals).also { it.start() }
        var lock = 0
        var screenOff = 0
        watcher.onLocked { lock++ }
        watcher.watchScreenOff { screenOff++ }
        signals.report!!(true)
        signals.report!!(true)
        assertEquals(1, lock)
        assertEquals(1, screenOff)
        signals.report!!(false)
        signals.report!!(true)
        assertEquals(2, lock)
    }

    @Test
    fun `stopped listeners hear nothing`() {
        val signals = Signals()
        val watcher = watcher(signals).also { it.start() }
        var heard = 0
        val stopLocked = watcher.onLocked { heard++ }
        val stopOff = watcher.watchScreenOff { heard++ }
        stopLocked()
        stopOff()
        signals.report!!(true)
        assertEquals(0, heard)
    }

    @Test
    fun `start listens once and close lets go`() {
        val signals = Signals()
        val watcher = watcher(signals)
        watcher.start()
        val first = signals.report
        watcher.start()
        assertEquals(first, signals.report)
        watcher.close()
        assertTrue(signals.closed)
        watcher.close()
    }

    @Test
    fun `an OS without lock signals never reports locked`() {
        val watcher = watcher(OsLockSignals.None).also { it.start() }
        assertFalse(watcher.isDeviceLocked())
        watcher.close()
    }

    @Test
    fun `callbacks are dispatched to the UI thread`() {
        val signals = Signals()
        val dispatched = mutableListOf<() -> Unit>()
        val watcher = DesktopDeviceLockWatcher(signals) { dispatched += it }.also { it.start() }
        var heard = 0
        watcher.onLocked { heard++ }
        signals.report!!(true)
        assertEquals(0, heard)
        dispatched.forEach { it() }
        assertEquals(1, heard)
    }

    @Test
    fun `polling reports changes of the answer`() {
        val answers = ArrayDeque(listOf(false, true, true, false))
        val reported = mutableListOf<Boolean>()
        val done = CountDownLatch(2)
        val polling = PollingLockSignals(
            intervalMillis = 5,
            probe = { if (answers.size > 1) answers.removeFirst() else answers.first() },
        )
        val subscription = polling.start {
            reported += it
            done.countDown()
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        subscription.close()
        assertEquals(listOf(true, false), reported.take(2))
    }

    @Test
    fun `polling accepts its own scheduler`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val polling = PollingLockSignals(intervalMillis = 1, probe = { false }, executor = { scheduler })
        polling.start { }.close()
        assertTrue(scheduler.isShutdown)
    }
}

class OsUptimeClockTest {
    @Test
    fun `the clock follows the OS uptime and the boot id`() {
        val source = FakeBootClock(uptime = 5_000, id = "boot-a")
        val clock = OsUptimeClock(source)
        assertTrue(clock.exact)
        assertEquals(5_000L, clock.elapsedRealtime())
        source.uptime = 9_000
        assertEquals(9_000L, clock.elapsedRealtime())
        val a = clock.bootCount()
        source.id = "boot-b"
        assertTrue(a != clock.bootCount())
        source.id = "boot-a"
        assertEquals(a, clock.bootCount())
    }

    @Test
    fun `without a boot id the count is minus one`() {
        assertEquals(-1, OsUptimeClock(FakeBootClock(id = null)).bootCount())
    }

    @Test
    fun `without an uptime the clock still runs, from the start of the process`() {
        val clock = OsUptimeClock(FakeBootClock(uptime = null))
        assertFalse(clock.exact)
        val first = clock.elapsedRealtime()
        Thread.sleep(5)
        assertTrue(clock.elapsedRealtime() >= first)
    }

    @Test
    fun `proc uptime is parsed to milliseconds`() {
        assertEquals(12_345_670L, parseProcUptime("12345.67 8910.11\n"))
        assertNull(parseProcUptime("garbage"))
        assertNull(parseProcUptime("-1.0 2.0"))
    }
}

class NoBiometricManagerTest {
    private val host = object : BiometricHost {}
    private val manager = NoBiometricManager()

    @Test
    fun `biometrics are never available`() {
        assertFalse(manager.isStrongBiometricAvailable())
        assertEquals(BiometricStatus.UNAVAILABLE, manager.strongBiometricStatus())
        assertFalse(manager.hasSlot())
        manager.disable()
    }

    @Test
    fun `enrolling and unlocking answer with nothing`() {
        var enrolled: Boolean? = null
        manager.enroll(host, ByteArray(32)) { enrolled = it }
        assertEquals(false, enrolled)
        var key: ByteArray? = byteArrayOf(1)
        manager.unlock(host) { key = it }
        assertNull(key)
    }

    @Test
    fun `an authentication request is an error`() {
        var errorCode = 0
        manager.authenticate(
            host,
            BiometricManager.BiometricType.GENERAL,
            object : BiometricManager.BiometricCallback {
                override fun onSuccess() = Unit

                override fun onError(errorCode: Int, errorMessage: String) {
                    reported = errorCode
                }

                override fun onFailed() = Unit
            },
        )
        errorCode = reported
        assertTrue(errorCode != 0)
    }

    private var reported = 0
}

class SwingMainThreadTimerTest {
    @Test
    fun `a scheduled action runs once on the UI thread`() {
        val ran = CountDownLatch(1)
        var onUiThread = false
        SwingMainThreadTimer().schedule(1) {
            onUiThread = SwingUtilities.isEventDispatchThread()
            ran.countDown()
        }
        assertTrue(ran.await(5, TimeUnit.SECONDS))
        assertTrue(onUiThread)
    }

    @Test
    fun `a cancelled action never runs`() {
        val ran = CountDownLatch(1)
        val cancel = SwingMainThreadTimer().schedule(50) { ran.countDown() }
        cancel()
        assertFalse(ran.await(300, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `cancelling from the UI thread works too`() {
        val ran = CountDownLatch(1)
        val cancel = SwingMainThreadTimer().schedule(50) { ran.countDown() }
        SwingUtilities.invokeAndWait { cancel() }
        assertFalse(ran.await(300, TimeUnit.MILLISECONDS))
    }
}
