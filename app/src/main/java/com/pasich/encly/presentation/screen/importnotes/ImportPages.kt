package com.pasich.encly.presentation.screen.importnotes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.done
import com.pasich.encly.core.resources.import_choose_again
import com.pasich.encly.core.resources.import_choose_files
import com.pasich.encly.core.resources.import_choose_folder
import com.pasich.encly.core.resources.import_choose_source_body
import com.pasich.encly.core.resources.import_choose_source_title
import com.pasich.encly.core.resources.import_done_body
import com.pasich.encly.core.resources.import_done_hint
import com.pasich.encly.core.resources.import_done_title
import com.pasich.encly.core.resources.import_error_encrypted_nn
import com.pasich.encly.core.resources.import_error_encrypted_sn
import com.pasich.encly.core.resources.import_error_failed
import com.pasich.encly.core.resources.import_error_no_picker
import com.pasich.encly.core.resources.import_error_nothing
import com.pasich.encly.core.resources.import_error_title
import com.pasich.encly.core.resources.import_error_too_large
import com.pasich.encly.core.resources.import_error_unreadable
import com.pasich.encly.core.resources.import_error_unsupported
import com.pasich.encly.core.resources.import_error_vault
import com.pasich.encly.core.resources.import_intro
import com.pasich.encly.core.resources.import_loss_archived
import com.pasich.encly.core.resources.import_loss_attachments
import com.pasich.encly.core.resources.import_loss_colors
import com.pasich.encly.core.resources.import_loss_files
import com.pasich.encly.core.resources.import_loss_nested
import com.pasich.encly.core.resources.import_loss_pinned
import com.pasich.encly.core.resources.import_loss_tags
import com.pasich.encly.core.resources.import_loss_todos
import com.pasich.encly.core.resources.import_loss_unsupported
import com.pasich.encly.core.resources.import_section_supported
import com.pasich.encly.core.resources.import_stat_checklists
import com.pasich.encly.core.resources.import_stat_duplicates
import com.pasich.encly.core.resources.import_stat_repeated
import com.pasich.encly.core.resources.import_stat_tags
import com.pasich.encly.core.resources.import_stat_trashed
import com.pasich.encly.data.importer.ImportLosses
import com.pasich.encly.data.importer.ImportPreview
import com.pasich.encly.data.importer.ImportSource
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.EnclyTonalButton
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.designsystem.StepHeading
import com.pasich.encly.presentation.viewmodel.ImportFailure
import com.pasich.encly.presentation.viewmodel.ImportResult
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StartPage(actions: ImportNotesActions) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.section)) {
        Text(
            text = stringResource(Res.string.import_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
            EnclyButton(text = stringResource(Res.string.import_choose_files), onClick = actions.onChooseFiles)
            EnclyTonalButton(text = stringResource(Res.string.import_choose_folder), onClick = actions.onChooseFolder)
        }
        Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
            SectionOverline(text = stringResource(Res.string.import_section_supported))
            Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.cardGap)) {
                ImportSource.entries.forEach { SourceCard(it) }
            }
        }
    }
}

@Composable
internal fun Labelled(label: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.textGap)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun previewLines(preview: ImportPreview): List<String> = buildList {
    add(stringResource(Res.string.import_stat_checklists, preview.checklists))
    add(stringResource(Res.string.import_stat_tags, preview.tags))
    if (preview.trashed > 0) add(stringResource(Res.string.import_stat_trashed, preview.trashed))
    if (preview.duplicates > 0) add(stringResource(Res.string.import_stat_duplicates, preview.duplicates))
    if (preview.duplicatesInSelection > 0) {
        add(stringResource(Res.string.import_stat_repeated, preview.duplicatesInSelection))
    }
}

@Composable
internal fun lossLines(preview: ImportPreview): List<String> {
    val losses: ImportLosses = preview.losses
    return buildList {
        if (losses.attachments > 0) add(stringResource(Res.string.import_loss_attachments, losses.attachments))
        if (losses.pinned > 0) add(stringResource(Res.string.import_loss_pinned, losses.pinned))
        if (losses.archived > 0) add(stringResource(Res.string.import_loss_archived, losses.archived))
        if (losses.colors > 0) add(stringResource(Res.string.import_loss_colors, losses.colors))
        if (losses.extraTags > 0) add(stringResource(Res.string.import_loss_tags, losses.extraTags))
        if (losses.todos > 0) add(stringResource(Res.string.import_loss_todos, losses.todos))
        if (preview.unsupported > 0) add(stringResource(Res.string.import_loss_unsupported, preview.unsupported))
        if (preview.filesSkipped > 0) add(stringResource(Res.string.import_loss_files, preview.filesSkipped))
        if (preview.nestedArchives > 0) add(stringResource(Res.string.import_loss_nested, preview.nestedArchives))
    }
}

@Composable
internal fun ChooseSourcePage(actions: ImportNotesActions) {
    StepHeading(
        title = stringResource(Res.string.import_choose_source_title),
        body = stringResource(Res.string.import_choose_source_body),
    )
    ImportSource.entries.forEach { source ->
        EnclyTonalButton(text = stringResource(source.title), onClick = { actions.onChooseSource(source) })
    }
    EnclyTextButton(
        text = stringResource(Res.string.cancel),
        onClick = actions.onCancel,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun DonePage(result: ImportResult, actions: ImportNotesActions) {
    StepHeading(
        title = stringResource(Res.string.import_done_title),
        body = stringResource(Res.string.import_done_body, result.imported, result.duplicates, result.notImported),
    )
    EnclyCallout(text = stringResource(Res.string.import_done_hint))
    EnclyButton(text = stringResource(Res.string.done), onClick = actions.onCancel)
}

@Composable
internal fun FailedPage(failure: ImportFailure, actions: ImportNotesActions) {
    StepHeading(title = stringResource(Res.string.import_error_title), body = stringResource(errorText(failure)))
    EnclyButton(text = stringResource(Res.string.import_choose_again), onClick = actions.onCancel)
}

internal fun errorText(failure: ImportFailure): StringResource = when (failure) {
    ImportFailure.ENCRYPTED_STANDARD_NOTES -> Res.string.import_error_encrypted_sn
    ImportFailure.ENCRYPTED_NOTESNOOK -> Res.string.import_error_encrypted_nn
    ImportFailure.UNSUPPORTED -> Res.string.import_error_unsupported
    ImportFailure.TOO_LARGE -> Res.string.import_error_too_large
    ImportFailure.NOTHING_FOUND -> Res.string.import_error_nothing
    ImportFailure.UNREADABLE -> Res.string.import_error_unreadable
    ImportFailure.NO_PICKER -> Res.string.import_error_no_picker
    ImportFailure.VAULT -> Res.string.import_error_vault
    ImportFailure.IMPORT -> Res.string.import_error_failed
}
