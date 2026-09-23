package com.pasich.encly.presentation.dialogs

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class SnackType {
    ERROR,
    SUCCESS,
}

@Composable
fun InfoSnackbar(snackbarData: SnackbarData, modifier: Modifier = Modifier, snackType: SnackType = SnackType.SUCCESS) {
    val message = snackbarData.visuals.message

    Snackbar(
        modifier = modifier.padding(16.dp),
        containerColor = when (snackType) {
            SnackType.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
            SnackType.ERROR -> MaterialTheme.colorScheme.errorContainer
        },
        contentColor = when (snackType) {
            SnackType.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
            SnackType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        },
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
