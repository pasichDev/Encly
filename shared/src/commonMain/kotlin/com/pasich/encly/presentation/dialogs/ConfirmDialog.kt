package com.pasich.encly.presentation.dialogs

import androidx.compose.runtime.Composable
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.dialog_confirm
import com.pasich.encly.core.resources.dialog_dismiss
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog
import org.jetbrains.compose.resources.stringResource

/** A yes/no confirmation; [destructive] shows the confirm action in `error`. */
@Composable
fun ConfirmDialog(
    isVisible: Boolean,
    titleText: String,
    messageText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    if (isVisible) {
        EnclyDialog(
            title = titleText,
            text = messageText,
            onDismissRequest = onDismiss,
            confirm = DialogAction(stringResource(Res.string.dialog_confirm), onConfirm, destructive = destructive),
            dismiss = DialogAction(stringResource(Res.string.dialog_dismiss), onDismiss),
        )
    }
}
