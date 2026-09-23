package com.pasich.encly.presentation.dialogs.blocks

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.components.custombox.ModalBoxItem
import com.pasich.encly.presentation.components.custombox.RoundPosition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionOtherBottomSheet(
    settings: SettingsBlockDialog,
    sheetState: SheetState,
    onAction: (ActionBlockDialog) -> Unit,
    onDismiss: () -> Unit,
) {
    if (settings.isBottomSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            sheetState = sheetState,
            shape = RectangleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.wrapContentHeight(),
        ) {
            Text(
                text = stringResource(settings.block.describe()),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 20.dp),
            )

            LazyColumn(
                modifier = Modifier.padding(16.dp),
            ) {
                // Every action here changes the note: a read-only editor offers none.
                if (!settings.canEdit) return@LazyColumn
                item {
                    ModalBoxItem(
                        title = stringResource(R.string.block_move_up),
                        icon = painterResource(R.drawable.ic_up),
                        roundPosition = RoundPosition.First,
                        enable = settings.blockMove != 1,
                        action = { onAction(ActionBlockDialog.Move(1)) },
                    )
                }
                item {
                    ModalBoxItem(
                        title = stringResource(R.string.block_move_down),
                        icon = painterResource(R.drawable.ic_down),
                        roundPosition = RoundPosition.Medium,
                        enable = settings.blockMove != 2,
                        action = { onAction(ActionBlockDialog.Move(2)) },
                    )
                }
                item {
                    ModalBoxItem(
                        title = stringResource(id = R.string.delete_block),
                        icon = painterResource(R.drawable.ic_to_trash),
                        roundPosition = RoundPosition.Last,
                        confirmationRequest = MaterialTheme.colorScheme.error,
                        action = { onAction(ActionBlockDialog.Delete) },
                    )
                }
            }
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
