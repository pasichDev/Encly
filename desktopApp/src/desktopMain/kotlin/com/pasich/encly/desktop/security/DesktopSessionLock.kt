package com.pasich.encly.desktop.security

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.pasich.encly.core.platform.MainThreadTimer
import com.pasich.encly.core.security.AutoLockPolicy
import com.pasich.encly.core.security.DeviceLockWatcher
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/**
 * What the OS reports about the session being locked (a lock screen, a fast user switch, a suspend).
 * [start] reports every change of the lock state through [onChange] (any thread) until the result is closed.
 */
fun interface OsLockSignals {
    fun start(onChange: (locked: Boolean) -> Unit): AutoCloseable

    /**
     * Whether the session is locked right now, read from the OS, or null when this OS only reports changes through
     * [start] (the last reported change is then the state). A source that reports changes some lockers never send
     * (a lock that starts and is never told it ended) must answer here, or its last change stays the state for good.
     */
    fun liveLocked(): Boolean? = null

    /** An OS where nothing can be detected: only the idle and focus based locks apply. */
    object None : OsLockSignals {
        override fun start(onChange: (locked: Boolean) -> Unit): AutoCloseable = AutoCloseable { }
    }
}

/**
 * Whether the machine slept since the last time it was asked. Two OS clocks tell: one that keeps counting while
 * the machine sleeps and one that stops, so the gap between them only grows by the time spent asleep (a stalled
 * process or a changed wall clock moves neither). [clocks] gives both in milliseconds, or null when they cannot be
 * read (then nothing is detected). The first question only takes the starting gap.
 */
class SleepDetector(private val clocks: () -> Clocks?, private val minSleepMillis: Long = MIN_SLEEP_MILLIS) {
    /** [counting]: milliseconds since boot, sleep included; [awake]: the same without the time asleep. */
    class Clocks(val counting: Long, val awake: Long)

    private var gap: Long? = null

    /** True when the machine slept for at least [minSleepMillis] since the previous call. */
    @Synchronized
    fun sleptSinceLastCheck(): Boolean {
        val now = clocks()?.let { it.counting - it.awake } ?: return false
        val before = gap
        gap = now
        return before != null && now - before >= minSleepMillis
    }

    companion object {
        /** Shorter naps are not told apart from clock rounding. */
        const val MIN_SLEEP_MILLIS: Long = 2_000L
    }
}

/**
 * What Linux can be asked about the session being locked, each source `null` when it cannot be read: logind's
 * `LockedHint` of the session (kept right by most lockers: swaylock, hyprlock, i3lock through xss-lock and the
 * desktops' own), and whether a desktop screen saver is active. Locked when any source says so. A source that throws
 * answers "not locked": an unreadable state must not keep the vault from opening.
 */
class LinuxLiveLock(private val lockedHint: () -> Boolean?, private val screenSaversActive: List<() -> Boolean?>) {
    @Suppress("TooGenericExceptionCaught")
    private fun says(source: () -> Boolean?): Boolean = try {
        source() == true
    } catch (_: Exception) {
        false
    }

    /** Whether the session is locked now. */
    fun isLocked(): Boolean = says(lockedHint) || screenSaversActive.any(::says)
}

/**
 * [OsLockSignals] for an OS that offers only a question ("is the session locked?"): [probe] is asked every
 * [intervalMillis] on a daemon thread, and a change of its answer is reported. [probe] must never throw; a
 * failure answers `false` (the state is unknown, not locked).
 *
 * Sleep is reported as a lock that ends at once: a machine that slept without locking the session (a Mac that
 * does not ask for the password after sleep, a PC without the wake-up sign-in) leaves the vault open for whoever
 * opens the lid, so [slept] says when it did and the vault locks. It is seen when the machine wakes, at the
 * next poll.
 */
class PollingLockSignals(
    private val intervalMillis: Long,
    private val probe: () -> Boolean,
    private val slept: () -> Boolean = { false },
    private val executor: () -> ScheduledExecutorService = {
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "encly-screen-lock-poll").apply { isDaemon = true }
        }
    },
) : OsLockSignals {
    override fun start(onChange: (locked: Boolean) -> Unit): AutoCloseable {
        val scheduler = executor()
        var last = false
        val poll = Runnable {
            val sleptSincePoll = slept()
            val now = probe()
            if (now != last) {
                last = now
                onChange(now)
            } else if (sleptSincePoll && !now) {
                // Slept with the session never locked: a lock that is over as soon as it is reported.
                onChange(true)
                onChange(false)
            }
        }
        scheduler.scheduleWithFixedDelay(poll, 0L, intervalMillis, TimeUnit.MILLISECONDS)
        return AutoCloseable { scheduler.shutdownNow() }
    }
}

/**
 * [DeviceLockWatcher] on the desktop: the OS says when the session is locked or the machine goes to sleep.
 *
 * It does two jobs. [DeviceLockWatcher.isDeviceLocked] and [DeviceLockWatcher.watchScreenOff] serve the
 * session lock while the app is in the background, as on Android. [onLocked] serves the app being in front:
 * the desktop's lock screen can come up while Encly has the focus, which Android's screen-off broadcast also
 * covers there, so the host subscribes once for the whole run and locks the vault.
 *
 * Callbacks are delivered through [dispatch] (the AWT event thread), whatever thread the OS reports on.
 */
class DesktopDeviceLockWatcher(
    private val signals: OsLockSignals,
    private val dispatch: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
) : DeviceLockWatcher,
    AutoCloseable {
    @Volatile
    private var locked = false

    private val screenOffListeners = CopyOnWriteArrayList<() -> Unit>()
    private val lockListeners = CopyOnWriteArrayList<() -> Unit>()
    private var subscription: AutoCloseable? = null

    /** Starts listening to the OS. Safe to call once; later calls do nothing. */
    @Synchronized
    fun start() {
        if (subscription == null) subscription = signals.start(::onSignal)
    }

    private fun onSignal(nowLocked: Boolean) {
        val wasLocked = locked
        locked = nowLocked
        if (nowLocked && !wasLocked) {
            dispatch {
                lockListeners.forEach { it() }
                screenOffListeners.forEach { it() }
            }
        }
    }

    // Where the OS can be asked, it is: the last signal can be stale for good (a locker that never says it ended).
    override fun isDeviceLocked(): Boolean = signals.liveLocked() ?: locked

    override fun watchScreenOff(onScreenOff: () -> Unit): () -> Unit {
        screenOffListeners += onScreenOff
        val stop: () -> Unit = { screenOffListeners -= onScreenOff }
        return stop
    }

    /** Calls [action] every time the session becomes locked, until the returned function is called. */
    fun onLocked(action: () -> Unit): () -> Unit {
        lockListeners += action
        val stop: () -> Unit = { lockListeners -= action }
        return stop
    }

    @Synchronized
    override fun close() {
        subscription?.close()
        subscription = null
    }
}

/**
 * The foreground state of the app for [com.pasich.encly.core.security.SessionLockManager], which is a
 * lifecycle observer: Android feeds it from the process lifecycle, the desktop from the window.
 *
 * The app is in the foreground while its window is active and not minimised. Leaving (another application
 * in front, minimised) is ON_STOP, which the lock manager answers by the user's auto-lock delay; coming back
 * is ON_START. The same owner is given to the UI as its lifecycle, so the navigation guards see the same
 * stops and starts as the lock manager.
 *
 * The app's own modal dialogs (a file chooser) take the focus from the window without the user leaving the
 * app: while one is open ([beginOwnDialog], [endOwnDialog]) the window counts as active.
 */
class DesktopAppLifecycle : LifecycleOwner {
    private val registry = LifecycleRegistry.createUnsafe(this)
    private var active = true
    private var minimised = false
    private var dialogs = 0

    override val lifecycle: Lifecycle get() = registry

    init {
        // The app starts in front: the first unlock happens after the window is up.
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /** True while the app counts as in front. */
    val inForeground: Boolean get() = registry.currentState.isAtLeast(Lifecycle.State.STARTED)

    fun windowActivated() = update { active = true }

    fun windowDeactivated() = update { active = false }

    fun windowMinimised() = update { minimised = true }

    fun windowRestored() = update { minimised = false }

    /** An own modal dialog is about to take the focus. */
    fun beginOwnDialog() = update { dialogs++ }

    /** The dialog closed and the focus goes back to the window (the activation event may come after). */
    fun endOwnDialog() = update {
        if (dialogs > 0) dialogs--
        if (dialogs == 0) active = true
    }

    /** The app is closing: no more events (the lock manager stops waiting for them). */
    fun destroy() {
        if (inForeground) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    private fun update(change: () -> Unit) {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        change()
        val wantForeground = !minimised && (active || dialogs > 0)
        when {
            wantForeground && !inForeground -> {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            !wantForeground && inForeground -> {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
        }
    }
}

/**
 * The OS uptime in nanoseconds for [IdleLock.touch], which runs on every input event and must not ask the OS each time
 * (a file read on Linux, a native call elsewhere): [uptimeMillis] is read at most once per [refreshNanos] of the
 * process's own monotonic clock, and in between the uptime moves on with that clock. A sleep makes this read low until
 * the next refresh, which can only make the idle time look longer, never shorter: the vault locks sooner, not later.
 */
class CoarseUptime(
    private val uptimeMillis: () -> Long,
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val refreshNanos: Long = TimeUnit.SECONDS.toNanos(1),
) {
    private class Reading(val uptimeNanos: Long, val atMonotonicNanos: Long)

    @Volatile
    private var reading = take()

    /** The uptime now, nanoseconds. Any thread. */
    fun nanos(): Long {
        val now = monotonicNanos()
        val current = reading
        if (now - current.atMonotonicNanos < refreshNanos) return current.uptimeNanos + (now - current.atMonotonicNanos)
        return take(now).also { reading = it }.uptimeNanos
    }

    private fun take(now: Long = monotonicNanos()) = Reading(TimeUnit.MILLISECONDS.toNanos(uptimeMillis()), now)
}

/**
 * Locks the vault after the user has not touched the keyboard or the mouse for a while although the window is
 * in front (the auto-lock delay covers the app being left, not an unattended open window). The limit is the
 * user's auto-lock delay, but never shorter than [MIN_IDLE_MILLIS]: the default delay of 15 seconds is meant
 * for leaving the app, not for pausing to read.
 *
 * The host calls [touch] on every input event; the check itself runs on [timer], rescheduled for exactly the
 * time that is left, so a busy mouse costs a field write and no timers.
 */
class IdleLock(
    private val timer: MainThreadTimer,
    private val policy: AutoLockPolicy,
    private val lockNow: () -> Unit,
    private val clock: () -> Long = System::nanoTime,
    /** The clock the check reads; the exact one, where [clock] (read on every input event) may be a cheap one. */
    private val checkClock: () -> Long = clock,
) {
    @Volatile
    private var lastInputNanos = clock()
    private var cancel: (() -> Unit)? = null

    /** The user did something. Cheap; any thread. */
    fun touch() {
        lastInputNanos = clock()
    }

    /** Starts watching. */
    fun start() {
        touch()
        schedule(limitMillis())
    }

    /** Stops watching. */
    fun stop() {
        cancel?.invoke()
        cancel = null
    }

    private fun limitMillis(): Long = maxOf(policy.delayMillis, MIN_IDLE_MILLIS)

    private fun schedule(delayMillis: Long) {
        cancel = timer.schedule(delayMillis, ::check)
    }

    private fun check() {
        val limit = limitMillis()
        val idle = TimeUnit.NANOSECONDS.toMillis(checkClock() - lastInputNanos)
        if (idle >= limit) {
            lockNow()
            schedule(limit)
        } else {
            schedule(limit - idle)
        }
    }

    companion object {
        /** The shortest idle time before the vault locks while the window is in front. */
        const val MIN_IDLE_MILLIS: Long = 5 * 60_000L
    }
}
