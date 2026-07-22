package com.pasich.encly.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.pasich.encly.core.AppLogger
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
 * Re-locking only closes the database; the DB key is re-derived from the stored seed hash on the
 * next unlock, so the user just re-authenticates (PIN/biometric/seed) — no data is lost.
 */
@Singleton
class SessionLockManager @Inject constructor(
    private val securityManager: SecurityManager,
) : DefaultLifecycleObserver {

    private val _locked = MutableStateFlow(false)

    /** Emits true when the app was re-locked in the background and must show the lock screen. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    override fun onStop(owner: LifecycleOwner) {
        if (securityManager.isLockable() && securityManager.isDatabaseUnlocked()) {
            securityManager.lock()
            _locked.value = true
            AppLogger.d(TAG, "App backgrounded — session re-locked")
        }
    }

    /** Clears the lock flag after a successful re-authentication on the lock screen. */
    fun onUnlocked() {
        _locked.value = false
    }

    private companion object {
        const val TAG = "SessionLockManager"
    }
}
