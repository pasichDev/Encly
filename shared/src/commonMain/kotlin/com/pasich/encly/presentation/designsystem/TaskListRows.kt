package com.pasich.encly.presentation.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.EnclyMotion
import com.pasich.encly.ui.theme.EnclyTheme

/** A checkbox's touch target; its box is drawn in the middle of it. */
private val CheckTarget = 48.dp

/** Before a task's checkbox target, so that the 20 dp box itself starts on the screen's 24 dp gutter. */
private val TaskRowStart = 10.dp

/** Where a task's text starts, and so where its steps' boxes start: steps read as nested under the title. */
private val StepStart = TaskRowStart + CheckTarget

/** After the last thing of a row: with [ChipEndPad] inside the count button, its content ends on the gutter. */
private val RowEnd = 12.dp
private val ChipEndPad = 12.dp
private val StepTextGap = 10.dp

/** Above a title: half of what a 48 dp target leaves around a 24 dp line of text. */
private val TitleInset = 12.dp
private val SectionMinHeight = 40.dp
private val ChipHeight = 28.dp
private val ChevronSize = 16.dp
private const val CHEVRON_TURN = 180f

/** A section of the tasks list: its [title] as an overline, with an optional [action] at the end. */
@Composable
fun EnclyTaskSection(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SectionMinHeight)
            .padding(start = EnclyTheme.spacing.gutter, end = RowEnd),
    ) {
        SectionOverline(text = title, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

/** What a task row shows at its end: a task's priority ([emphasis] tints it `error`), or when it was done. */
class TaskRowEnd(val text: String, val emphasis: Boolean = false)

/**
 * The count button of a task that has steps: [count] ("1 / 4") and a chevron that points up while the
 * steps are [open]. [onToggle] opens or folds them; [label] is what the button does, for TalkBack.
 */
class TaskStepsToggle(val count: String, val open: Boolean, val label: String, val onToggle: () -> Unit)

/**
 * A task in the tasks list: a 20 dp checkbox, the title (struck through and muted when [checked]) with
 * [end] and the [steps] count button on its line, and an optional one-line [description] under it. A tap
 * on the row beside the checkbox and the count button runs [onClick], announced as [onClickLabel].
 */
@Suppress("LongParameterList") // A design-system row: independent optional slots.
@Composable
fun EnclyTaskListRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    end: TaskRowEnd? = null,
    steps: TaskStepsToggle? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClickLabel = onClickLabel, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(start = TaskRowStart, end = if (steps == null) RowEnd + ChipEndPad else RowEnd),
    ) {
        EnclyCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            size = CheckboxSize.COMPACT,
            enabled = enabled,
            modifier = Modifier.size(CheckTarget).semantics { contentDescription = title },
        )
        // The text starts where a one-line title is centred on the checkbox; a second line or a description
        // goes on under it, and the priority and the count stay on the first line.
        Column(
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.textGap),
            modifier = Modifier.weight(1f).heightIn(min = CheckTarget).padding(vertical = TitleInset),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                ),
                color = if (checked) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!description.isNullOrBlank() && !checked) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (end != null) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = CheckTarget)) {
                Text(
                    text = end.text,
                    style = EnclyTheme.typography.dataSmall,
                    color = if (end.emphasis && !checked) colors.error else colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = EnclyTheme.spacing.xs),
                )
            }
        }
        if (steps != null) StepsToggleButton(steps, enabled)
    }
}

/** The count of a task's steps as a small pill with a chevron, on a 48 dp target. */
@Composable
private fun StepsToggleButton(toggle: TaskStepsToggle, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    val turn by animateFloatAsState(if (toggle.open) CHEVRON_TURN else 0f, EnclyMotion.medium(), label = "steps")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = CheckTarget)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = toggle.label, onClick = toggle.onToggle)
            .padding(start = EnclyTheme.spacing.xs, end = ChipEndPad)
            .semantics { stateDescription = toggle.count },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
            modifier = Modifier
                .heightIn(min = ChipHeight)
                .background(colors.surfaceContainerHigh, CircleShape)
                .padding(start = EnclyTheme.spacing.s, end = EnclyTheme.spacing.xs),
        ) {
            Text(text = toggle.count, style = EnclyTheme.typography.dataSmall, color = colors.onSurfaceVariant)
            Icon(
                EnclyIcons.ChevronDown,
                contentDescription = toggle.label,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(ChevronSize).rotate(turn),
            )
        }
    }
}

/** A step's row: indented so that what leads it starts under the task's title. */
@Composable
private fun StepRowFrame(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CheckTarget)
            // The 18 dp box is centred in its 48 dp target: the target starts that much before the text column.
            .padding(start = StepStart - (CheckTarget - CheckboxSize.STEP.size) / 2, end = RowEnd + ChipEndPad),
        content = content,
    )
}

/** What leads a step's row: its checkbox, or a plus for a step that is not there yet. */
@Composable
private fun StepLead(title: String, checked: Boolean?, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.textAfterBox().size(CheckTarget)) {
        if (checked != null) {
            EnclyCheckbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                size = CheckboxSize.STEP,
                enabled = enabled,
                modifier = Modifier.size(CheckTarget).semantics { contentDescription = title },
            )
        } else {
            Icon(
                EnclyIcons.Plus,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(CheckboxSize.STEP.size),
            )
        }
    }
}

/** The lead's 48 dp target reaches past its box: what follows starts a fixed gap after the box itself. */
private fun Modifier.textAfterBox(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val trim = ((CheckTarget - CheckboxSize.STEP.size) / 2 - StepTextGap).roundToPx()
    layout(placeable.width - trim, placeable.height) { placeable.placeRelative(0, 0) }
}

/**
 * A step (sub-task) under a task: an 18 dp checkbox under the task's title and the step's title on one
 * line, struck through when [checked]. A long press on the title runs [onLongPress] (announced as
 * [onLongPressLabel]); [actions] are what TalkBack offers on it.
 */
@Suppress("LongParameterList") // A design-system row: independent optional slots.
@Composable
fun EnclyStepRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    onLongPressLabel: String? = null,
    actions: List<CustomAccessibilityAction> = emptyList(),
) {
    val colors = MaterialTheme.colorScheme
    StepRowFrame(modifier = modifier) {
        StepLead(title, checked, onCheckedChange, enabled)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = if (checked) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onLongPress != null && enabled) {
                        Modifier
                            .pointerInput(onLongPress) { detectTapGestures(onLongPress = { onLongPress() }) }
                            .semantics {
                                onLongClick(onLongPressLabel) {
                                    onLongPress()
                                    true
                                }
                            }
                    } else {
                        Modifier
                    },
                )
                .semantics { if (actions.isNotEmpty()) customActions = actions },
        )
    }
}

/**
 * A step being typed in place of its row: its checkbox ([checked], which [onCheckedChange] flips), or a
 * plus for a new step ([checked] null), then a one-line field. [label] is its placeholder and spoken name;
 * [imeAction] is the keyboard's key, which runs [onImeAction]. [fieldModifier] reaches the text field itself.
 */
@Suppress("LongParameterList") // A field's value, callbacks and slots.
@Composable
fun EnclyStepField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    checked: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface)
    StepRowFrame(modifier = modifier) {
        StepLead(value, checked, onCheckedChange, enabled = true)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            // No suggestions or learning, as for every other vault text.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
            keyboardActions = KeyboardActions { onImeAction() },
            modifier = Modifier
                .weight(1f)
                .then(fieldModifier)
                .semantics { contentDescription = label },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(text = label, style = style.copy(color = colors.onSurfaceVariant), maxLines = 1)
                    }
                    innerTextField()
                }
            },
        )
    }
}

/** "+ Step" under a task's steps, in `primary`: [onClick] turns it into the field for a new one. */
@Composable
fun EnclyAddStep(text: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    StepRowFrame(modifier = modifier.clickable(role = Role.Button, onClickLabel = description, onClick = onClick)) {
        StepLead(text, checked = null, onCheckedChange = {}, enabled = true)
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
