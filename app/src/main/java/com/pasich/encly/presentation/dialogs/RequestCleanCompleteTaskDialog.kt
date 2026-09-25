package com.pasich.encly.presentation.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog

@Composable
fun RequestCleanCompleteTaskDialog(showDialog: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (showDialog) {
        EnclyDialog(
            title = stringResource(R.string.task_clear_completed_title),
            text = stringResource(R.string.task_clear_completed_message),
            onDismissRequest = onDismiss,
            confirm = DialogAction(
                text = stringResource(R.string.clear),
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
