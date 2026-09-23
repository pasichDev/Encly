package com.pasich.encly.presentation.screen.backup

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.composables.icons.lucide.ArchiveRestore
import com.composables.icons.lucide.DatabaseBackup
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldCheck
import com.pasich.encly.R
import com.pasich.encly.core.backup.BackupFormat
import com.pasich.encly.presentation.components.custombox.RoundPosition
import com.pasich.encly.presentation.components.custombox.SettingBox
import com.pasich.encly.presentation.viewmodel.BackupAction
import com.pasich.encly.presentation.viewmodel.BackupMessage
import com.pasich.encly.presentation.viewmodel.BackupStep
import com.pasich.encly.presentation.viewmodel.BackupViewModel
import com.pasich.encly.utils.formatNoteDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Backup: encrypted export and import. Files go through the Storage Access
 * Framework only, so Encly needs no storage permission and never learns more than the one
 * document the user picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    BackupDialogs(rememberBackupDialogActions(viewModel), state.step)

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.clearMessage()
        snackbarHostState.showSnackbar(message.resolve(context))
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.backup_title)) }, navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            BackupIntroCard()
            BackupActions(
                lastExportAt = state.lastExportAt,
                enabled = !state.busy,
                onExport = { viewModel.start(BackupAction.EXPORT) },
                onImport = { viewModel.start(BackupAction.IMPORT) },
            )
        }
    }
}

@Composable
private fun BackupIntroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.ShieldCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(
                    text = stringResource(R.string.backup_intro_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.backup_intro_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun BackupActions(lastExportAt: Long?, enabled: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    val lastExport = lastExportAt?.let {
        stringResource(R.string.backup_last_export, formatNoteDate(Date(it)))
    } ?: stringResource(R.string.backup_never_exported)
    SettingBox(
        title = stringResource(R.string.backup_export),
        subTitle = lastExport,
        roundPosition = RoundPosition.First,
        icon = rememberVectorPainter(Lucide.DatabaseBackup),
        action = { if (enabled) onExport() },
    )
    SettingBox(
        title = stringResource(R.string.backup_import),
        subTitle = stringResource(R.string.backup_import_desc),
        roundPosition = RoundPosition.Last,
        icon = rememberVectorPainter(Lucide.ArchiveRestore),
        action = { if (enabled) onImport() },
    )
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
