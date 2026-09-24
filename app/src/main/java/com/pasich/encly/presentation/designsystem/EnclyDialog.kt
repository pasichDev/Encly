package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * The Encly dialog (design spec §3.3, dialogs): `surfaceContainerHigh`, radius 28, the title in
 * the display family at 24/30 and the body in bodyMedium `onSurfaceVariant`. Give either [text]
 * or [content]; both stack when given.
 */
@Composable
fun EnclyDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirm: DialogAction,
    modifier: Modifier = Modifier,
    dismiss: DialogAction? = null,
    text: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = colors.surfaceContainerHigh,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        tonalElevation = 0.dp,
        title = { Text(text = title, style = EnclyTheme.typography.dialogTitle) },
        text = if (text == null && content == null) {
            null
        } else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
                    if (text != null) Text(text = text, style = MaterialTheme.typography.bodyMedium)
                    content?.invoke(this)
                }
            }
        },
        confirmButton = { DialogButton(confirm) },
        dismissButton = dismiss?.let { action -> { DialogButton(action) } },
    )
}

@Composable
private fun DialogButton(action: DialogAction) {
    val colors = MaterialTheme.colorScheme
    TextButton(
        onClick = action.onClick,
        enabled = action.enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (action.destructive) colors.error else colors.primary,
        ),
    ) {
        Text(text = action.text, style = MaterialTheme.typography.labelLarge)
    }
}
