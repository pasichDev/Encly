package com.pasich.encly.presentation.designsystem

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * The extended FAB ("New note"): 56 dp, radius 18, `primary` / `onPrimary` (`error` / `onError`
 * when [destructive]), and the one shadow a screen has (design spec §1.4). [expanded] false
 * collapses it to the icon, which then carries [text] as its description.
 */
@Composable
fun EnclyFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val shadow = EnclyTheme.colors.shadow
    ExtendedFloatingActionButton(
        text = { Text(text = text, style = MaterialTheme.typography.labelLarge) },
        icon = { Icon(icon, contentDescription = if (expanded) null else text) },
        onClick = onClick,
        expanded = expanded,
        shape = shape,
        containerColor = if (destructive) colors.error else colors.primary,
        contentColor = if (destructive) colors.onError else colors.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        modifier = modifier.shadow(ShadowElevation, shape, ambientColor = shadow, spotColor = shadow),
    )
}
