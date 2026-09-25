package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.pasich.encly.core.security.SessionLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** The "Lock now" action of the notes screen. */
@HiltViewModel
class LockNowViewModel @Inject constructor(private val sessionLockManager: SessionLockManager) : ViewModel() {

    /** Closes the vault; the app then shows the lock screen. */
    fun lockNow() = sessionLockManager.lockNow()
}
