package com.pasich.encly.presentation.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R

@Composable
fun ConfirmDialog(
    isVisible: Boolean,
    titleText: String,
    messageText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = titleText) },
            text = { Text(text = messageText) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(text = stringResource(R.string.dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.dialog_dismiss))
                }
            },
        )
    }
}
