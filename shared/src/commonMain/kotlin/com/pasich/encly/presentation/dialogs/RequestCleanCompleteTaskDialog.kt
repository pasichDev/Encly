package com.pasich.encly.presentation.dialogs

import androidx.compose.runtime.Composable
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.clear
import com.pasich.encly.core.resources.task_clear_completed_message
import com.pasich.encly.core.resources.task_clear_completed_title
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import org.jetbrains.compose.resources.stringResource

@Composable
fun RequestCleanCompleteTaskDialog(showDialog: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (showDialog) {
        EnclyDialog(
            title = stringResource(Res.string.task_clear_completed_title),
            text = stringResource(Res.string.task_clear_completed_message),
            onDismissRequest = onDismiss,
            confirm = DialogAction(
                text = stringResource(Res.string.clear),
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
