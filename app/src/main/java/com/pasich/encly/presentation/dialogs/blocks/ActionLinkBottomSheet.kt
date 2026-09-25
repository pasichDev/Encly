package com.pasich.encly.presentation.dialogs.blocks

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.presentation.editor.blocks.LinkBlock
import com.pasich.encly.presentation.editor.blocks.isOpenableLink

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
        EnclyBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
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
                EnclySheetRow(
                    title = stringResource(R.string.link_open),
                    icon = EnclyIcons.External,
                    onClick = { onAction(ActionBlockDialog.OpenLink) },
                )
            }
            EnclySheetRow(
                title = stringResource(R.string.link_copy),
                icon = EnclyIcons.Copy,
                onClick = { onAction(ActionBlockDialog.CopyLink) },
            )
            if (settings.canEdit) {
                EnclySheetRow(
                    title = stringResource(R.string.link_edit),
                    icon = EnclyIcons.Edit,
                    onClick = { onAction(ActionBlockDialog.EditLink) },
                )
                EnclyGroupDivider()
                BlockEditRows(settings, onAction)
            }
        }
    }
}
