package com.pasich.encly.presentation.screen.importnotes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.data.importer.ImportPreview
import com.pasich.encly.data.importer.ReviewNote
import com.pasich.encly.presentation.designsystem.CalloutTone
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyCard
import com.pasich.encly.presentation.designsystem.EnclyCardStyle
import com.pasich.encly.presentation.designsystem.EnclyChip
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.StepHeading
import com.pasich.encly.presentation.viewmodel.ReviewFilter
import com.pasich.encly.presentation.viewmodel.ReviewState
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.utils.formatNoteDate
import java.util.Date

/**
 * The review step before an import: the detected source with its counts and losses, then every
 * note with a checkbox. Only ticked notes are imported; a note already in the vault is shown but
 * cannot be ticked. Read-only: titles and snippets are shown here and nowhere else, and nothing
 * on this page is saved in instance state, copied or shared.
 */
@Composable
fun ImportReviewScreen(
    preview: ImportPreview,
    review: ReviewState,
    actions: ImportNotesActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { EnclyTopBar(title = stringResource(R.string.import_title), onBack = actions.onCancel) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = EnclyTheme.spacing.gutter, vertical = EnclyTheme.spacing.s),
                verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
            ) {
                EnclyButton(
                    text = stringResource(R.string.import_action_import_count, review.selected.size),
                    onClick = actions.onImport,
                    enabled = review.selected.isNotEmpty(),
                )
                EnclyTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = actions.onCancel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = EnclyTheme.spacing.gutter, vertical = EnclyTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
        ) {
            item { Summary(preview) }
            item { Controls(review, actions) }
            items(review.visible, key = { it.uid }) { note ->
                NoteRow(
                    note = note,
                    checked = note.uid in review.selected,
                    expandedText = review.expandedText.takeIf { review.expanded == note.uid },
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun Summary(preview: ImportPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.section)) {
        StepHeading(
            title = stringResource(R.string.import_preview_title),
            body = stringResource(
                R.string.import_detected,
                stringResource(preview.source.title),
                pluralStringResource(R.plurals.backup_count_notes, preview.notes, preview.notes),
            ),
        )
        EnclyCard {
            previewLines(preview).forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                text = stringResource(R.string.import_stat_destination),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val losses = lossLines(preview)
        if (losses.isNotEmpty()) {
            EnclyCallout(
                title = stringResource(R.string.import_label_losses),
                text = losses.joinToString("\n"),
                tone = CalloutTone.WARNING,
            )
        }
        Text(
            text = stringResource(R.string.import_preview_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "N of M selected", select all / none, and the All / New only / In trash filter. */
@Composable
private fun Controls(review: ReviewState, actions: ImportNotesActions) {
    Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.import_review_selected, review.selected.size, review.newCount),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            val all = review.newCount > 0 && review.selected.size == review.newCount
            EnclyTextButton(
                text = stringResource(if (all) R.string.import_select_none else R.string.import_select_all),
                onClick = actions.onToggleAll,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs)) {
            FilterChip(ReviewFilter.ALL, R.string.import_filter_all, review, actions)
            FilterChip(ReviewFilter.NEW, R.string.import_filter_new, review, actions)
            FilterChip(ReviewFilter.TRASH, R.string.import_filter_trash, review, actions)
        }
    }
}

@Composable
private fun FilterChip(filter: ReviewFilter, label: Int, review: ReviewState, actions: ImportNotesActions) {
    EnclyChip(
        label = stringResource(label),
        selected = review.filter == filter,
        onClick = { actions.onFilter(filter) },
    )
}

@Composable
private fun NoteRow(note: ReviewNote, checked: Boolean, expandedText: String?, actions: ImportNotesActions) {
    EnclyCard(style = EnclyCardStyle.OUTLINED, onClick = { actions.onExpand(note.uid) }) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs)) {
            Checkbox(
                checked = checked,
                onCheckedChange = { actions.onToggle(note.uid) },
                enabled = !note.existing,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.textGap),
            ) {
                Text(
                    text = note.title.ifBlank { stringResource(R.string.import_untitled) },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (note.snippet.isNotEmpty()) {
                    Text(
                        text = note.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    text = badges(note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (expandedText != null) {
                    Text(text = expandedText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Badges and date on one line: checklist, tag, in trash, already in the vault, then the date. */
@Composable
private fun badges(note: ReviewNote): String = buildList {
    if (note.hasChecklist) add(stringResource(R.string.import_badge_checklist))
    note.tag?.let { add("#$it") }
    if (note.trashed) add(stringResource(R.string.import_badge_trash))
    if (note.existing) add(stringResource(R.string.import_badge_in_vault))
    add(formatNoteDate(Date(note.date)))
}.joinToString(" · ")
