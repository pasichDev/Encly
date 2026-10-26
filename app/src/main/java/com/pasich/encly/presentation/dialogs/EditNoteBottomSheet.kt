package com.pasich.encly.presentation.dialogs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.presentation.components.FontSizeSlider
import com.pasich.encly.presentation.components.LabeledSlider
import com.pasich.encly.presentation.components.SliderScale
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyNoteColorPicker
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.designsystem.bleed
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.presentation.viewmodel.enclyViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import kotlin.math.roundToInt

enum class EditNoteBottomSheetAction {
    CLOSE_NO_SAVE,
    DUPLICATE,
    FIND,
    TRASH,
    EXPORT_MARKDOWN,
    ARCHIVE,
    UNARCHIVE,
}

/**
 * The editor's "More actions" sheet: text size, then archive, find, duplicate, discard changes and delete. The
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
        EnclyBottomSheet(onDismissRequest = onDismiss, modifier = modifier, scrollable = true) {
            FontSizeSlider(
                currentSize = fontSize,
                onSizeChange = { newSize -> viewModel.updateFontSize(newSize) },
            )
            LineSpacingSlider(selected = lineSpacing, onSelect = viewModel::updateNoteLineSpacing)
            EnclyGroupDivider()
            SectionOverline(stringResource(R.string.note_color_title))
            EnclyNoteColorPicker(
                selected = NoteColor.fromKey(note.note.color),
                onSelect = viewModel::updateColor,
                modifier = Modifier.bleed(EnclyTheme.spacing.gutter),
                contentPadding = PaddingValues(horizontal = EnclyTheme.spacing.gutter),
            )
            ArchiveRow(note = note.note, onAction = onAction, onDismiss = onDismiss)
            EditorActionRows(onAction = onAction, onDismiss = onDismiss)
        }
    }
}

/** Find, export, duplicate, discard and delete: each runs its action and closes the sheet. */
@Composable
private fun EditorActionRows(onAction: (EditNoteBottomSheetAction) -> Unit, onDismiss: () -> Unit) {
    fun run(action: EditNoteBottomSheetAction) {
        onAction(action)
        onDismiss()
    }
    EnclySheetRow(
        title = stringResource(R.string.find_in_note),
        icon = EnclyIcons.Search,
        onClick = { run(EditNoteBottomSheetAction.FIND) },
    )
    EnclySheetRow(
        title = stringResource(R.string.markdown_export),
        icon = EnclyIcons.Upload,
        onClick = { run(EditNoteBottomSheetAction.EXPORT_MARKDOWN) },
    )
    EnclySheetRow(
        title = stringResource(id = R.string.duplicate),
        icon = EnclyIcons.Duplicate,
        onClick = { run(EditNoteBottomSheetAction.DUPLICATE) },
    )
    EnclySheetRow(
        title = stringResource(R.string.note_discard_all_changes),
        icon = EnclyIcons.Undo,
        onClick = { run(EditNoteBottomSheetAction.CLOSE_NO_SAVE) },
    )
    EnclySheetRow(
        title = stringResource(id = R.string.delete),
        icon = EnclyIcons.Trash,
        destructive = true,
        confirmFirst = true,
        onClick = { run(EditNoteBottomSheetAction.TRASH) },
    )
}

/** The reading densities in display order; the enum's own order is fixed by what is stored. */
private val lineSpacingOrder = listOf(
    NoteLineSpacing.COMPACT to R.string.note_line_spacing_compact,
    NoteLineSpacing.DEFAULT to R.string.note_line_spacing_default,
    NoteLineSpacing.COMFORTABLE to R.string.note_line_spacing_comfortable,
)

/** The reading density as a three-step slider, in the same row style as the text size. */
@Composable
private fun LineSpacingSlider(selected: NoteLineSpacing, onSelect: (NoteLineSpacing) -> Unit) {
    val index = lineSpacingOrder.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    LabeledSlider(
        label = stringResource(R.string.note_line_spacing_title),
        valueText = stringResource(lineSpacingOrder[index].second),
        value = index.toFloat(),
        onValueChange = { onSelect(lineSpacingOrder[it.roundToInt().coerceIn(lineSpacingOrder.indices)].first) },
        scale = SliderScale(0f..lineSpacingOrder.lastIndex.toFloat(), steps = lineSpacingOrder.size - 2),
    )
}

/** Archive, or unarchive. Pinning is a list matter: it lives in the notes list's sheet only. */
@Composable
private fun ArchiveRow(note: Note, onAction: (EditNoteBottomSheetAction) -> Unit, onDismiss: () -> Unit) {
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
