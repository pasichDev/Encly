package com.pasich.encly.presentation.dialogs

import androidx.compose.runtime.Composable
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.delete
import com.pasich.encly.core.resources.delete_tag
import com.pasich.encly.core.resources.delete_tag_desrpt
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import org.jetbrains.compose.resources.stringResource

@Composable
fun RequestDeleteTagsDialog(showDialog: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (showDialog) {
        EnclyDialog(
            title = stringResource(Res.string.delete_tag),
            text = stringResource(Res.string.delete_tag_desrpt),
            onDismissRequest = onDismiss,
            confirm = DialogAction(
                text = stringResource(Res.string.delete),
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                destructive = true,
            ),
            dismiss = DialogAction(stringResource(Res.string.cancel), onDismiss),
        )
    }
}
