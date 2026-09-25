package com.pasich.encly.presentation.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog

@Composable
fun RequestDeleteTagsDialog(showDialog: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (showDialog) {
        EnclyDialog(
            title = stringResource(R.string.delete_tag),
            text = stringResource(R.string.delete_tag_desrpt),
            onDismissRequest = onDismiss,
            confirm = DialogAction(
                text = stringResource(R.string.delete),
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                destructive = true,
            ),
            dismiss = DialogAction(stringResource(R.string.cancel), onDismiss),
        )
    }
}
