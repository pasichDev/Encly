package com.pasich.encly.presentation.components.appbar

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AppBarTextButton(text: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = { onClick() },
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.surfaceContainer,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.labelLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
