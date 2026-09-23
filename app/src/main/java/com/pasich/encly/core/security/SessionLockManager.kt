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
 * Auto-locks the app when it goes to the background.
 *
 * Registered against [androidx.lifecycle.ProcessLifecycleOwner] in [com.pasich.encly.MyApplication].
 * On [onStop] (the whole process backgrounded) it re-locks the encrypted database if a
 * re-authentication strategy is configured and the session is currently unlocked, then flips
 * [locked] so the UI navigates back to the lock screen the next time the app is foregrounded.
 *
 * Re-locking closes SQLCipher and zeroizes Encly's in-memory DEK copy. The next foreground
 * unlock must unwrap the DEK again through the PIN, auth-bound biometric, or recovery slot.
 */
@Singleton
class SessionLockManager @Inject constructor(private val securityManager: SecurityManager) : DefaultLifecycleObserver {

    private val _locked = MutableStateFlow(false)

    /** Emits true when the app was re-locked in the background and must show the lock screen. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /**
     * Whether the process is in the foreground (between ProcessLifecycleOwner ON_START and
     * ON_STOP). An unlock that finishes after ON_STOP must not leave the vault open.
     */
    @Volatile
    private var foreground = false

    /** Monotonic milliseconds; replaceable in tests. */
    internal var clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI }

    @Volatile
    private var systemPickerRequestedAt = NONE

    @Volatile
    private var suspendedAt = NONE

    /** Schedules [action] on the main thread after a delay; returns a cancel function. */
    internal var timer: (delayMs: Long, action: () -> Unit) -> () -> Unit = { delayMs, action ->
        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        val cancel: () -> Unit = { handler.removeCallbacks(runnable) }
        cancel
    }

    private var cancelPickerTimeout: (() -> Unit)? = null

    /**
     * Announces that the app is about to open the system file picker (Storage Access Framework)
     * for a backup export/import. The picker is another app, so the process goes to the
     * background; the next ON_STOP within [PICKER_LAUNCH_WINDOW_MS] therefore keeps the vault
     * open instead of re-locking it. If the user stays away longer than [PICKER_MAX_AWAY_MS],
     * the vault is re-locked as soon as the app comes back.
     */
    @MainThread
    fun allowSystemPicker() {
        systemPickerRequestedAt = clock()
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        cancelPickerTimeout?.invoke()
        cancelPickerTimeout = null
        val stoppedAt = suspendedAt
        suspendedAt = NONE
        if (stoppedAt != NONE && clock() - stoppedAt > PICKER_MAX_AWAY_MS && isOpenAndLockable()) {
            relock()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        val requestedAt = systemPickerRequestedAt
        systemPickerRequestedAt = NONE
        val now = clock()
        if (requestedAt != NONE && now - requestedAt <= PICKER_LAUNCH_WINDOW_MS) {
            suspendedAt = now
            // Also close the vault if the user leaves the picker for another app and never
            // comes back.
            cancelPickerTimeout = timer(PICKER_MAX_AWAY_MS) {
                if (!foreground && suspendedAt != NONE) {
                    suspendedAt = NONE
                    if (isOpenAndLockable()) relock()
                }
            }
            return
        }
        if (isOpenAndLockable()) {
            relock()
        }
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

    private fun isOpenAndLockable(): Boolean = securityManager.isLockable() && securityManager.isDatabaseUnlocked()

    private fun relock() {
        securityManager.lock()
        _locked.value = true
    }

    private companion object {
        const val TAG = "SessionLockManager"
        const val NONE = Long.MIN_VALUE
        const val NANOS_PER_MILLI = 1_000_000L
        const val PICKER_LAUNCH_WINDOW_MS = 5_000L
        const val PICKER_MAX_AWAY_MS = 5 * 60_000L
    }
}
