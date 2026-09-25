package com.pasich.encly.presentation.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyDialog

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
            confirm = DialogAction(stringResource(R.string.dialog_confirm), onConfirm, destructive = destructive),
            dismiss = DialogAction(stringResource(R.string.dialog_dismiss), onDismiss),
        )
    }
}
