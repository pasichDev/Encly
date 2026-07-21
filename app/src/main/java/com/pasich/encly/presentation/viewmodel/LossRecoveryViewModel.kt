package com.pasich.encly.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.utils.ReminderStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Backs the unrecoverable-loss recovery screen. The only available action is a full
 * wipe: there is no backdoor recovery — losing the seed means the data is gone.
 */
@HiltViewModel
class LossRecoveryViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val securityManager: SecurityManager
) : ViewModel() {

    /** Wipes all data and security state and resets the app to onboarding. */
    fun wipeAllData() {
        securityManager.wipeAndReset()
        ReminderStore.clear(context)
    }
}
