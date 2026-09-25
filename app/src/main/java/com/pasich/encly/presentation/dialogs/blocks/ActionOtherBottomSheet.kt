package com.pasich.encly.presentation.dialogs.blocks

import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionOtherBottomSheet(
    settings: SettingsBlockDialog,
    sheetState: SheetState,
    onAction: (ActionBlockDialog) -> Unit,
    onDismiss: () -> Unit,
) {
    if (settings.isBottomSheetVisible) {
        EnclyBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            title = stringResource(settings.block.describe()),
        ) {
            // Every action here changes the note: a read-only editor offers none.
            if (settings.canEdit) BlockEditRows(settings, onAction)
        }
    }
}

/** Human-readable block type, as a string resource. */
@StringRes
fun Block.describe(): Int = when (this) {
    is Block.TextBlock -> R.string.text
    is Block.QuoteBlock -> R.string.quote
    is Block.ListBlock -> R.string.block_list
    is Block.HBlock -> R.string.block_heading
    is Block.LinkBlock -> R.string.block_link
    is Block.SeparatorBlock -> R.string.block_separator
}
