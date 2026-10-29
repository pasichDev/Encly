package com.pasich.encly.presentation.screen.backup

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.markdown_export_warning_body
import com.pasich.encly.core.resources.markdown_export_warning_title
import com.pasich.encly.data.export.MarkdownExport
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.viewmodel.BackupMessage
import com.pasich.encly.presentation.viewmodel.BackupStep
import com.pasich.encly.presentation.viewmodel.MarkdownExportViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * The screen side of the Markdown export, drawn by the Backup screen and by the editor: the
 * warning that the file is not encrypted, the PIN (or biometric) re-authentication every export
 * uses, and the system save dialog the file is written through. [onMessage] shows the result.
 * Nothing else leaves the app: no share sheet, no temp file.
 */
@Composable
internal fun MarkdownExportHost(onMessage: (String) -> Unit, viewModel: MarkdownExportViewModel = metroViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val noteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MarkdownExport.MIME_NOTE),
        viewModel::onTarget,
    )
    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MarkdownExport.MIME_ZIP),
        viewModel::onTarget,
    )
    val currentOnMessage by rememberUpdatedState(onMessage)

    LaunchedEffect(state.step) {
        val step = state.step as? BackupStep.PickMarkdownTarget ?: return@LaunchedEffect
        try {
            (if (step.zip) zipLauncher else noteLauncher).launch(step.name)
        } catch (_: ActivityNotFoundException) {
            viewModel.onSystemPickerUnavailable()
            return@LaunchedEffect
        }
        viewModel.onSystemPickerLaunched()
    }
    LaunchedEffect(state.message) {
        val message = state.message as? BackupMessage.Text ?: return@LaunchedEffect
        viewModel.clearMessage()
        currentOnMessage(getString(message.id))
    }

    when (val step = state.step) {
        BackupStep.ConfirmPlainExport -> PlainExportWarning(
            onContinue = viewModel::confirmWarning,
            onCancel = viewModel::cancel,
        )

        is BackupStep.Reauth -> ReauthDialog(viewModel.reauthFlow, step, viewModel::cancel)

        else -> Unit
    }
}

/** Before any plaintext export: the file is not encrypted, wherever it is saved. */
@Composable
private fun PlainExportWarning(onContinue: () -> Unit, onCancel: () -> Unit) {
    EnclyDialog(
        title = stringResource(Res.string.markdown_export_warning_title),
        text = stringResource(Res.string.markdown_export_warning_body),
        onDismissRequest = onCancel,
        confirm = DialogAction(stringResource(Res.string.action_continue), onContinue),
        dismiss = DialogAction(stringResource(Res.string.cancel), onCancel),
    )
}
