package com.pasich.encly.core.security

import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.pasich.encly.core.platform.MainThreadTimer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Whether the open session was closed. ViewModels that hold decrypted content observe it and
 * drop that content at once: after a background re-lock the UI (and its navigation away from
 * protected screens) only runs again once the app is back in front.
 */
public interface VaultLockEvents {
    /** True from the moment the vault is closed until the next unlock is published. */
    public val locked: StateFlow<Boolean>

    /**
     * Registers [flush] to run before the vault is closed by a lock: an editor writes its pending changes in it. A
     * lock waits for the registered flushes for at most [LOCK_FLUSH_TIMEOUT_MS], then closes the vault whether they
     * finished or not. Returns the function that unregisters it. Nothing happens here by default.
     */
    public fun registerPreLockFlush(flush: suspend () -> Unit): () -> Unit = {}
}

/** The longest a lock waits for the pending editor saves before it closes the vault anyway. */
public const val LOCK_FLUSH_TIMEOUT_MS: Long = 1_000L

/** For code that runs without a session manager (tests, previews): never locks. */
public object NeverLocked : VaultLockEvents {
    override val locked: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
}

/**
 * Auto-locks the app when it goes to the background.
 *
 * Registered against [androidx.lifecycle.ProcessLifecycleOwner] in [com.pasich.encly.MyApplication].
 * On [onStop] (the whole process backgrounded) it re-locks the encrypted database if a
 * re-authentication strategy is configured and the session is currently unlocked, then flips
 * [locked] so the UI navigates back to the lock screen the next time the app is foregrounded.
 *
 * Re-locking closes SQLCipher and zeroizes Encly's in-memory DEK copy. The next foreground
 * unlock must unwrap the DEK again through the PIN, auth-bound biometric, or recovery slot.
 *
 * It waits the user's auto-lock delay first ([AutoLockPolicy], 15 s by default, "immediately"
 * possible), and a system file picker (another app) gets its own short grace: see
 * [allowSystemPicker]. A screen that is already off (or a keyguard that is up) at ON_STOP locks
 * at once, and a screen-off later ends both. Both are measured on `elapsedRealtime` (deep sleep
 * counts) when the app comes back. The in-background timer ([MainThreadTimer]) is not trusted to run on its own:
 * a cached app is frozen on Android 14+ and its handler callbacks and context-registered receivers stop, so the
 * Android timer also sets a wake-up alarm for the same delay that calls [onAwayTimeout] on the main thread; cancelling
 * the timer (on return or lock) cancels both.
 */
@OptIn(ExperimentalAtomicApi::class)
@SingleIn(AppScope::class)
@Suppress("TooManyFunctions") // The whole session lifecycle: lifecycle hooks, picker grace, unlock publishing.
@Inject
public class SessionLockManager(
    private val securityManager: SecurityManager,
    private val deviceLock: DeviceLockWatcher = DeviceLockWatcher.None,
    systemClock: LockoutClock = JvmMonotonicClock,
    private val autoLock: AutoLockPolicy = AutoLockPolicy.Immediately,
    private val mainThread: MainThreadTimer = MainThreadTimer.None,
    private val screenshots: ScreenshotAllowance = ScreenshotAllowance(),
) : DefaultLifecycleObserver,
    VaultLockEvents {

    private val _locked = MutableStateFlow(false)
    private val _locking = MutableStateFlow(false)

    private class PreLockFlush(val run: suspend () -> Unit)

    private val preLockFlushes = MutableStateFlow<List<PreLockFlush>>(emptyList())

    /** True from the moment a lock starts waiting for the editors until the vault is closed. */
    private val flushing = AtomicBoolean(false)

    /**
     * Counts the lock requests of a lockable vault (an OS lock, the idle lock, "Lock now", a background stop), whether
     * or not anything was open when they came. An unlock that was running while one arrived must not be published: see
     * [lockRequests] and [onUnlocked].
     */
    private val lockRequestCount = AtomicLong(0L)

    /**
     * True while a lock waits for the editors' saves (at most [LOCK_FLUSH_TIMEOUT_MS]) and [locked] is not set yet:
     * [locked] stays false then so the editors can still read and save their content. The UI must already show
     * nothing and take no commands.
     */
    public val locking: StateFlow<Boolean> = _locking.asStateFlow()

    /** Emits true when the app was re-locked in the background and must show the lock screen. */
    override val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /**
     * Counts published unlocks: each one starts a new session. A screen that was stopped while
     * this changed (the vault was unlocked on another lock screen, such as the My Notes
     * hand-off's) shows a session it never saw, possibly after an erase.
     */
    @Volatile
    public var sessionGeneration: Long = 0L
        private set

    /**
     * Whether the process is in the foreground (between ProcessLifecycleOwner ON_START and
     * ON_STOP). An unlock that finishes after ON_STOP must not leave the vault open.
     */
    @Volatile
    private var foreground = false

    /** Milliseconds since boot, deep sleep included (`elapsedRealtime`); replaceable in tests. */
    internal var clock: () -> Long = systemClock::elapsedRealtime

    @Volatile
    private var systemPickerRequestedAt = NONE

    @Volatile
    private var suspendedAt = NONE

    /** How long the current suspension may last: the picker grace or the auto-lock delay. */
    @Volatile
    private var awayLimitMs = 0L

    /** Set when the screen went off (or the keyguard came up) during a picker grace. */
    @Volatile
    private var lockedWhileAway = false

    /** Where a lock waits for the editors' saves; replaceable in tests. */
    internal var lockScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Schedules [action] on the main thread after a delay; returns a cancel function. */
    internal var timer: (delayMs: Long, action: () -> Unit) -> () -> Unit = mainThread::schedule

    private var cancelPickerTimeout: (() -> Unit)? = null
    private var stopScreenOffWatch: (() -> Unit)? = null

    /**
     * Announces that the app is about to open the system file picker (Storage Access Framework)
     * to choose a backup to import. The picker is another app, so the process goes to the
     * background; the next ON_STOP within [PICKER_LAUNCH_WINDOW_MS] therefore keeps the vault
     * open instead of re-locking it, for at most [PICKER_MAX_AWAY_MS]. Turning the screen off
     * or locking the device ends the grace at once.
     *
     * Used by the backup import, the Markdown export (its plaintext file is written after the picker returns, up to
     * [PICKER_MAX_AWAY_MS]) and the notes import pickers. The grace never survives a screen that is already off or a
     * keyguard that is up at ON_STOP, and the screen turning off later ends it at once.
     */
    @MainThread
    public fun allowSystemPicker() {
        systemPickerRequestedAt = clock()
    }

    /** The picker returned (or never opened): no later ON_STOP gets the grace. */
    @MainThread
    public fun endSystemPicker() {
        systemPickerRequestedAt = NONE
        endSuspension()
    }

    /**
     * Startup found a committed vault that is not open. Starts [locked], so any screen that a
     * restored back stack (process death) or an intent would open is replaced by the lock
     * screen before it can show anything. By default it is also a lock request (an unlock that began before it is not
     * published). A host that shows one vault in several windows passes `countsAsLockRequest = false`: a window that
     * opens is no lock, and must not throw away the unlock another window is checking (a real lock still counts);
     * with the vault open it then does nothing.
     */
    @MainThread
    public fun requireUnlock(countsAsLockRequest: Boolean = true) {
        if (!countsAsLockRequest && securityManager.isDatabaseUnlocked()) return
        if (countsAsLockRequest) lockRequestCount.addAndFetch(1L)
        screenshots.revoke()
        _locked.value = true
    }

    /**
     * Counts published erases of the whole vault ("Erase all data"): every window that shows this vault drops what it
     * shows and starts over (see [onVaultErased]). Starts at 0 for the process.
     */
    private val _erasedCount = MutableStateFlow(0L)
    public val erasedCount: StateFlow<Long> = _erasedCount.asStateFlow()

    /**
     * The whole vault was erased (see [SecurityManager.wipeAndReset]): any unlock still running must not be published
     * (the keys it opens are gone), and every window learns of it through [erasedCount] and starts over with
     * onboarding, so none keeps showing the erased vault's notes. [locked] is cleared: nothing is locked, there is no
     * vault, and a new one made in onboarding must not start behind a lock screen. Call on the main thread after a
     * successful erase, from the window that made it.
     */
    @MainThread
    public fun onVaultErased() {
        lockRequestCount.addAndFetch(1L)
        screenshots.revoke()
        securityManager.abandonKeyStorePrompt()
        // A lock that raced the erase (the screen went off while it ran) has nothing left to lock: left set, it would
        // keep the shield over the onboarding of the new vault until the process ends.
        _locked.value = false
        _erasedCount.update { it + 1L }
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        val stoppedAt = suspendedAt
        val lockedAway = lockedWhileAway
        endSuspension()
        if (stoppedAt == NONE) return
        if (graceBroken(stoppedAt, lockedAway) && isOpenAndLockable()) relock()
    }

    @Suppress("ReturnCount") // The guards of a stop, each ending it.
    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        val requestedAt = systemPickerRequestedAt
        systemPickerRequestedAt = NONE
        // ON_STOP comes about 700 ms after the activity stopped, so a screen-off broadcast sent meanwhile (or a
        // keyguard that came up) is already gone: neither the delay nor the picker grace may keep the vault open.
        // The watchers of suspend() only hear a screen-off that comes later.
        if (!deviceLock.isScreenOn() || deviceLock.isDeviceLocked()) {
            // Counted even with nothing open: an unlock still checking the PIN must not be published after the
            // screen comes back on, which an insecure keyguard would not report as locked.
            lockNow()
            return
        }
        val now = clock()
        if (requestedAt != NONE && now - requestedAt in 0..PICKER_LAUNCH_WINDOW_MS) {
            suspend(now, PICKER_MAX_AWAY_MS)
            return
        }
        if (!isOpenAndLockable()) return
        val delay = autoLock.delayMillis
        if (delay > 0) suspend(now, delay) else relock()
    }

    /**
     * Keeps the vault open for up to [limitMs] while the app is away (the picker grace, or the
     * auto-lock delay). The screen going off closes it right away, even if the app never comes
     * back; the timer closes it when the time runs out.
     */
    private fun suspend(now: Long, limitMs: Long) {
        suspendedAt = now
        awayLimitMs = limitMs
        lockedWhileAway = false
        stopScreenOffWatch = deviceLock.watchScreenOff { onAwayTimeout(screenOff = true) }
        cancelPickerTimeout = timer(limitMs) { onAwayTimeout(screenOff = false) }
    }

    /** The grace no longer holds: too long away, the screen went off, or the keyguard is up. */
    private fun graceBroken(stoppedAt: Long, lockedAway: Boolean): Boolean =
        clock() - stoppedAt >= awayLimitMs || lockedAway || deviceLock.isDeviceLocked()

    private fun onAwayTimeout(screenOff: Boolean) {
        if (foreground || suspendedAt == NONE) return
        if (screenOff) lockedWhileAway = true
        endSuspension()
        if (isOpenAndLockable()) relock()
    }

    private fun endSuspension() {
        suspendedAt = NONE
        lockedWhileAway = false
        cancelPickerTimeout?.invoke()
        cancelPickerTimeout = null
        stopScreenOffWatch?.invoke()
        stopScreenOffWatch = null
    }

    /**
     * The number of lock requests so far. A caller that starts an unlock reads it first and passes it to [onUnlocked]
     * when the unlock is done: a lock that came in between (an OS lock or the idle lock while the PIN was being
     * checked, when there was nothing open yet to close) must not be lost to the unlock that finishes after it.
     */
    public fun lockRequests(): Long = lockRequestCount.load()

    /**
     * Whether the device reports itself locked right now. An unlock that [onUnlocked] refuses for this reason can say
     * so, instead of leaving the lock screen as it was.
     */
    public fun isDeviceLocked(): Boolean = deviceLock.isDeviceLocked()

    /**
     * Publishes a completed unlock (PIN, biometric, recovery seed, first-run setup or v1
     * upgrade). Call on the main thread, where ON_START/ON_STOP are delivered too, right
     * after the vault was opened. [lockRequestsAtStart] is [lockRequests] as it was when the unlock began.
     *
     * PBKDF2 and the SQLCipher open run off the main thread and can finish after the app was
     * sent to the background, when [onStop] already ran and found nothing to close, or after a lock request that
     * found nothing open. In that case the vault is closed again at once, [locked] stays true and this returns
     * false: the caller must not navigate into protected content.
     */
    @MainThread
    public fun onUnlocked(lockRequestsAtStart: Long = lockRequests()): Boolean {
        if (!foreground || lockRequestCount.load() != lockRequestsAtStart || deviceLock.isDeviceLocked()) {
            relock()
            return false
        }
        // A new session starts protected: an allowance that slipped in after the lock that ended the last one
        // (it is revoked when a lock starts, and the vault can close up to a second later) does not reach this one.
        screenshots.revoke()
        sessionGeneration++
        _locked.value = false
        return true
    }

    /**
     * An unlock finished for a screen that no longer exists (its ViewModel was cleared while
     * the key was being checked). Nobody will navigate into the vault, so it is closed again.
     */
    @MainThread
    public fun onUnlockAbandoned() {
        if (isOpenAndLockable()) relock()
    }

    /**
     * "Lock now" on the notes screen: closes the open vault exactly as backgrounding does, so the
     * lock screen follows. Nothing happens before onboarding is committed or with no vault open.
     */
    @MainThread
    public fun lockNow() {
        // Counted even with nothing open: an unlock that is running must not publish after it.
        if (securityManager.isLockable()) {
            lockRequestCount.addAndFetch(1L)
            // A keyring prompt that unlock waits on goes away with the lock request.
            securityManager.abandonKeyStorePrompt()
        }
        if (isOpenAndLockable()) relock()
    }

    private fun isOpenAndLockable(): Boolean = securityManager.isLockable() && securityManager.isDatabaseUnlocked()

    override fun registerPreLockFlush(flush: suspend () -> Unit): () -> Unit {
        val entry = PreLockFlush(flush)
        preLockFlushes.update { it + entry }
        return { preLockFlushes.update { it - entry } }
    }

    private fun relock() {
        lockRequestCount.addAndFetch(1L)
        securityManager.abandonKeyStorePrompt()
        // The screenshot allowance ends with every lock, before anything else can show.
        screenshots.revoke()
        val flushes = preLockFlushes.value
        if (flushes.isEmpty()) {
            closeVault()
            return
        }
        // A lock already waiting for the editors covers this one.
        if (!flushing.compareAndSet(false, true)) return
        _locking.value = true
        lockScope.launch {
            try {
                // The saves run in jobs of their own, so a save stuck in the database cannot hold the lock past
                // the timeout: only the wait for them is cancelled.
                val saves = flushes.map { flush ->
                    launch { runCatching { flush.run() }.onFailure { if (it is CancellationException) throw it } }
                }
                withTimeoutOrNull(LOCK_FLUSH_TIMEOUT_MS) { saves.joinAll() }
            } finally {
                // Reset whatever closing the vault does: a stuck flag would swallow every later lock.
                try {
                    closeVault()
                } finally {
                    try {
                        _locking.value = false
                    } finally {
                        flushing.store(false)
                    }
                }
            }
        }
    }

    private fun closeVault() {
        try {
            securityManager.lock()
        } finally {
            // Published even if closing the vault failed: the UI must not go on showing an open session.
            _locked.value = true
        }
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
        const val PICKER_LAUNCH_WINDOW_MS = 5_000L
        const val PICKER_MAX_AWAY_MS = 60_000L
    }
}
