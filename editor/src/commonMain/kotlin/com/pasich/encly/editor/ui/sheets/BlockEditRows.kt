package com.pasich.encly.editor.ui.sheets

import androidx.compose.runtime.Composable
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.theme.LocalEditorTheme
import com.pasich.encly.editor.ui.components.EditorSheetRow

/** Move up, move down, duplicate and delete (confirmed by a second tap), shared by the block sheets. */
@Composable
internal fun BlockEditRows(settings: SettingsBlockDialog, onAction: (ActionBlockDialog) -> Unit) {
    val strings = LocalEditorStrings.current
    val icons = LocalEditorTheme.current.icons
    EditorSheetRow(
        title = strings.text(EditorString.BLOCK_MOVE_UP),
        icon = icons.arrowUp,
        enabled = settings.blockMove != 1,
        onClick = { onAction(ActionBlockDialog.Move(1)) },
    )
    EditorSheetRow(
        title = strings.text(EditorString.BLOCK_MOVE_DOWN),
        icon = icons.arrowDown,
        enabled = settings.blockMove != 2,
        onClick = { onAction(ActionBlockDialog.Move(2)) },
    )
    EditorSheetRow(
        title = strings.text(EditorString.BLOCK_DUPLICATE),
        icon = icons.duplicate,
        onClick = { onAction(ActionBlockDialog.Duplicate) },
    )
    EditorSheetRow(
        title = strings.text(EditorString.BLOCK_DELETE),
        icon = icons.trash,
        destructive = true,
        confirmFirst = true,
        onClick = { onAction(ActionBlockDialog.Delete) },
    )
}
