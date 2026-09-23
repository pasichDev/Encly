package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.PendingRestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AuthSetupViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val sessionLockManager: SessionLockManager,
    private val pendingRestore: PendingRestore,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun biometricAvailable(): Boolean = securityManager.biometricAvailable()

    fun setPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val ok = withContext(Dispatchers.Default) { securityManager.configurePin(pin) }
            _busy.value = false
            onResult(ok)
        }
    }

    fun enableBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        securityManager.enrollBiometric(activity, onResult)
    }

    /**
     * [FinishResult.ok] is `true` only when the vault is open and the app is still in the
     * foreground; [FinishResult.backgrounded] tells the caller setup succeeded but the session
     * was re-locked, so it should neither navigate nor show an error.
     *
     * A backup chosen with "Restore from backup" during onboarding is imported here, into the
     * vault setup just opened and before onboarding is committed. If that import fails,
     * nothing is committed and the vault is closed again; [FinishResult.restoreFailed] asks the
     * user to retry (the backup stays staged) or to [skipRestore]. A process killed meanwhile
     * restarts onboarding instead of opening an empty vault.
     */
    fun finishSetup(onResult: (FinishResult) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val outcome = withContext(Dispatchers.IO) { openRestoreAndCommit() }
            val committed = outcome == SetupOutcome.COMMITTED
            val published = committed && sessionLockManager.onUnlocked()
            _busy.value = false
            onResult(
                FinishResult(
                    ok = published,
                    backgrounded = committed && !published,
                    restoreFailed = outcome == SetupOutcome.RESTORE_FAILED,
                ),
            )
        }
    }

    /** Drops the staged backup, then finishes setup with an empty vault. */
    fun skipRestore(onResult: (FinishResult) -> Unit) {
        pendingRestore.clear()
        finishSetup(onResult)
    }

    private suspend fun openRestoreAndCommit(): SetupOutcome {
        // A failed open keeps a staged restore for a retry of this same setup.
        if (!securityManager.openInitialVault()) return SetupOutcome.FAILED
        val outcome = when {
            !pendingRestore.apply() -> SetupOutcome.RESTORE_FAILED
            !securityManager.commitInitialSetup() -> SetupOutcome.FAILED
            else -> SetupOutcome.COMMITTED
        }
        // Nothing committed: close the new vault rather than leave it open behind an error.
        if (outcome != SetupOutcome.COMMITTED) securityManager.lock()
        return outcome
    }

    private enum class SetupOutcome { COMMITTED, RESTORE_FAILED, FAILED }

    data class FinishResult(val ok: Boolean, val backgrounded: Boolean, val restoreFailed: Boolean)
}
