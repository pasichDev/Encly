package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.VaultLockEvents
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Runs [clear] as soon as the session locks. After a background re-lock the UI only resumes
 * (and navigates away from this screen, which clears the ViewModel) once the app is in front
 * again; until then decrypted content held here would stay reachable in the heap. [clear]
 * drops it right away instead.
 */
internal fun ViewModel.clearOnLock(events: VaultLockEvents, clear: () -> Unit) {
    viewModelScope.launch {
        events.locked.first { it }
        clear()
    }
}
