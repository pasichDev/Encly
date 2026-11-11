package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.EnclyTheme

/** What the bar above the keyboard does to the step being typed; a null action is not offered. */
class StepBarActions(
    val onMoveUp: (() -> Unit)?,
    val onMoveDown: (() -> Unit)?,
    val onDelete: (() -> Unit)?,
    val onDone: () -> Unit,
)

/** The names of the bar's buttons, for the tooltips and TalkBack. */
class StepBarLabels(val moveUp: String, val moveDown: String, val delete: String, val done: String)

/**
 * The bar of a step being typed, for the screen to put on top of the keyboard: move up, move down and
 * delete (shown with [showStepTools]; an arrow without an action is disabled), and "Done" at the end.
 * 52 dp, `surfaceContainer`, full width. Its buttons never take the focus from the field.
 */
@Composable
fun EnclyStepBar(
    actions: StepBarActions,
    labels: StepBarLabels,
    showStepTools: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val keepFocus = Modifier.focusProperties { canFocus = false }
    Surface(color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(StepBarHeight).padding(horizontal = EnclyTheme.spacing.xs),
        ) {
            if (showStepTools) {
                IconWithTooltip(
                    icon = EnclyIcons.ArrowUp,
                    label = labels.moveUp,
                    onClick = { actions.onMoveUp?.invoke() },
                    enabled = actions.onMoveUp != null,
                    modifier = keepFocus,
                )
                IconWithTooltip(
                    icon = EnclyIcons.ArrowDown,
                    label = labels.moveDown,
                    onClick = { actions.onMoveDown?.invoke() },
                    enabled = actions.onMoveDown != null,
                    modifier = keepFocus,
                )
                IconWithTooltip(
                    icon = EnclyIcons.Trash,
                    label = labels.delete,
                    onClick = { actions.onDelete?.invoke() },
                    enabled = actions.onDelete != null,
                    tint = colors.error,
                    modifier = keepFocus,
                )
            }
            Spacer(Modifier.weight(1f))
            EnclyTextButton(text = labels.done, onClick = actions.onDone, modifier = keepFocus)
        }
    }
}

private val StepBarHeight = 52.dp
