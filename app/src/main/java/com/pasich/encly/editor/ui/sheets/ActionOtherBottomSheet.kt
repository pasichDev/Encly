package com.pasich.encly.editor.ui.sheets

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.ui.components.EditorSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionOtherBottomSheet(
    settings: SettingsBlockDialog,
    sheetState: SheetState,
    onAction: (ActionBlockDialog) -> Unit,
    onDismiss: () -> Unit,
) {
    if (settings.isBottomSheetVisible) {
        EditorSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            title = LocalEditorStrings.current.text(settings.block.describe()),
        ) {
            // Every action here changes the note: a read-only editor offers none.
            if (settings.canEdit) BlockEditRows(settings, onAction)
        }
    }
}

/** Human-readable block type, as a string key. */
fun Block.describe(): EditorString = when (this) {
    is Block.TextBlock -> EditorString.TEXT
    is Block.QuoteBlock -> EditorString.QUOTE
    is Block.ListBlock -> EditorString.BLOCK_LIST
    is Block.HBlock -> EditorString.BLOCK_HEADING
    is Block.LinkBlock -> EditorString.BLOCK_LINK
    is Block.SeparatorBlock -> EditorString.BLOCK_SEPARATOR
}
