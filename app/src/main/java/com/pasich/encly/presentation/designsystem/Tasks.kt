package com.pasich.encly.presentation.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.EnclyTheme

private const val CONTROL_ANIMATION_MS = 150

/** How long a sub-task tree takes to open or close, and its chevron to turn. */
internal const val TREE_ANIMATION_MS = 200
private const val CHEVRON_TURN = 180f

private val SEGMENT_BAR_MAX_WIDTH = 160.dp
private val SEGMENT_GAP = 3.dp
private val SEGMENT_MIN_GAP = 1.dp
private val SEGMENT_DONE_HEIGHT = 4.dp
private val SEGMENT_OPEN_HEIGHT = 2.dp

/** Up to this many segments keep the full gap; more narrow it. */
private const val SEGMENT_FULL_GAP_COUNT = 12

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
                    EnclyIcons.Check,
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
 * [meta] line (e.g. when it was completed), the priority label in the data style
 * ([priorityEmphasis] tints it `error`) and an optional [trailing] slot (the list's chevron and
 * edit button) and, with [progress] (each sub-task's done state), an [EnclySegmentBar] under the
 * meta line. [large] uses the 24 dp checkbox and bodyLarge of full lists; otherwise the compact
 * widget row. With [onClick] the rest of the row is one tap target, announced with
 * [onClickLabel]; the checkbox announces [title]. [connectorBelow] draws the start of the
 * sub-task tree from under the checkbox to the row's bottom edge, where the first tree row
 * ([EnclySubtaskRow], [EnclySubtaskField], [EnclyAddSubtaskButton]) continues it.
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
    connectorBelow: Boolean = false,
    progress: List<Boolean>? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val checkboxSize = if (large) CheckboxSize.LARGE else CheckboxSize.SMALL
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.textButtonHeight)
            .then(
                if (connectorBelow) Modifier.connectorBelowCheckbox(checkboxSize, colors.outlineVariant) else Modifier,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClickLabel = onClickLabel, onClick = onClick)
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
            progress = progress,
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

/** The text column of [EnclyTaskRow]: title, description, meta line and segment bar. */
@Suppress("LongParameterList") // EnclyTaskRow's text, passed through.
@Composable
private fun TaskRowText(
    title: String,
    checked: Boolean,
    large: Boolean,
    description: String?,
    meta: String?,
    progress: List<Boolean>?,
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
        if (!progress.isNullOrEmpty()) {
            EnclySegmentBar(done = progress, modifier = Modifier.padding(top = EnclyTheme.spacing.xxs))
        }
    }
}

/**
 * The chevron button of a sub-task tree: an `onSurfaceVariant` arrow on a 48 dp target that
 * points down, and turns 180° (animated) when [expanded].
 */
@Composable
fun EnclyExpandButton(
    expanded: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val turn by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_TURN else 0f,
        animationSpec = tween(TREE_ANIMATION_MS),
        label = "chevron",
    )
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(
            EnclyIcons.ChevronDown,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.disabled(),
            modifier = Modifier
                .size(EnclyTheme.spacing.iconSmall)
                .rotate(turn),
        )
    }
}

/** A row's own action (edit, delete): a 20 dp [icon] in `onSurfaceVariant` on a 48 dp target. */
@Composable
fun EnclyRowIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.disabled(),
            modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
        )
    }
}

/**
 * Sub-task progress on a task row: one rounded segment per sub-task, 3 dp apart, at most 160 dp
 * wide. A done one is `primary` and 4 dp tall, an open one `outlineVariant` and 2 dp: the height
 * tells them apart where the palette's primary is low in chroma. Past a dozen segments the gaps
 * narrow so they still fit. Starts at the start edge (mirrored in RTL). Decorative: the row
 * announces the count.
 */
@Composable
fun EnclySegmentBar(done: List<Boolean>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier = modifier
            .widthIn(max = SEGMENT_BAR_MAX_WIDTH)
            .fillMaxWidth()
            .height(SEGMENT_DONE_HEIGHT),
    ) {
        if (done.isEmpty()) return@Canvas
        val count = done.size
        val gap = (SEGMENT_GAP * minOf(1f, SEGMENT_FULL_GAP_COUNT.toFloat() / count)).toPx()
            .coerceAtLeast(SEGMENT_MIN_GAP.toPx())
        val width = ((size.width - gap * (count - 1)) / count).coerceAtLeast(SEGMENT_MIN_GAP.toPx())
        done.forEachIndexed { index, isDone ->
            val height = (if (isDone) SEGMENT_DONE_HEIGHT else SEGMENT_OPEN_HEIGHT).toPx()
            val start = index * (width + gap)
            val left = if (rtl) size.width - start - width else start
            drawRoundRect(
                color = if (isDone) colors.primary else colors.outlineVariant,
                topLeft = Offset(left, (size.height - height) / 2),
                size = Size(width, height),
                cornerRadius = CornerRadius(height / 2),
            )
        }
    }
}

/**
 * The top of the sub-task tree on a task row: the same line as [subtaskConnector], from the
 * bottom of the (vertically centred) checkbox down to the row's bottom edge. No semantics.
 */
@Composable
private fun Modifier.connectorBelowCheckbox(checkbox: CheckboxSize, color: Color): Modifier {
    val spacing = EnclyTheme.spacing
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return drawBehind {
        val trunk = spacing.minTouchTarget.toPx() / 2
        val x = if (rtl) size.width - trunk else trunk
        val top = size.height / 2 + checkbox.size.toPx() / 2
        drawLine(color, Offset(x, top), Offset(x, size.height), spacing.hairline.toPx())
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
