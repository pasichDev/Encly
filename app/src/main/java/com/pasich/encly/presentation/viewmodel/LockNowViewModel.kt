package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.pasich.encly.core.security.SessionLockManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey

/** The "Lock now" action of the notes screen. */
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class LockNowViewModel(private val sessionLockManager: SessionLockManager) : ViewModel() {

    /** Closes the vault; the app then shows the lock screen. */
    fun lockNow() = sessionLockManager.lockNow()
}
