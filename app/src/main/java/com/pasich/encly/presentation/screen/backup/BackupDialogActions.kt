package com.pasich.encly.presentation.screen.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pasich.encly.presentation.viewmodel.BackupViewModel
import com.pasich.encly.presentation.viewmodel.ImportFlow
import com.pasich.encly.presentation.viewmodel.ReauthFlow
import com.pasich.encly.presentation.viewmodel.RecoveryPhraseFlow

/**
 * The parts of a [BackupViewModel] the backup dialogs drive, so the ViewModel itself stays with
 * the screen. Build it with [rememberBackupDialogActions].
 */
class BackupDialogActions(
    val reauth: ReauthFlow,
    val phrase: RecoveryPhraseFlow,
    val importing: ImportFlow,
    val cancel: () -> Unit,
    val eraseAllData: () -> Unit,
)

@Composable
fun rememberBackupDialogActions(viewModel: BackupViewModel): BackupDialogActions = remember(viewModel) {
    BackupDialogActions(
        reauth = viewModel.reauthFlow,
        phrase = viewModel.phraseFlow,
        importing = viewModel.importFlow,
        cancel = viewModel::cancel,
        eraseAllData = viewModel::eraseAllData,
    )
}
