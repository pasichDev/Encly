package com.pasich.encly.presentation.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.delete
import com.pasich.encly.core.resources.duplicate
import com.pasich.encly.core.resources.find_in_note
import com.pasich.encly.core.resources.markdown_copy
import com.pasich.encly.core.resources.markdown_export
import com.pasich.encly.core.resources.note_archive
import com.pasich.encly.core.resources.note_discard_all_changes
import com.pasich.encly.core.resources.note_export_pdf
import com.pasich.encly.core.resources.note_print
import com.pasich.encly.core.resources.note_text_use_default
import com.pasich.encly.core.resources.note_unarchive
import com.pasich.encly.core.resources.select_all
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.domain.model.NoteTextOverride
import com.pasich.encly.presentation.components.FontSizeSlider
import com.pasich.encly.presentation.components.NoteLineSpacingSlider
import com.pasich.encly.presentation.designsystem.EnclyDesktopIcons
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyNoteColorPicker
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.presentation.designsystem.EnclySheetTiles
import com.pasich.encly.presentation.designsystem.IconWithTooltip
import com.pasich.encly.presentation.designsystem.SheetTile
import com.pasich.encly.presentation.designsystem.bleed
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

enum class EditNoteBottomSheetAction {
    CLOSE_NO_SAVE,
    DUPLICATE,
    FIND,
    SELECT_ALL,
    TRASH,
    EXPORT_MARKDOWN,
    EXPORT_PDF,
    PRINT,
    COPY_MARKDOWN,
    ARCHIVE,
    UNARCHIVE,
}

/** The test tag of the sheet's content, for its size. */
internal const val EDIT_NOTE_SHEET_TAG = "edit-note-sheet"

/**
 * Which tiles the sheet offers: Copy as Markdown not for a note whose content could not be read
 * ([canCopy]), Select all only where blocks can be picked ([canSelect]), Export PDF and Print where the
 * host makes documents ([documents]); [archived] turns Archive into Unarchive.
 */
internal class EditorRows(val canCopy: Boolean, val canSelect: Boolean, val documents: Boolean, val archived: Boolean)

/** The note's own text settings and colour as the sheet shows them, and what its controls change ([actions]). */
internal class NoteLook(
    val fontSize: Int,
    val lineSpacing: NoteLineSpacing,
    val override: NoteTextOverride,
    val color: NoteColor,
    val actions: NoteLookActions,
)

/** What the sheet's live controls change; the sheet stays open for them. */
internal class NoteLookActions(
    val onSize: (Int) -> Unit,
    val onSpacing: (NoteLineSpacing) -> Unit,
    val onDefaultSize: () -> Unit,
    val onDefaultSpacing: () -> Unit,
    val onColor: (NoteColor) -> Unit,
)

/**
 * The editor's "More actions" sheet: quick actions as tiles, then this note's text size, line spacing and
 * colour, then discard and delete. The font itself is chosen app-wide in Settings, Appearance.
 */
@Composable
fun EditNoteBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditNoteViewModel = metroViewModel<EditNoteViewModel>(),
    documents: Boolean = false,
    onAction: (EditNoteBottomSheetAction) -> Unit = {},
) {
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val lineSpacing by viewModel.noteLineSpacing.collectAsStateWithLifecycle()
    val note by viewModel.state.collectAsStateWithLifecycle()
    val override by viewModel.textOverride.collectAsStateWithLifecycle()
    val loadFailed by viewModel.contentLoadFailed.collectAsStateWithLifecycle()
    val simpleEdit by viewModel.simpleEdit.collectAsStateWithLifecycle()
    EditNoteBottomSheet(
        isVisible = isVisible,
        rows = EditorRows(
            canCopy = !loadFailed,
            canSelect = !loadFailed && !simpleEdit,
            documents = documents,
            archived = note.note.isArchived,
        ),
        look = NoteLook(
            fontSize = fontSize,
            lineSpacing = lineSpacing,
            override = override,
            color = NoteColor.fromKey(note.note.color),
            actions = NoteLookActions(
                onSize = viewModel::updateFontSize,
                onSpacing = viewModel::updateNoteLineSpacing,
                onDefaultSize = viewModel::useDefaultTextSize,
                onDefaultSpacing = viewModel::useDefaultLineSpacing,
                onColor = viewModel::updateColor,
            ),
        ),
        onDismiss = onDismiss,
        onAction = onAction,
        modifier = modifier,
    )
}

/**
 * The sheet itself. Nothing in it comes or goes while it is open, so it never changes height under a
 * finger: the reset buttons are always there (disabled while there is nothing to reset), and which tiles
 * are offered is settled before it opens. A tile or a row closes the sheet, which slides away before its
 * action runs; the sliders and the swatches apply at once and leave it open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditNoteBottomSheet(
    isVisible: Boolean,
    rows: EditorRows,
    look: NoteLook,
    onDismiss: () -> Unit,
    onAction: (EditNoteBottomSheetAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = rememberSheetCloser(sheetState, onDismiss = onDismiss)
    val closeAndRun: (EditNoteBottomSheetAction) -> Unit = { action -> close { onAction(action) } }

    if (isVisible) {
        // Scrolls only when the window is too short for it (split screen, large font): its height never changes.
        AdaptiveSheet(onDismissRequest = onDismiss, modifier = modifier, sheetState = sheetState, scrollable = true) {
            Column(
                verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
                modifier = Modifier.testTag(EDIT_NOTE_SHEET_TAG),
            ) {
                EnclySheetTiles(tiles = editorTiles(rows, closeAndRun))
                EnclyGroupDivider()
                NoteLookControls(look)
                EnclyGroupDivider()
                EnclySheetRow(
                    title = stringResource(Res.string.note_discard_all_changes),
                    icon = EnclyIcons.Undo,
                    onClick = { closeAndRun(EditNoteBottomSheetAction.CLOSE_NO_SAVE) },
                )
                EnclySheetRow(
                    title = stringResource(Res.string.delete),
                    icon = EnclyIcons.Trash,
                    destructive = true,
                    confirmFirst = true,
                    onClick = { closeAndRun(EditNoteBottomSheetAction.TRASH) },
                )
            }
        }
    }
}

/**
 * The quick actions, in order: find, select all, copy as Markdown, duplicate, export as Markdown, export
 * as PDF and print, archive. Pinning is a list matter: it lives in the notes list's sheet only.
 */
@Composable
private fun editorTiles(rows: EditorRows, run: (EditNoteBottomSheetAction) -> Unit): List<SheetTile> = buildList {
    add(SheetTile(stringResource(Res.string.find_in_note), EnclyIcons.Search) { run(EditNoteBottomSheetAction.FIND) })
    if (rows.canSelect) {
        add(
            SheetTile(stringResource(Res.string.select_all), EnclyIcons.Checklist) {
                run(EditNoteBottomSheetAction.SELECT_ALL)
            },
        )
    }
    if (rows.canCopy) {
        add(
            SheetTile(stringResource(Res.string.markdown_copy), EnclyIcons.Copy) {
                run(EditNoteBottomSheetAction.COPY_MARKDOWN)
            },
        )
    }
    add(
        SheetTile(stringResource(Res.string.duplicate), EnclyIcons.Duplicate) {
            run(EditNoteBottomSheetAction.DUPLICATE)
        },
    )
    add(
        SheetTile(stringResource(Res.string.markdown_export), EnclyIcons.Upload) {
            run(EditNoteBottomSheetAction.EXPORT_MARKDOWN)
        },
    )
    if (rows.documents) {
        add(
            SheetTile(stringResource(Res.string.note_export_pdf), EnclyIcons.Paper) {
                run(EditNoteBottomSheetAction.EXPORT_PDF)
            },
        )
        add(
            SheetTile(stringResource(Res.string.note_print), EnclyDesktopIcons.Print) {
                run(EditNoteBottomSheetAction.PRINT)
            },
        )
    }
    if (rows.archived) {
        add(
            SheetTile(stringResource(Res.string.note_unarchive), EnclyIcons.Unarchive) {
                run(EditNoteBottomSheetAction.UNARCHIVE)
            },
        )
    } else {
        add(
            SheetTile(stringResource(Res.string.note_archive), EnclyIcons.Archive) {
                run(EditNoteBottomSheetAction.ARCHIVE)
            },
        )
    }
}

/** Text size and line spacing, each with its reset button at the end, then the colour swatches. */
@Composable
private fun ColumnScope.NoteLookControls(look: NoteLook) {
    val actions = look.actions
    ResettableRow(canReset = look.override.size != null, onReset = actions.onDefaultSize) {
        FontSizeSlider(currentSize = look.fontSize, onSizeChange = actions.onSize, modifier = Modifier.weight(1f))
    }
    ResettableRow(canReset = look.override.spacing != null, onReset = actions.onDefaultSpacing) {
        NoteLineSpacingSlider(
            selected = look.lineSpacing,
            onSelect = actions.onSpacing,
            modifier = Modifier.weight(1f),
        )
    }
    EnclyNoteColorPicker(
        selected = look.color,
        onSelect = actions.onColor,
        modifier = Modifier.bleed(EnclyTheme.spacing.gutter),
        contentPadding = PaddingValues(horizontal = EnclyTheme.spacing.gutter),
    )
}

/**
 * A control with "use default" at its end. The button is always laid out and only disabled while the
 * note follows the default, so the row is the same size either way.
 */
@Composable
private fun ResettableRow(canReset: Boolean, onReset: () -> Unit, control: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        control()
        IconWithTooltip(
            icon = EnclyIcons.Undo,
            label = stringResource(Res.string.note_text_use_default),
            onClick = onReset,
            enabled = canReset,
        )
    }
}
