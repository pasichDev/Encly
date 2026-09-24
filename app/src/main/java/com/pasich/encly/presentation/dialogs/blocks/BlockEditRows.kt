package com.pasich.encly.presentation.dialogs.blocks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclySheetRow

/** Move up, move down and delete (confirmed by a second tap), shared by the block sheets. */
@Composable
internal fun BlockEditRows(settings: SettingsBlockDialog, onAction: (ActionBlockDialog) -> Unit) {
    EnclySheetRow(
        title = stringResource(R.string.block_move_up),
        icon = Lucide.ArrowUp,
        enabled = settings.blockMove != 1,
        onClick = { onAction(ActionBlockDialog.Move(1)) },
    )
    EnclySheetRow(
        title = stringResource(R.string.block_move_down),
        icon = Lucide.ArrowDown,
        enabled = settings.blockMove != 2,
        onClick = { onAction(ActionBlockDialog.Move(2)) },
    )
    EnclySheetRow(
        title = stringResource(id = R.string.delete_block),
        icon = Lucide.Trash2,
        destructive = true,
        confirmFirst = true,
        onClick = { onAction(ActionBlockDialog.Delete) },
    )
}
