package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The retry of a start that could not read the vault's state file ([InitialStatus.STORAGE_UNREADABLE]): nothing
 * is wiped, the start is simply asked again.
 */
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class StorageUnreadableViewModel(
    private val securityManager: SecurityManager,
    private val sessionLockManager: SessionLockManager,
) : ViewModel() {
    private val _stillUnreadable = MutableStateFlow(false)

    /** True once a retry found the file still unreadable. */
    val stillUnreadable: StateFlow<Boolean> = _stillUnreadable.asStateFlow()

    /** Resolves the start again and reports where it leads; a committed vault that is not open starts locked. */
    fun retry(onStatus: (InitialStatus) -> Unit) {
        viewModelScope.launch {
            val status = withContext(Dispatchers.Default) { securityManager.resolveInitialStatus() }
            _stillUnreadable.value = status == InitialStatus.STORAGE_UNREADABLE
            if (status == InitialStatus.AUTH) sessionLockManager.requireUnlock()
            onStatus(status)
        }
    }
}
