package com.pasich.encly.presentation.screen.backup

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.core.backup.BackupFormat
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyPillButton
import com.pasich.encly.presentation.designsystem.EnclySnackbarHost
import com.pasich.encly.presentation.designsystem.EnclyTonalButton
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.viewmodel.BackupAction
import com.pasich.encly.presentation.viewmodel.BackupMessage
import com.pasich.encly.presentation.viewmodel.BackupStep
import com.pasich.encly.presentation.viewmodel.BackupUiState
import com.pasich.encly.presentation.viewmodel.BackupViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.utils.formatNoteDate
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Backup: encrypted export and import. Files go through the Storage Access
 * Framework only, so Encly needs no storage permission and never learns more than the one
 * document the user picked.
 */
@Composable
fun BackupScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    BackupPickers(
        step = state.step,
        onExportTarget = viewModel.exportFlow::onTarget,
        onImportFile = viewModel.importFlow::onFile,
        onPickerLaunch = viewModel::onSystemPickerLaunched,
        onPickerUnavailable = viewModel::onSystemPickerUnavailable,
    )
    val dialogActions = rememberBackupDialogActions(viewModel)

    // Shown from a screen-wide scope: clearing the message restarts this effect, which must not
    // cancel the snackbar it just opened.
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.clearMessage()
        snackbarScope.launch { snackbarHostState.showSnackbar(message.resolve(context)) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                EnclyTopBar(title = stringResource(R.string.backup_title), onBack = { navController.popBackStack() })
            },
            snackbarHost = { EnclySnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                // A fixed slot: the page does not jump when the progress bar comes and goes.
                Box(modifier = Modifier.fillMaxWidth().height(EnclyTheme.spacing.xxs)) {
                    if (state.busy) {
                        LinearProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                BackupContent(state = state, onStart = viewModel::start)
            }
        }
        // Drawn last: the phrase steps cover the whole screen.
        BackupDialogs(dialogActions, state.step, state.busy)
    }
}

@Composable
private fun BackupContent(state: BackupUiState, onStart: (BackupAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = EnclyTheme.spacing.gutter, vertical = EnclyTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.section),
    ) {
        Text(
            text = stringResource(R.string.backup_intro_short),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
            SectionOverline(text = stringResource(R.string.backup_section_export))
            ExportAction(
                lastExportAt = state.lastExportAt,
                enabled = !state.busy,
                onExport = { onStart(BackupAction.EXPORT) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
            SectionOverline(text = stringResource(R.string.backup_section_import))
            ImportAction(enabled = !state.busy, onImport = { onStart(BackupAction.IMPORT) })
        }
        EnclyCallout(
            title = stringResource(R.string.backup_intro_title),
            text = stringResource(R.string.backup_intro_body),
            icon = EnclyIcons.Shield,
        )
    }
}

@Composable
private fun ExportAction(lastExportAt: Long?, enabled: Boolean, onExport: () -> Unit) {
    val lastExport = lastExportAt?.let {
        stringResource(R.string.backup_last_export, formatNoteDate(Date(it)))
    } ?: stringResource(R.string.backup_never_exported)
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs)) {
        EnclyTonalButton(text = stringResource(R.string.backup_export), onClick = onExport, enabled = enabled)
        Text(text = lastExport, style = EnclyTheme.typography.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ImportAction(enabled: Boolean, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs)) {
        EnclyPillButton(
            text = stringResource(R.string.backup_import),
            onClick = onImport,
            enabled = enabled,
            leadingIcon = EnclyIcons.Restore,
        )
        Text(
            text = stringResource(R.string.backup_import_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Opens the system "create document" / "open document" picker when a flow asks for it. */
@Composable
private fun BackupPickers(
    step: BackupStep,
    onExportTarget: (Uri?) -> Unit,
    onImportFile: (Uri?) -> Unit,
    onPickerLaunch: () -> Unit,
    onPickerUnavailable: () -> Unit,
) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE),
        onExportTarget,
    )
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        onImportFile,
    )
    // The effect restarts only on [step]; always report to the latest callbacks.
    val currentOnPickerLaunched by rememberUpdatedState(onPickerLaunch)
    val currentOnPickerUnavailable by rememberUpdatedState(onPickerUnavailable)
    LaunchedEffect(step) {
        when (step) {
            BackupStep.PickExportTarget ->
                launchPicker(currentOnPickerLaunched, currentOnPickerUnavailable) {
                    exportLauncher.launch(defaultFileName())
                }

            BackupStep.PickImportFile ->
                launchPicker(currentOnPickerLaunched, currentOnPickerUnavailable) {
                    importLauncher.launch(arrayOf("*/*"))
                }

            else -> Unit
        }
    }
}

/**
 * Keeps the vault open for the picker only once it has really started: a device without a
 * documents app throws [ActivityNotFoundException], which ends the flow with a message
 * instead of crashing and leaves the next backgrounding locking as usual.
 */
private fun launchPicker(onLaunched: () -> Unit, onUnavailable: () -> Unit, launch: () -> Unit) {
    try {
        launch()
    } catch (_: ActivityNotFoundException) {
        onUnavailable()
        return
    }
    onLaunched()
}

/** "encly-backup-2026-09-23.enclybak": a date only, never a title or other content. */
private fun defaultFileName(): String {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
    return "encly-backup-$date.${BackupFormat.FILE_EXTENSION}"
}

private fun BackupMessage.resolve(context: Context): String = when (this) {
    is BackupMessage.Text -> context.getString(id)

    is BackupMessage.Imported -> context.getString(
        R.string.backup_import_done,
        summary.notesAdded,
        summary.tasksAdded,
        summary.tagsAdded,
        summary.skipped,
    )
}
