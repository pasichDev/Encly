package com.pasich.encly.presentation.screen.handoff

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.backup_count_notes
import com.pasich.encly.core.resources.backup_count_tags
import com.pasich.encly.core.resources.backup_count_tasks
import com.pasich.encly.core.resources.backup_import_done
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.close
import com.pasich.encly.core.resources.done
import com.pasich.encly.core.resources.handoff_done_hint
import com.pasich.encly.core.resources.handoff_done_title
import com.pasich.encly.core.resources.handoff_error_failed
import com.pasich.encly.core.resources.handoff_error_invalid
import com.pasich.encly.core.resources.handoff_error_title
import com.pasich.encly.core.resources.handoff_error_too_large
import com.pasich.encly.core.resources.handoff_error_unsupported
import com.pasich.encly.core.resources.handoff_error_vault
import com.pasich.encly.core.resources.handoff_import
import com.pasich.encly.core.resources.handoff_importing
import com.pasich.encly.core.resources.handoff_preview_attachments
import com.pasich.encly.core.resources.handoff_preview_body
import com.pasich.encly.core.resources.handoff_preview_pinned
import com.pasich.encly.core.resources.handoff_preview_title
import com.pasich.encly.core.resources.handoff_reading
import com.pasich.encly.core.resources.handoff_title
import com.pasich.encly.data.handoff.HandoffError
import com.pasich.encly.data.handoff.HandoffPreview
import com.pasich.encly.presentation.designsystem.CalloutTone
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.StepHeading
import com.pasich.encly.presentation.screen.LockExits
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.presentation.screen.pincode.AuthLoading
import com.pasich.encly.presentation.viewmodel.HandoffStep
import com.pasich.encly.presentation.viewmodel.ImportFromMyNotesViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Import from My Notes: the lock screen while the vault is closed, then the hand-off's steps.
 * [onClose] ends the activity with the result its current step stands for.
 */
@Composable
fun ImportFromMyNotesScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportFromMyNotesViewModel = metroViewModel(),
) {
    val locked by viewModel.locked.collectAsState()
    val step by viewModel.step.collectAsState()
    // A failure shows even while the vault is closed: "nothing can open the vault" comes from
    // the lock screen itself, and nothing of the vault is on that page.
    if (locked && step !is HandoffStep.Failed) {
        // Unlocking publishes itself to the session (LockViewModel); the steps then follow.
        val exits = remember(viewModel, onClose) {
            LockExits(
                onUnlock = {},
                onRecoveryUnlock = {},
                onVaultLost = viewModel::onVaultLost,
                onBack = onClose,
            )
        }
        LockScreen(exits = exits, modifier = modifier)
    } else {
        HandoffContent(step = step, onConfirm = viewModel::confirm, onClose = onClose, modifier = modifier)
    }
}

@Composable
private fun HandoffContent(
    step: HandoffStep,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The import is one transaction; leaving in the middle would only roll it back.
    BackHandler(enabled = step == HandoffStep.Importing) {}
    when (step) {
        HandoffStep.Waiting -> AuthLoading(stringResource(Res.string.handoff_title), modifier)

        HandoffStep.Reading -> AuthLoading(stringResource(Res.string.handoff_reading), modifier)

        HandoffStep.Importing -> AuthLoading(stringResource(Res.string.handoff_importing), modifier)

        is HandoffStep.Preview -> HandoffPage(onClose = onClose, modifier = modifier) {
            PreviewBody(step.preview)
            EnclyButton(text = stringResource(Res.string.handoff_import), onClick = onConfirm)
            EnclyTextButton(
                text = stringResource(Res.string.cancel),
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is HandoffStep.Done -> HandoffPage(onClose = onClose, modifier = modifier) {
            val summary = step.summary
            StepHeading(
                title = stringResource(Res.string.handoff_done_title),
                body = stringResource(
                    Res.string.backup_import_done,
                    summary.notesAdded,
                    summary.tasksAdded,
                    summary.tagsAdded,
                    step.skipped,
                ),
            )
            EnclyCallout(text = stringResource(Res.string.handoff_done_hint))
            EnclyButton(text = stringResource(Res.string.done), onClick = onClose)
        }

        is HandoffStep.Failed -> HandoffPage(onClose = onClose, modifier = modifier) {
            StepHeading(title = stringResource(Res.string.handoff_error_title), body = stringResource(errorText(step)))
            EnclyButton(text = stringResource(Res.string.close), onClick = onClose)
        }
    }
}

/** The counts, then what will not come over. */
@Composable
private fun PreviewBody(preview: HandoffPreview, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.section)) {
        StepHeading(
            title = stringResource(Res.string.handoff_preview_title),
            body = stringResource(
                Res.string.handoff_preview_body,
                pluralStringResource(Res.plurals.backup_count_notes, preview.notes, preview.notes),
                pluralStringResource(Res.plurals.backup_count_tasks, preview.tasks, preview.tasks),
                pluralStringResource(Res.plurals.backup_count_tags, preview.tags, preview.tags),
            ),
        )
        if (preview.attachments > 0) {
            EnclyCallout(
                text = stringResource(Res.string.handoff_preview_attachments, preview.attachments),
                tone = CalloutTone.WARNING,
            )
        }
        if (preview.pinned > 0) {
            EnclyCallout(text = stringResource(Res.string.handoff_preview_pinned, preview.pinned))
        }
    }
}

/** A titled, scrolling page with a close button, for every step that waits for the user. */
@Composable
private fun HandoffPage(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            EnclyTopBar(title = stringResource(Res.string.handoff_title), onClose = onClose)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = EnclyTheme.spacing.gutter, vertical = EnclyTheme.spacing.s),
                verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.section),
                content = content,
            )
        }
    }
}

private fun errorText(step: HandoffStep.Failed): StringResource = when {
    step.vaultUnavailable -> Res.string.handoff_error_vault
    step.error == HandoffError.UNSUPPORTED_SCHEMA -> Res.string.handoff_error_unsupported
    step.error == HandoffError.TOO_LARGE -> Res.string.handoff_error_too_large
    step.error == HandoffError.INVALID_PAYLOAD -> Res.string.handoff_error_invalid
    else -> Res.string.handoff_error_failed
}
