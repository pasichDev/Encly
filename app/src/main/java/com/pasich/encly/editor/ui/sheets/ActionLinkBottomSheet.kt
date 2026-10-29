package com.pasich.encly.editor.ui.sheets

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.LocalEditorStrings
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.theme.LocalEditorTheme
import com.pasich.encly.editor.ui.blocks.LinkBlock
import com.pasich.encly.editor.ui.blocks.isOpenableLink
import com.pasich.encly.editor.ui.components.EditorGroupDivider
import com.pasich.encly.editor.ui.components.EditorSheet
import com.pasich.encly.editor.ui.components.EditorSheetRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLinkBottomSheet(
    settings: SettingsBlockDialog,
    sheetState: SheetState,
    onAction: (ActionBlockDialog) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (settings.isBottomSheetVisible) {
        val strings = LocalEditorStrings.current
        val icons = LocalEditorTheme.current.icons
        EditorSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
            val block = settings.block
            var openable = false
            if (block is Block.LinkBlock) {
                val urlModel by block.block.collectAsState()
                openable = isOpenableLink(urlModel.url)
                if (!urlModel.isError) {
                    LinkBlock(block, blockActions = null, onClick = {})
                }
                // The whole address, so the user sees exactly what "Open link" hands to another app.
                SelectionContainer {
                    Text(
                        text = urlModel.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // An address Encly will not open (see NoteLink.Blocked) can still be copied.
            if (openable) {
                EditorSheetRow(
                    title = strings.text(EditorString.LINK_OPEN),
                    icon = icons.external,
                    onClick = { onAction(ActionBlockDialog.OpenLink) },
                )
            }
            EditorSheetRow(
                title = strings.text(EditorString.LINK_COPY),
                icon = icons.copy,
                onClick = { onAction(ActionBlockDialog.CopyLink) },
            )
            if (settings.canEdit) {
                EditorSheetRow(
                    title = strings.text(EditorString.LINK_EDIT),
                    icon = icons.edit,
                    onClick = { onAction(ActionBlockDialog.EditLink) },
                )
                EditorGroupDivider()
                BlockEditRows(settings, onAction)
            }
        }
    }
}
