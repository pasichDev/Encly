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

    /** An OS where nothing can be detected: only the idle and focus based locks apply. */
    object None : OsLockSignals {
        override fun start(onChange: (locked: Boolean) -> Unit): AutoCloseable = AutoCloseable { }
    }
}

/**
 * [OsLockSignals] for an OS that offers only a question ("is the session locked?"): [probe] is asked every
 * [intervalMillis] on a daemon thread, and a change of its answer is reported. [probe] must never throw; a
 * failure answers `false` (the state is unknown, not locked).
 */
class PollingLockSignals(
    private val intervalMillis: Long,
    private val probe: () -> Boolean,
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
            val now = probe()
            if (now != last) {
                last = now
                onChange(now)
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

    override fun isDeviceLocked(): Boolean = locked

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
        val idle = TimeUnit.NANOSECONDS.toMillis(clock() - lastInputNanos)
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
