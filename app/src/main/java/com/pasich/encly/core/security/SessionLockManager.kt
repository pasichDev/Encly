package com.pasich.encly.core.security

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the open session was closed. ViewModels that hold decrypted content observe it and
 * drop that content at once: after a background re-lock the UI (and its navigation away from
 * protected screens) only runs again once the app is back in front.
 */
interface VaultLockEvents {
    /** True from the moment the vault is closed until the next unlock is published. */
    val locked: StateFlow<Boolean>
}

/** For code that runs without a session manager (tests, previews): never locks. */
object NeverLocked : VaultLockEvents {
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
 * The one exception is the system file picker of a backup import (another app, so the process
 * goes to the background): see [allowSystemPicker]. Its grace is short, measured on
 * `elapsedRealtime` (deep sleep counts), ends when the screen turns off, and is re-checked
 * the moment the app comes back.
 */
@Singleton
@Suppress("TooManyFunctions") // The whole session lifecycle: lifecycle hooks, picker grace, unlock publishing.
class SessionLockManager @Inject constructor(
    private val securityManager: SecurityManager,
    private val deviceLock: DeviceLockWatcher = DeviceLockWatcher.None,
    systemClock: LockoutClock = JvmMonotonicClock,
) : DefaultLifecycleObserver,
    VaultLockEvents {

    private val _locked = MutableStateFlow(false)

    /** Emits true when the app was re-locked in the background and must show the lock screen. */
    override val locked: StateFlow<Boolean> = _locked.asStateFlow()

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

    /** Set when the screen went off (or the keyguard came up) during a picker grace. */
    @Volatile
    private var lockedWhileAway = false

    /** Schedules [action] on the main thread after a delay; returns a cancel function. */
    internal var timer: (delayMs: Long, action: () -> Unit) -> () -> Unit = { delayMs, action ->
        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        val cancel: () -> Unit = { handler.removeCallbacks(runnable) }
        cancel
    }

    private var cancelPickerTimeout: (() -> Unit)? = null
    private var stopScreenOffWatch: (() -> Unit)? = null

    /**
     * Announces that the app is about to open the system file picker (Storage Access Framework)
     * to choose a backup to import. The picker is another app, so the process goes to the
     * background; the next ON_STOP within [PICKER_LAUNCH_WINDOW_MS] therefore keeps the vault
     * open instead of re-locking it, for at most [PICKER_MAX_AWAY_MS]. Turning the screen off
     * or locking the device ends the grace at once.
     *
     * Export does not need this: its file is sealed before the picker opens.
     */
    @MainThread
    fun allowSystemPicker() {
        systemPickerRequestedAt = clock()
    }

    /** The picker returned (or never opened): no later ON_STOP gets the grace. */
    @MainThread
    fun endSystemPicker() {
        systemPickerRequestedAt = NONE
        endSuspension()
    }

    /**
     * Startup found a committed vault that is not open. Starts [locked], so any screen that a
     * restored back stack (process death) or an intent would open is replaced by the lock
     * screen before it can show anything.
     */
    @MainThread
    fun requireUnlock() {
        _locked.value = true
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        val stoppedAt = suspendedAt
        val lockedAway = lockedWhileAway
        endSuspension()
        if (stoppedAt == NONE) return
        if (graceBroken(stoppedAt, lockedAway) && isOpenAndLockable()) relock()
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        val requestedAt = systemPickerRequestedAt
        systemPickerRequestedAt = NONE
        val now = clock()
        if (requestedAt != NONE && now - requestedAt in 0..PICKER_LAUNCH_WINDOW_MS) {
            suspendedAt = now
            lockedWhileAway = false
            // The screen going off during the picker closes the vault right away, even if the
            // app never comes back; the timeout covers leaving the picker for another app.
            stopScreenOffWatch = deviceLock.watchScreenOff { onAwayTimeout(screenOff = true) }
            cancelPickerTimeout = timer(PICKER_MAX_AWAY_MS) { onAwayTimeout(screenOff = false) }
            return
        }
        if (isOpenAndLockable()) {
            relock()
        }
    }

    /** The picker grace no longer holds: too long away, the screen went off, or the keyguard is up. */
    private fun graceBroken(stoppedAt: Long, lockedAway: Boolean): Boolean =
        clock() - stoppedAt > PICKER_MAX_AWAY_MS || lockedAway || deviceLock.isDeviceLocked()

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
     * Publishes a completed unlock (PIN, biometric, recovery seed, first-run setup or v1
     * upgrade). Call on the main thread, where ON_START/ON_STOP are delivered too, right
     * after the vault was opened.
     *
     * PBKDF2 and the SQLCipher open run off the main thread and can finish after the app was
     * sent to the background, when [onStop] already ran and found nothing to close. In that
     * case the vault is closed again at once, [locked] stays true and this returns false:
     * the caller must not navigate into protected content.
     */
    @MainThread
    fun onUnlocked(): Boolean {
        if (!foreground) {
            relock()
            return false
        }
        _locked.value = false
        return true
    }

    /**
     * An unlock finished for a screen that no longer exists (its ViewModel was cleared while
     * the key was being checked). Nobody will navigate into the vault, so it is closed again.
     */
    @MainThread
    fun onUnlockAbandoned() {
        if (isOpenAndLockable()) relock()
    }

    /**
     * "Lock now" on the notes screen: closes the open vault exactly as backgrounding does, so the
     * lock screen follows. Nothing happens before onboarding is committed or with no vault open.
     */
    @MainThread
    fun lockNow() {
        if (isOpenAndLockable()) relock()
    }

    private fun isOpenAndLockable(): Boolean = securityManager.isLockable() && securityManager.isDatabaseUnlocked()

    private fun relock() {
        securityManager.lock()
        _locked.value = true
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
        const val PICKER_LAUNCH_WINDOW_MS = 5_000L
        const val PICKER_MAX_AWAY_MS = 60_000L
    }
}
