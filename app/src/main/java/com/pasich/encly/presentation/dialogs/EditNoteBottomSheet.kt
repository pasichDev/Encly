package com.pasich.encly.presentation.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.presentation.components.FontSizeSlider
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyRadioRow
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.presentation.viewmodel.enclyViewModel

enum class EditNoteBottomSheetAction {
    CLOSE_NO_SAVE,
    DUPLICATE,
    FIND,
    TRASH,
    TOGGLE_PIN,
    ARCHIVE,
    UNARCHIVE,
}

/**
 * The editor's "More actions" sheet: text size, then pin, archive, find, duplicate, discard changes and delete. The
 * font itself is chosen app-wide in Settings, Appearance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = enclyViewModel<EditNoteViewModel>(),
    onAction: (EditNoteBottomSheetAction) -> Unit = {},
) {
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val lineSpacing by viewModel.noteLineSpacing.collectAsStateWithLifecycle()
    val note by viewModel.state.collectAsStateWithLifecycle()

    if (isVisible) {
        EnclyBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
            FontSizeSlider(
                currentSize = fontSize,
                onSizeChange = { newSize -> viewModel.updateFontSize(newSize) },
            )
            SectionOverline(stringResource(R.string.note_line_spacing_title))
            LineSpacingChoices(selected = lineSpacing, onSelect = viewModel::updateNoteLineSpacing)
            EnclyGroupDivider()
            PinAndArchiveRows(note = note.note, onAction = onAction, onDismiss = onDismiss)
            EnclySheetRow(
                title = stringResource(R.string.find_in_note),
                icon = EnclyIcons.Search,
                onClick = {
                    onAction(EditNoteBottomSheetAction.FIND)
                    onDismiss()
                },
            )
            EnclySheetRow(
                title = stringResource(id = R.string.duplicate),
                icon = EnclyIcons.Duplicate,
                onClick = {
                    onAction(EditNoteBottomSheetAction.DUPLICATE)
                    onDismiss()
                },
            )
            EnclySheetRow(
                title = stringResource(R.string.note_discard_all_changes),
                icon = EnclyIcons.Undo,
                onClick = {
                    onAction(EditNoteBottomSheetAction.CLOSE_NO_SAVE)
                    onDismiss()
                },
            )
            EnclySheetRow(
                title = stringResource(id = R.string.delete),
                icon = EnclyIcons.Trash,
                destructive = true,
                confirmFirst = true,
                onClick = {
                    onAction(EditNoteBottomSheetAction.TRASH)
                    onDismiss()
                },
            )
        }
    }
}

/** The reading densities in display order; the enum's own order is fixed by what is stored. */
private val lineSpacingOrder = listOf(
    NoteLineSpacing.COMPACT to R.string.note_line_spacing_compact,
    NoteLineSpacing.DEFAULT to R.string.note_line_spacing_default,
    NoteLineSpacing.COMFORTABLE to R.string.note_line_spacing_comfortable,
)

@Composable
private fun LineSpacingChoices(selected: NoteLineSpacing, onSelect: (NoteLineSpacing) -> Unit) {
    lineSpacingOrder.forEach { (spacing, label) ->
        EnclyRadioRow(
            title = stringResource(label),
            selected = selected == spacing,
            onClick = { onSelect(spacing) },
        )
    }
}

/** Pin (not for an archived note: it is never pinned) and archive, or unarchive. */
@Composable
private fun PinAndArchiveRows(note: Note, onAction: (EditNoteBottomSheetAction) -> Unit, onDismiss: () -> Unit) {
    if (!note.isArchived) {
        EnclySheetRow(
            title = stringResource(if (note.isPinned) R.string.note_unpin else R.string.note_pin),
            icon = if (note.isPinned) EnclyIcons.PinOff else EnclyIcons.Pin,
            onClick = {
                onAction(EditNoteBottomSheetAction.TOGGLE_PIN)
                onDismiss()
            },
        )
    }
    EnclySheetRow(
        title = stringResource(if (note.isArchived) R.string.note_unarchive else R.string.note_archive),
        icon = if (note.isArchived) EnclyIcons.Unarchive else EnclyIcons.Archive,
        onClick = {
            onAction(
                if (note.isArchived) EditNoteBottomSheetAction.UNARCHIVE else EditNoteBottomSheetAction.ARCHIVE,
            )
            onDismiss()
        },
    )
}
