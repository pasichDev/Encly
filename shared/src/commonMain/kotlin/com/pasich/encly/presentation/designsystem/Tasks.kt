// The checkbox, the task row and the progress track, not only the enum of checkbox sizes.
@file:Suppress("MatchingDeclarationName")

package com.pasich.encly.presentation.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.EnclyMotion
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * Checkbox sizes: a step under a task (18, radius 5), the editor's checklist and a task in the tasks list
 * (20, radius 6), the task widget (22, radius 6) and full lists (24, radius 7).
 */
enum class CheckboxSize(val size: Dp, val radius: Dp) {
    /** A step under a task: smaller than the task's own box, so it reads as nested. */
    STEP(18.dp, 5.dp),

    /** The editor's checklist and the tasks list: small enough to sit on a line of text. */
    COMPACT(20.dp, 6.dp),
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
    /** The square's side, when it must be smaller than [size] (a line of small text). */
    boxSize: Dp = size.size,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(minOf(size.radius, boxSize * MAX_RADIUS_FRACTION))
    val fill by animateColorAsState(
        targetValue = if (checked) colors.primary else Color.Transparent,
        animationSpec = EnclyMotion.short(),
        label = "checkbox",
    )
    val toggle = if (onCheckedChange == null) {
        Modifier
    } else {
        Modifier
            .minimumInteractiveComponentSize()
            .enclyInteractive(shape, enabled)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    }
    Box(contentAlignment = Alignment.Center, modifier = modifier.then(toggle)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(boxSize)
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
                    EnclyIcons.Check,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(minOf(16.dp, boxSize * CHECK_ICON_FRACTION)),
                )
            }
        }
    }
}

private const val CHECK_ICON_FRACTION = 0.7f
private const val MAX_RADIUS_FRACTION = 0.3f

/**
 * A task row: checkbox, title (struck through and muted when done), an optional description, a
 * [meta] line (e.g. when it was completed), the priority label in the data style
 * ([priorityEmphasis] tints it `error`) and an optional [trailing] slot. [large] uses the bodyLarge
 * of full lists; otherwise the compact widget row. With [onClick] the rest of the row is one tap
 * target, announced with [onClickLabel]; the checkbox announces [title].
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
    onClickLabel: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    // Rows of every size take the smaller box: the large one looked heavy next to the text.
    val checkboxSize = CheckboxSize.SMALL
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.textButtonHeight)
            .then(
                if (onClick != null) {
                    Modifier
                        .enclyInteractive(MaterialTheme.shapes.small, enabled)
                        .clickable(enabled = enabled, onClickLabel = onClickLabel, onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        EnclyCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            size = checkboxSize,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title },
        )
        TaskRowText(
            title = title,
            checked = checked,
            large = large,
            description = description,
            meta = meta,
            modifier = Modifier.weight(1f),
        )
        if (priority != null) {
            Text(
                text = priority,
                style = EnclyTheme.typography.dataSmall,
                color = if (priorityEmphasis && !checked) colors.error else colors.onSurfaceVariant,
            )
        }
        trailing?.invoke(this)
    }
}

/** The text column of [EnclyTaskRow]: title, description and meta line. */
@Composable
private fun TaskRowText(
    title: String,
    checked: Boolean,
    large: Boolean,
    description: String?,
    meta: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val done = if (checked) TextDecoration.LineThrough else TextDecoration.None
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.textGap)) {
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
