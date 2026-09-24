package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.R
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.BackupDocuments
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.BackupPhraseSetup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Settings → Backup, plus the vault actions of Settings → Security (create or replace the
 * recovery phrase, erase all data). Every flow starts with re-authentication. Plaintext exists only in memory
 * (the decrypted payload waiting for the merge/replace choice) and is dropped on cancel, on
 * completion and when the ViewModel is cleared, e.g. by a re-lock.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    backupManager: BackupManager,
    private val phraseSetup: BackupPhraseSetup,
    private val securityManager: SecurityManager,
    private val sessionLockManager: SessionLockManager,
    documents: BackupDocuments,
) : ViewModel() {

    private val state = BackupFlowState(viewModelScope, backupManager.lastExportAt(), ::dropPending)
    val uiState: StateFlow<BackupUiState> = state.ui.asStateFlow()

    val exportFlow = ExportFlow(state, backupManager, documents)
    val phraseFlow = RecoveryPhraseFlow(state, backupManager, phraseSetup, onReady = ::onPhraseReady)
    val importFlow = ImportFlow(state, backupManager, documents, onPickerReturned = sessionLockManager::endSystemPicker)
    val reauthFlow = ReauthFlow(state, securityManager, onAuthenticated = ::proceed)

    /** The flow in progress, so a ready recovery phrase continues the right one. */
    private var action: BackupAction? = null

    init {
        // A re-lock drops every plaintext buffer (a decrypted backup, new recovery words) at
        // once, even while the app is in the background and this screen is not composed. An
        // export's sealed file is ciphertext and may still be written to the chosen document.
        viewModelScope.launch {
            sessionLockManager.locked.collect { locked ->
                if (locked) {
                    phraseFlow.drop()
                    importFlow.drop()
                    action = null
                    state.ui.update { it.copy(step = BackupStep.Idle, busy = false) }
                }
            }
        }
    }

    fun start(action: BackupAction) {
        dropPending()
        this.action = action
        val biometric = securityManager.isBiometricEnabled() && securityManager.biometricAvailable()
        state.go(BackupStep.Reauth(action, biometric))
    }

    private suspend fun proceed(action: BackupAction) {
        when {
            action == BackupAction.IMPORT -> state.go(BackupStep.PickImportFile)

            action == BackupAction.ERASE -> state.go(BackupStep.ConfirmErase)

            action == BackupAction.CREATE_PHRASE ->
                if (phraseSetup.hasRecoveryPhrase()) {
                    state.finish(BackupMessage.Text(R.string.backup_phrase_exists))
                } else {
                    phraseFlow.create()
                }

            // Replacing needs an existing phrase; a vault without one simply gets its first.
            action == BackupAction.REPLACE_PHRASE ->
                if (phraseSetup.hasRecoveryPhrase()) state.go(BackupStep.ConfirmReplacePhrase) else phraseFlow.create()

            !phraseSetup.hasRecoveryPhrase() -> state.go(BackupStep.NeedsPhrase)

            phraseSetup.needsConfirmation() -> state.go(BackupStep.ConfirmExistingPhrase())

            else -> exportFlow.seal()
        }
    }

    private suspend fun onPhraseReady() {
        if (action == BackupAction.EXPORT) exportFlow.seal() else state.go(BackupStep.Idle)
    }

    /**
     * Deletes the vault (database, key slots, settings) after [BackupStep.ConfirmErase]. There
     * is no undo: the only way back is a backup file.
     */
    fun eraseAllData() {
        if (state.step != BackupStep.ConfirmErase) return
        dropPending()
        state.launchBusy {
            withContext(Dispatchers.IO) { securityManager.wipeAndReset() }
            state.ui.update { it.copy(step = BackupStep.Idle, erased = true) }
        }
    }

    /**
     * The system picker is another app. Only the import picker keeps the vault open while it is
     * in front (briefly, see SessionLockManager.allowSystemPicker): the vault is needed again
     * when the file comes back. An export's file is already sealed, so its picker needs no
     * open vault and backgrounding locks as usual.
     */
    fun onSystemPickerLaunched() {
        if (state.step == BackupStep.PickImportFile) sessionLockManager.allowSystemPicker()
        state.go(BackupStep.Idle)
    }

    /** No app on this device handles the Storage Access Framework picker. */
    fun onSystemPickerUnavailable() {
        sessionLockManager.endSystemPicker()
        state.finish(BackupMessage.Text(R.string.backup_error_no_picker))
    }

    fun cancel() {
        dropPending()
        action = null
        state.go(BackupStep.Idle)
    }

    fun clearMessage() {
        state.ui.update { it.copy(message = null) }
    }

    override fun onCleared() {
        dropPending()
        super.onCleared()
    }

    private fun dropPending() {
        exportFlow.drop()
        phraseFlow.drop()
        importFlow.drop()
    }
}
