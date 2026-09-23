package com.pasich.encly.presentation.components.appbar

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.buttonSizeAppBar

/**
 * Toolbar icon button. A null [onPress] renders it disabled (dimmed), so callers do not pass
 * an enabled flag and a matching tint separately.
 */
@Composable
fun AppBarIconButton(
    icon: Int,
    contentDescription: String?,
    onPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    margin: Dp = 0.dp,
) {
    IconButton(
        onClick = { onPress?.invoke() },
        modifier = modifier,
        enabled = onPress != null,
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(buttonSizeAppBar).padding(margin),
            tint = if (onPress != null) {
                tint
            } else {
                MaterialTheme.colorScheme.onBackground.copy(alpha = DISABLED_ALPHA)
            },
        )
    }
}

private const val DISABLED_ALPHA = 0.5f
