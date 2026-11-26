package com.pasich.encly.desktop.security

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.pasich.encly.core.platform.MainThreadTimer
import com.pasich.encly.core.security.AutoLockPolicy
import com.pasich.encly.core.security.DeviceLockWatcher
import org.freedesktop.dbus.errors.ServiceUnknown
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
     * A source that can be asked ([asksTheOs]) answers null for "could not be read this time" (a timeout, an error);
     * that is unknown, never "unlocked" (see [DesktopDeviceLockWatcher.isDeviceLocked]).
     */
    fun liveLocked(): Boolean? = null

    /**
     * True when the OS can be asked ([liveLocked] answers null only when a read failed) and so the last reported
     * change is not the state: a locker that never says it ended (Linux: swaylock, i3lock through xss-lock) leaves
     * the last report at "locked" for good. Every report of a lock is then a lock to act on, not only the first after
     * an unlocked one.
     */
    val asksTheOs: Boolean get() = false

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
 * What Linux can be asked about the session being locked, each source `null` when it is not there: logind's
 * `LockedHint` of the session (kept right by most lockers: swaylock, hyprlock, i3lock through xss-lock and the
 * desktops' own), and whether a desktop screen saver is active. [isLocked]: locked when any source says so; a source
 * that throws answers "not locked" there. [state] keeps what [isLocked] cannot say: a source that failed, with no other
 * saying "locked", leaves the state unknown (null), which the caller must not read as "unlocked".
 *
 * A source that is not there is not a failure, and the bus cannot be asked about it by reading it: dbus-java's
 * `getDBusOwnerName` (`GetNameOwner`) answers a name nobody owns with the D-Bus error `NameHasNoOwner`, which it
 * throws as a plain `DBusExecutionException` with no trace of the error's name left (`getType()` is the class's), so
 * no handler can tell it from a real failure. Most systems have only one of the four screen savers and some none
 * (sway, i3, Hyprland), so reading that as "unknown" would leave the state unknown for good. A source is therefore
 * asked whether its name has an owner first, with `NameHasOwner`, which answers (see [screenSaverSource]); only a
 * source that is there and then fails (a timeout, an error of its own) is unknown. A service that stops while it is
 * read ([ServiceUnknown]) is gone too.
 */
class LinuxLiveLock(private val lockedHint: () -> Boolean?, private val screenSaversActive: List<() -> Boolean?>) {
    /** What a source says: false when it is not there, null when reading it failed. */
    @Suppress("TooGenericExceptionCaught")
    private fun read(source: () -> Boolean?): Boolean? = try {
        source() ?: false
    } catch (_: ServiceUnknown) {
        false
    } catch (_: Exception) {
        null
    }

    private fun says(source: () -> Boolean?): Boolean = read(source) == true

    /** Whether the session is locked now. */
    fun isLocked(): Boolean = says(lockedHint) || screenSaversActive.any(::says)

    /** True when any source says locked; null when none does and at least one could not be read; false otherwise. */
    fun state(): Boolean? {
        val answers = (listOf(lockedHint) + screenSaversActive).map(::read)
        return when {
            true in answers -> true
            null in answers -> null
            else -> false
        }
    }
}

/**
 * A screen saver as a source of [LinuxLiveLock]: [hasOwner] (`NameHasOwner` of its bus name, a question that always
 * has an answer) first, and [active] (`GetActive`) only when someone owns the name: asking a name nobody owns raises
 * an error the library does not name, and could start the service. Null (not there) when nobody owns it.
 */
fun screenSaverSource(hasOwner: () -> Boolean, active: () -> Boolean): () -> Boolean? = {
    if (hasOwner()) active() else null
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
    /** Milliseconds on a clock that only runs forward; read only to bound a lock nothing can confirm. */
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val dispatch: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
) : DeviceLockWatcher,
    AutoCloseable {
    @Volatile
    private var locked = false

    // The OS could not be asked while the last report said "locked": how many questions in a row, and since when.
    private var unconfirmedReads = 0
    private var unconfirmedSince = 0L

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
        // A new report is new evidence: what was stale before it is not counted against it.
        resetUnconfirmed()
        // A source that can be asked is not trusted to say when a lock ended, so its second report of a lock is not
        // swallowed as a repeat of one that is long over: that would leave the vault open behind the lock screen.
        if (nowLocked && (!wasLocked || signals.asksTheOs)) {
            dispatch {
                lockListeners.forEach { it() }
                screenOffListeners.forEach { it() }
            }
        }
    }

    // Where the OS can be asked, it is: the last signal can be stale for good (a locker that never says it ended). When
    // it cannot answer this time (a timeout, an error: null) the state is unknown and the last report stands, so a
    // slow bus never turns a known "locked" into "unlocked". Unknown is not forced to "locked" here: a read that fails
    // every time would then refuse every unlock for good.
    //
    // But a "locked" that only a signal ever set, with the OS never able to confirm it, would refuse every unlock for
    // good (a bus that answers nothing, a locker that never reports its end). It is dropped once [STALE_READS]
    // questions in a row went unanswered over at least [STALE_MILLIS]. That cannot leave the vault open behind a real
    // lock screen: the vault is closed by the lock signal itself, not by this flag, which only decides whether an
    // unlock that was typed into Encly's own window may complete; behind a real lock screen nothing can be typed
    // there. A later report of a lock starts the count again.
    override fun isDeviceLocked(): Boolean {
        val live = signals.liveLocked()
        if (live != null || !locked) {
            resetUnconfirmed()
            return live ?: false
        }
        val now = clock()
        synchronized(this) {
            if (unconfirmedReads == 0) unconfirmedSince = now
            unconfirmedReads++
            if (unconfirmedReads >= STALE_READS && now - unconfirmedSince >= STALE_MILLIS) {
                locked = false
                resetUnconfirmed()
            }
        }
        return locked
    }

    @Synchronized
    private fun resetUnconfirmed() {
        unconfirmedReads = 0
        unconfirmedSince = 0L
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        /** Unanswered questions, in a row, after which a "locked" nothing confirms is dropped... */
        const val STALE_READS = 3

        /** ...if they also span at least this long (a user's retry, not a burst of checks of one attempt). */
        const val STALE_MILLIS = 30_000L
    }

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
