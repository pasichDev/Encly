package com.pasich.encly.presentation.screen.backup

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
import com.pasich.encly.core.resources.pdf_export_warning_body
import com.pasich.encly.core.resources.pdf_export_warning_title
import com.pasich.encly.core.resources.print_warning_body
import com.pasich.encly.core.resources.print_warning_title
import com.pasich.encly.data.export.MarkdownExport
import com.pasich.encly.platform.LocalDocumentPickers
import com.pasich.encly.platform.PickerUnavailableException
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.viewmodel.BackupMessage
import com.pasich.encly.presentation.viewmodel.BackupStep
import com.pasich.encly.presentation.viewmodel.MarkdownExportViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * The screen side of the Markdown export, drawn by the Backup screen and by the editor: the
 * warning that the file is not encrypted, the PIN (or biometric) re-authentication every export
 * uses, and the system save dialog the file is written through. [onMessage] shows the result.
 * Nothing else leaves the app: no share sheet, no temp file.
 */
@Composable
internal fun MarkdownExportHost(
    onMessage: (String) -> Unit,
    viewModel: MarkdownExportViewModel = metroViewModel(),
    onMessageRes: ((StringResource) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickers = LocalDocumentPickers.current
    val noteLauncher = pickers.rememberCreateDocument(MarkdownExport.MIME_NOTE, viewModel::onTarget)
    val zipLauncher = pickers.rememberCreateDocument(MarkdownExport.MIME_ZIP, viewModel::onTarget)
    val pdfLauncher = pickers.rememberCreateDocument(MIME_PDF, viewModel::onTarget)
    val currentOnMessage by rememberUpdatedState(onMessage)
    val currentOnMessageRes by rememberUpdatedState(onMessageRes)

    LaunchedEffect(state.step) {
        val step = state.step as? BackupStep.PickMarkdownTarget ?: return@LaunchedEffect
        try {
            val launcher = when {
                step.pdf -> pdfLauncher
                step.zip -> zipLauncher
                else -> noteLauncher
            }
            launcher.launch(step.name)
        } catch (_: PickerUnavailableException) {
            viewModel.onSystemPickerUnavailable()
            return@LaunchedEffect
        }
        viewModel.onSystemPickerLaunched()
    }
    LaunchedEffect(state.message) {
        val message = state.message as? BackupMessage.Text ?: return@LaunchedEffect
        viewModel.clearMessage()
        // A host that shows its messages itself (the list's toasts) takes the resource; the others the text.
        currentOnMessageRes?.invoke(message.id) ?: currentOnMessage(getString(message.id))
    }

    when (val step = state.step) {
        BackupStep.ConfirmPlainExport -> PlainExportWarning(
            onContinue = viewModel::confirmWarning,
            onCancel = viewModel::cancel,
        )

        is BackupStep.ConfirmDocumentExport -> PlainExportWarning(
            onContinue = viewModel::confirmWarning,
            onCancel = viewModel::cancel,
            title = stringResource(
                if (step.print) Res.string.print_warning_title else Res.string.pdf_export_warning_title,
            ),
            body = stringResource(
                if (step.print) Res.string.print_warning_body else Res.string.pdf_export_warning_body,
            ),
        )

        is BackupStep.Reauth -> ReauthDialog(viewModel.reauthFlow, step, viewModel::cancel)

        else -> Unit
    }
}

private const val MIME_PDF = "application/pdf"

/** Before any plaintext export: the file is not encrypted, wherever it is saved. */
@Composable
private fun PlainExportWarning(
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    title: String = stringResource(Res.string.markdown_export_warning_title),
    body: String = stringResource(Res.string.markdown_export_warning_body),
) {
    EnclyDialog(
        title = title,
        text = body,
        onDismissRequest = onCancel,
        confirm = DialogAction(stringResource(Res.string.action_continue), onContinue),
        dismiss = DialogAction(stringResource(Res.string.cancel), onCancel),
    )
}
