package com.pasich.encly.editor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pasich.encly.editor.theme.LocalEditorTheme

// Small controls the editor draws itself, so it needs nothing from its host's design system.

private val CheckboxRadius = 6.dp
private const val CHECKBOX_ANIMATION_MS = 150
private const val CHECK_ICON_FRACTION = 0.7f
private const val MAX_RADIUS_FRACTION = 0.3f
private val CheckIconMax = 16.dp
private val ToolSize = 44.dp
private val ToolShape = RoundedCornerShape(14.dp)
private const val DISABLED_ALPHA = 0.38f

/**
 * A checklist's box: unchecked is a 2 dp `outline` square, checked fills `primary` with an
 * `onPrimary` tick. It only draws; the list item toggles it. [boxSize] is the square's side.
 */
@Composable
fun EditorCheckbox(checked: Boolean, boxSize: Dp, modifier: Modifier = Modifier) {
    val theme = LocalEditorTheme.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(minOf(CheckboxRadius, boxSize * MAX_RADIUS_FRACTION))
    val fill by animateColorAsState(
        targetValue = if (checked) colors.primary else Color.Transparent,
        animationSpec = tween(CHECKBOX_ANIMATION_MS),
        label = "checkbox",
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(boxSize)
                .background(fill, shape)
                .then(if (checked) Modifier else Modifier.border(theme.spacing.stroke, colors.outline, shape)),
        ) {
            if (checked) {
                Icon(
                    theme.icons.check,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(minOf(CheckIconMax, boxSize * CHECK_ICON_FRACTION)),
                )
            }
        }
    }
}

/**
 * Shows [label] in a small tooltip when [content] (an icon-only button) is long-pressed or hovered,
 * so a user can learn what the icon does. [modifier] lays out the anchor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTooltip(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}

/**
 * A 44 dp icon button filled with `primary` (the "Add link" button), radius 14, with a long-press
 * tooltip of [contentDescription]. A null [onClick] shows it disabled.
 */
@Composable
fun EditorFilledToolButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val enabled = onClick != null
    EditorTooltip(contentDescription, modifier = modifier) {
        Surface(
            onClick = { onClick?.invoke() },
            enabled = enabled,
            shape = ToolShape,
            color = colors.primary,
            contentColor = if (enabled) colors.onPrimary else colors.onPrimary.copy(alpha = DISABLED_ALPHA),
            modifier = Modifier
                .size(ToolSize)
                .semantics { role = Role.Button },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(LocalEditorTheme.current.spacing.iconMedium),
                )
            }
        }
    }
}
