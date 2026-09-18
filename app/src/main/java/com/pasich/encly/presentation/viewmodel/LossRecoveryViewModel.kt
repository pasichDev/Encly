package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.pasich.encly.core.security.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Full-reset path for unrecoverable local vault corruption/loss.
 */
@HiltViewModel
class LossRecoveryViewModel @Inject constructor(
    private val securityManager: SecurityManager
) : ViewModel() {

    fun wipeAllData() {
        securityManager.wipeAndReset()
    }
}
