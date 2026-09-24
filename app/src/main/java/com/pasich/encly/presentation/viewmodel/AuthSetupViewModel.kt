package com.pasich.encly.presentation.viewmodel

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

/**
 * The last step of first-run setup: onboarding has created the vault and set its PIN (and
 * biometric) slots; this opens it, imports a backup staged by "Restore from backup" and commits.
 */
@HiltViewModel
class AuthSetupViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val sessionLockManager: SessionLockManager,
    private val pendingRestore: PendingRestore,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // Kept here, not in saved state: both die with the process, like the staged backup they
    // refer to. A "Retry" restored after a process death would otherwise commit an empty vault.
    private val _restoreFailed = MutableStateFlow(false)

    /** The staged backup did not import; the screen asks to retry or to skip it. */
    val restoreFailed: StateFlow<Boolean> = _restoreFailed.asStateFlow()

    private val _finishFailed = MutableStateFlow(false)

    /** Setup did not complete (the vault did not open or the commit failed). */
    val finishFailed: StateFlow<Boolean> = _finishFailed.asStateFlow()

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
        _restoreFailed.value = false
        _finishFailed.value = false
        viewModelScope.launch {
            _busy.value = true
            val outcome = withContext(Dispatchers.IO) { openRestoreAndCommit() }
            val committed = outcome == SetupOutcome.COMMITTED
            val published = committed && sessionLockManager.onUnlocked()
            _busy.value = false
            report(
                FinishResult(
                    ok = published,
                    backgrounded = committed && !published,
                    restoreFailed = outcome == SetupOutcome.RESTORE_FAILED,
                ),
                onResult,
            )
        }
    }

    /**
     * "Retry" after a failed restore. Refuses, committing nothing, when no backup is staged any
     * more: finishing then would silently open an empty vault instead of the user's notes.
     */
    fun retryRestore(onResult: (FinishResult) -> Unit) {
        if (pendingRestore.isStaged) {
            finishSetup(onResult)
        } else {
            _restoreFailed.value = false
            report(FinishResult(ok = false, backgrounded = false, restoreFailed = false), onResult)
        }
    }

    /** Drops the staged backup, then finishes setup with an empty vault. */
    fun skipRestore(onResult: (FinishResult) -> Unit) {
        pendingRestore.clear()
        finishSetup(onResult)
    }

    private fun report(result: FinishResult, onResult: (FinishResult) -> Unit) {
        _restoreFailed.value = result.restoreFailed
        _finishFailed.value = !result.ok && !result.backgrounded && !result.restoreFailed
        onResult(result)
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
