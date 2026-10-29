package com.pasich.encly.presentation.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import com.pasich.encly.ui.theme.EnclyTheme

/** How long the next step takes to slide into place. */
private const val STEP_ANIMATION_MS = 250

/**
 * The sub-tasks under a task's [EnclyTaskRow]: [collapsed] (its next step, or nothing) while
 * [expanded] (an `updateTransition` of the open state) is false, the whole tree ([content]) while
 * it is true. They cross-fade while the height follows. While it moves either way
 * `currentState || targetState` is true, so the task row can keep its connector until the
 * tree has fully closed.
 */
@Composable
fun EnclySubtaskTree(
    expanded: Transition<Boolean>,
    collapsed: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    expanded.AnimatedContent(
        modifier = modifier,
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            fadeIn(tween(TREE_ANIMATION_MS)) togetherWith fadeOut(tween(TREE_ANIMATION_MS)) using
                SizeTransform(clip = true) { _, _ -> tween(TREE_ANIMATION_MS) }
        },
    ) { open ->
        Column { if (open) content() else collapsed() }
    }
}

/**
 * The next-step leaf as it moves on: when [step] (an `updateTransition` of the shown sub-task's
 * id) changes, the new one slides up into place while the old one leaves upwards. Instant when
 * not [animate] (animations turned off).
 */
@Composable
fun EnclyStepTransition(
    step: Transition<Long>,
    animate: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Long) -> Unit,
) {
    step.AnimatedContent(
        modifier = modifier,
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            if (!animate) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (slideInVertically(tween(STEP_ANIMATION_MS)) { it } + fadeIn(tween(STEP_ANIMATION_MS))) togetherWith
                    (slideOutVertically(tween(STEP_ANIMATION_MS)) { -it } + fadeOut(tween(STEP_ANIMATION_MS))) using
                    SizeTransform(clip = true)
            }
        },
    ) { id -> content(id) }
}

/**
 * A sub-task in its task's tree: a [CheckboxSize.SMALL] checkbox and a bodyMedium title, struck
 * through and muted when done, and an optional [trailing] action. [dimmed] mutes the title of a
 * done task's sub-task. The checkbox announces [checkboxDescription] (the title by default);
 * [onClick] (the title) is announced with [onClickLabel], and the title carries
 * [customActions] (such as Move up/down), where TalkBack offers them. While [dragging] the row
 * is lifted on `surfaceContainerHigh` and leaves its connector behind.
 */
@Suppress("LongParameterList") // A row's content, state and callbacks, like EnclyTaskRow.
@Composable
fun EnclySubtaskRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    checkboxDescription: String = title,
    customActions: List<CustomAccessibilityAction> = emptyList(),
    dragging: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = EnclyTheme.spacing
    SubtaskTreeRow(
        isLast = isLast,
        connector = !dragging,
        modifier = modifier.then(
            if (dragging) Modifier.background(colors.surfaceContainerHigh, RoundedCornerShape(spacing.s)) else Modifier,
        ),
    ) {
        EnclyCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            size = CheckboxSize.SMALL,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = checkboxDescription },
        )
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = spacing.minTouchTarget)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (customActions.isNotEmpty()) {
                        // Same node as the clickable title above: one semantics node per layout node.
                        Modifier.semantics { this.customActions = customActions }
                    } else {
                        Modifier
                    },
                )
                .padding(start = spacing.xxs, end = spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                ),
                color = if (checked || dimmed) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

/** The plus of a new sub-task, in the tree's checkbox column: 20 dp, `primary`. Decorative. */
@Composable
fun EnclySubtaskAddIcon(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val tint = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(EnclyTheme.spacing.minTouchTarget)) {
        Icon(
            EnclyIcons.Plus,
            contentDescription = null,
            tint = if (enabled) tint else tint.disabled(),
            modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
        )
    }
}

/**
 * The add leaf closing a task's tree (└): [EnclySubtaskAddIcon] and [text] in labelLarge
 * `primary`, announced as [description] (e.g. "Add sub-task" for a "Sub-task" label), and an
 * optional [trailing] action. The row past the indent is the tap target.
 */
@Composable
fun EnclyAddSubtaskButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = text,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val color = MaterialTheme.colorScheme.primary
    SubtaskTreeRow(isLast = true, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = EnclyTheme.spacing.minTouchTarget)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description },
        ) {
            EnclySubtaskAddIcon(enabled = enabled)
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) color else color.disabled(),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = EnclyTheme.spacing.xxs, end = EnclyTheme.spacing.xs),
            )
        }
        trailing?.invoke()
    }
}

/**
 * A sub-task being typed in its task's tree (├, or └ when [isLast]): [leading] in the checkbox
 * column (the sub-task's checkbox, or [EnclySubtaskAddIcon] for a new one), a single-line
 * bodyMedium field underlined in `primary`, and an optional [trailing] action. [label] is the
 * placeholder and the field's spoken name. The keyboard is the one of the task sheet's fields,
 * with a Done key ([onDone]). [fieldModifier] reaches the text field itself (focus).
 */
@Suppress("LongParameterList") // A field's value, callbacks and slots, like EnclyTextField.
@Composable
fun EnclySubtaskField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isLast: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    done: Boolean = false,
    leading: @Composable () -> Unit = { EnclySubtaskAddIcon() },
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = EnclyTheme.spacing
    val style = MaterialTheme.typography.bodyMedium.copy(
        color = if (done) colors.onSurfaceVariant else colors.onSurface,
        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
    )
    SubtaskTreeRow(isLast = isLast, modifier = modifier) {
        leading()
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .weight(1f)
                .then(fieldModifier)
                .semantics { contentDescription = label },
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .heightIn(min = spacing.minTouchTarget)
                        .padding(start = spacing.xxs, end = spacing.xs),
                ) {
                    Box(modifier = Modifier.fillMaxWidth().underline(colors.primary).padding(vertical = spacing.xxs)) {
                        if (value.isEmpty()) {
                            Text(text = label, style = style.copy(color = colors.onSurfaceVariant), maxLines = 1)
                        }
                        innerTextField()
                    }
                }
            },
        )
        trailing?.invoke()
    }
}

/**
 * One row of a sub-task tree: at least a touch target tall, indented by one touch target, with
 * the decorative [connector] (├, or └ when [isLast]) in that indent.
 */
@Composable
private fun SubtaskTreeRow(
    isLast: Boolean,
    modifier: Modifier = Modifier,
    connector: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val spacing = EnclyTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minTouchTarget)
            .then(
                if (connector) {
                    Modifier.subtaskConnector(isLast = isLast, color = MaterialTheme.colorScheme.outlineVariant)
                } else {
                    Modifier
                },
            )
            .padding(start = spacing.minTouchTarget),
        content = content,
    )
}

/** A [EnclyTheme.spacing] `stroke` line under the content, edge to edge. */
@Composable
private fun Modifier.underline(color: Color): Modifier {
    val stroke = EnclyTheme.spacing.stroke
    return drawBehind {
        val y = size.height - stroke.toPx() / 2
        drawLine(color, Offset(0f, y), Offset(size.width, y), stroke.toPx())
    }
}

/**
 * The tree connector behind a sub-task tree row: a vertical line under the parent's checkbox
 * centre (half a touch target in from the start edge) through the row, or down to its middle on
 * the last row, and an elbow to just before the small checkbox. Drawn only: it has no semantics.
 * Mirrored in RTL.
 */
@Composable
private fun Modifier.subtaskConnector(isLast: Boolean, color: Color): Modifier {
    val spacing = EnclyTheme.spacing
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return drawBehind {
        val stroke = spacing.hairline.toPx()
        val target = spacing.minTouchTarget.toPx()
        val trunk = target / 2
        // The small checkbox is centred in its own touch target, which starts one target in.
        val elbowEnd = target + (target - CheckboxSize.SMALL.size.toPx()) / 2 - spacing.xxs.toPx()
        val middle = size.height / 2
        fun x(fromStart: Float) = if (rtl) size.width - fromStart else fromStart
        drawLine(color, Offset(x(trunk), 0f), Offset(x(trunk), if (isLast) middle else size.height), stroke)
        drawLine(color, Offset(x(trunk), middle), Offset(x(elbowEnd), middle), stroke)
    }
}
