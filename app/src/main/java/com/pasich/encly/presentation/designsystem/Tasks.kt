package com.pasich.encly.presentation.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.pasich.encly.ui.theme.EnclyTheme

private const val CONTROL_ANIMATION_MS = 150

/** Checkbox sizes: the task widget (22, radius 6) and full lists and the editor (24, radius 7). */
enum class CheckboxSize(val size: Dp, val radius: Dp) {
    SMALL(22.dp, 6.dp),
    LARGE(24.dp, 7.dp),
}

/**
 * A checkbox (design spec §3.3): unchecked is a 2 dp `outline` square, checked fills `primary`
 * with an `onPrimary` check. The touch target is padded to 48 dp.
 */
@Composable
fun EnclyCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    size: CheckboxSize = CheckboxSize.LARGE,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(size.radius)
    val fill by animateColorAsState(
        targetValue = if (checked) colors.primary else Color.Transparent,
        animationSpec = tween(CONTROL_ANIMATION_MS),
        label = "checkbox",
    )
    val toggle = if (onCheckedChange == null) {
        Modifier
    } else {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    }
    Box(contentAlignment = Alignment.Center, modifier = modifier.then(toggle)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size.size)
                .background(if (enabled) fill else fill.disabled(), shape)
                .then(
                    if (checked) {
                        Modifier
                    } else {
                        Modifier.border(EnclyTheme.spacing.stroke, colors.outline, shape)
                    },
                ),
        ) {
            if (checked) {
                Icon(
                    Lucide.Check,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * A task row: checkbox, title (struck through and muted when done), an optional description, a
 * [meta] line (e.g. when it was completed) and the priority label in the data style
 * ([priorityEmphasis] tints it `error`). [large] uses the 24 dp checkbox and bodyLarge of full
 * lists; otherwise the compact widget row.
 */
@Composable
fun EnclyTaskRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    meta: String? = null,
    priority: String? = null,
    priorityEmphasis: Boolean = false,
    large: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val done = if (checked) TextDecoration.LineThrough else TextDecoration.None
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.textButtonHeight)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier),
    ) {
        EnclyCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            size = if (large) CheckboxSize.LARGE else CheckboxSize.SMALL,
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.textGap)) {
            Text(
                text = title,
                style = (if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium)
                    .copy(textDecoration = done),
                color = if (checked) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = done),
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (meta != null) {
                Text(text = meta, style = EnclyTheme.typography.dataSmall, color = colors.onSurfaceVariant)
            }
        }
        if (priority != null) {
            Text(
                text = priority,
                style = EnclyTheme.typography.dataSmall,
                color = if (priorityEmphasis && !checked) colors.error else colors.onSurfaceVariant,
            )
        }
    }
}

/** A continuous 4 dp progress track: `outlineVariant` behind a `primary` fill. */
@Composable
fun EnclyProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.primary, shape),
        )
    }
}
