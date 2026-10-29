package com.pasich.encly.presentation.dialogs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.delete
import com.pasich.encly.core.resources.duplicate
import com.pasich.encly.core.resources.find_in_note
import com.pasich.encly.core.resources.markdown_copy
import com.pasich.encly.core.resources.markdown_export
import com.pasich.encly.core.resources.note_archive
import com.pasich.encly.core.resources.note_color_title
import com.pasich.encly.core.resources.note_discard_all_changes
import com.pasich.encly.core.resources.note_text_use_default
import com.pasich.encly.core.resources.note_unarchive
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.presentation.components.FontSizeSlider
import com.pasich.encly.presentation.components.NoteLineSpacingSlider
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyNoteColorPicker
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.designsystem.bleed
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

enum class EditNoteBottomSheetAction {
    CLOSE_NO_SAVE,
    DUPLICATE,
    FIND,
    TRASH,
    EXPORT_MARKDOWN,
    COPY_MARKDOWN,
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
    viewModel: EditNoteViewModel = metroViewModel<EditNoteViewModel>(),
    onAction: (EditNoteBottomSheetAction) -> Unit = {},
) {
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val lineSpacing by viewModel.noteLineSpacing.collectAsStateWithLifecycle()
    val note by viewModel.state.collectAsStateWithLifecycle()
    val override by viewModel.textOverride.collectAsStateWithLifecycle()
    val loadFailed by viewModel.contentLoadFailed.collectAsStateWithLifecycle()

    if (isVisible) {
        EnclyBottomSheet(onDismissRequest = onDismiss, modifier = modifier, scrollable = true) {
            FontSizeSlider(
                currentSize = fontSize,
                onSizeChange = { newSize -> viewModel.updateFontSize(newSize) },
            )
            NoteLineSpacingSlider(selected = lineSpacing, onSelect = viewModel::updateNoteLineSpacing)
            UseDefaultRow(visible = override.isSet, onClick = viewModel::useDefaultText)
            EnclyGroupDivider()
            SectionOverline(stringResource(Res.string.note_color_title))
            EnclyNoteColorPicker(
                selected = NoteColor.fromKey(note.note.color),
                onSelect = viewModel::updateColor,
                modifier = Modifier.bleed(EnclyTheme.spacing.gutter),
                contentPadding = PaddingValues(horizontal = EnclyTheme.spacing.gutter),
            )
            ArchiveRow(note = note.note, onAction = onAction, onDismiss = onDismiss)
            EditorActionRows(canCopy = !loadFailed, onAction = onAction, onDismiss = onDismiss)
        }
    }
}

/**
 * Find, copy, export, duplicate, discard and delete: each runs its action and closes the sheet.
 * Copy as Markdown is hidden for a note whose content could not be read, like the export's no-op.
 */
@Composable
private fun EditorActionRows(canCopy: Boolean, onAction: (EditNoteBottomSheetAction) -> Unit, onDismiss: () -> Unit) {
    fun run(action: EditNoteBottomSheetAction) {
        onAction(action)
        onDismiss()
    }
    EnclySheetRow(
        title = stringResource(Res.string.find_in_note),
        icon = EnclyIcons.Search,
        onClick = { run(EditNoteBottomSheetAction.FIND) },
    )
    EnclySheetRow(
        title = stringResource(Res.string.markdown_export),
        icon = EnclyIcons.Upload,
        onClick = { run(EditNoteBottomSheetAction.EXPORT_MARKDOWN) },
    )
    if (canCopy) {
        EnclySheetRow(
            title = stringResource(Res.string.markdown_copy),
            icon = EnclyIcons.Copy,
            onClick = { run(EditNoteBottomSheetAction.COPY_MARKDOWN) },
        )
    }
    EnclySheetRow(
        title = stringResource(Res.string.duplicate),
        icon = EnclyIcons.Duplicate,
        onClick = { run(EditNoteBottomSheetAction.DUPLICATE) },
    )
    EnclySheetRow(
        title = stringResource(Res.string.note_discard_all_changes),
        icon = EnclyIcons.Undo,
        onClick = { run(EditNoteBottomSheetAction.CLOSE_NO_SAVE) },
    )
    EnclySheetRow(
        title = stringResource(Res.string.delete),
        icon = EnclyIcons.Trash,
        destructive = true,
        confirmFirst = true,
        onClick = { run(EditNoteBottomSheetAction.TRASH) },
    )
}

/** Archive, or unarchive. Pinning is a list matter: it lives in the notes list's sheet only. */
@Composable
private fun ArchiveRow(note: Note, onAction: (EditNoteBottomSheetAction) -> Unit, onDismiss: () -> Unit) {
    EnclySheetRow(
        title = stringResource(if (note.isArchived) Res.string.note_unarchive else Res.string.note_archive),
        icon = if (note.isArchived) EnclyIcons.Unarchive else EnclyIcons.Archive,
        onClick = {
            onAction(
                if (note.isArchived) EditNoteBottomSheetAction.UNARCHIVE else EditNoteBottomSheetAction.ARCHIVE,
            )
            onDismiss()
        },
    )
}

/** "Use default": shown while the note sets its own text size or line spacing. */
@Composable
private fun UseDefaultRow(visible: Boolean, onClick: () -> Unit) {
    if (!visible) return
    EnclySheetRow(title = stringResource(Res.string.note_text_use_default), icon = EnclyIcons.Undo, onClick = onClick)
}
