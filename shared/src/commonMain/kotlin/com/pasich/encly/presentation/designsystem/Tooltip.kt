package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified

/**
 * Shows [label] in a small tooltip when [content] (an icon-only button) is long-pressed or hovered,
 * so a user can learn what the icon does. Pass the button's own localized content description: the
 * tooltip is drawn in its own popup and is not part of the anchor's semantics, so TalkBack still
 * announces the label once. Nothing is added for a blank [label] (a decorative icon). [modifier]
 * lays out the anchor, so pass the button's size or weight here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnclyTooltip(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (label.isBlank()) {
        Box(modifier) { content() }
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}

/**
 * An [IconButton] with its [label] as content description and long-press tooltip. A null [tint] keeps the
 * button's own content colour; an unspecified [iconSize] keeps the icon's 24 dp.
 */
@Composable
fun IconWithTooltip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    iconSize: Dp = Dp.Unspecified,
) {
    EnclyTooltip(label) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier.enclyInteractiveRound(enabled)) {
            val iconModifier = if (iconSize.isSpecified) Modifier.size(iconSize) else Modifier
            if (tint == null) {
                Icon(icon, contentDescription = label, modifier = iconModifier)
            } else {
                Icon(icon, contentDescription = label, tint = tint, modifier = iconModifier)
            }
        }
    }
}
