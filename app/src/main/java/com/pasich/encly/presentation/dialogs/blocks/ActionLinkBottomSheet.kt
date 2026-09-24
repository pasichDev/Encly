package com.pasich.encly.presentation.dialogs.blocks

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.designsystem.EnclyGroupDivider
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclySheetRow
import com.pasich.encly.presentation.editor.blocks.LinkBlock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLinkBottomSheet(
    settings: SettingsBlockDialog,
    sheetState: SheetState,
    onAction: (ActionBlockDialog) -> Unit,
    onDismiss: () -> Unit,
) {
    if (settings.isBottomSheetVisible) {
        EnclyBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
            val block = settings.block
            if (block is Block.LinkBlock) {
                val urlModel by block.block.collectAsState()
                if (!urlModel.isError) {
                    LinkBlock(block, blockActions = null, onClick = {})
                }
            }
            EnclySheetRow(
                title = stringResource(R.string.link_open),
                icon = EnclyIcons.External,
                onClick = { onAction(ActionBlockDialog.OpenLink) },
            )
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
