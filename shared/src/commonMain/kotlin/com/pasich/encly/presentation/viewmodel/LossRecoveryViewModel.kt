package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.pasich.encly.core.security.SecurityManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey

/**
 * Full-reset path for unrecoverable local vault corruption/loss.
 */
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class LossRecoveryViewModel(private val securityManager: SecurityManager) : ViewModel() {

    fun wipeAllData() {
        securityManager.wipeAndReset()
    }
}
