package com.pasich.encly.presentation.components.tiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.subtasks_done_summary
import com.pasich.encly.core.resources.subtasks_hide
import com.pasich.encly.core.resources.subtasks_show
import com.pasich.encly.core.resources.task_completed_at
import com.pasich.encly.core.resources.task_edit_placeholder
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.components.tasks.PriorityValues
import com.pasich.encly.presentation.components.tasks.StepActions
import com.pasich.encly.presentation.designsystem.EnclyContextMenu
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTaskListRow
import com.pasich.encly.presentation.designsystem.MenuEntry
import com.pasich.encly.presentation.designsystem.TaskRowEnd
import com.pasich.encly.presentation.designsystem.TaskStepsToggle
import com.pasich.encly.ui.theme.EnclyMotion
import com.pasich.encly.utils.formatNoteDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private const val TASK_REMOVAL_ANIMATION_MS = EnclyMotion.LONG_MS
private const val COMPLETION_SHOWN_TIMEOUT_MS = 1_000L
private const val TASK_TRANSLATION_X = 100f

/** What a task's block on the Tasks list can do; the screen builds it once from its ViewModel. */
@Immutable
class TaskItemActions(
    val onToggleTask: (taskId: Long, completed: Boolean) -> Unit,
    /** A tap on the row: opens the task's sheet (its title, description, priority and checklist). */
    val onEdit: (Task) -> Unit,
    val steps: StepActions,
)

/**
 * A task's row on the tasks list; its steps follow it as list items of their own. A tap on the row opens
 * the task's sheet. An open task with steps carries their count as a button that opens or folds them
 * ([stepsOpen] is how they are shown now). Ticked, the row fades out before the task is stored as done.
 */
@Composable
fun TaskItem(
    task: Task,
    subtasks: List<Subtask>,
    actions: TaskItemActions,
    modifier: Modifier = Modifier,
    stepsOpen: Boolean = false,
    enabled: Boolean = true,
) {
    var isRemoving by remember(task.id) { mutableStateOf(false) }
    var shouldComplete by remember(task.id) { mutableStateOf(false) }
    val shown by animateFloatAsState(
        targetValue = if (isRemoving) 0f else 1f,
        animationSpec = tween(durationMillis = TASK_REMOVAL_ANIMATION_MS, easing = EnclyMotion.Standard),
        label = "task_removal_animation",
    )

    CompleteTaskAfterAnimation(
        task = task,
        shouldComplete = shouldComplete,
        onRemovingChange = { isRemoving = it },
        onCompleteChange = { shouldComplete = it },
        onTaskToggle = actions.onToggleTask,
    )

    val enabledNow = enabled && !isRemoving
    val towardsEnd = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Box(
        modifier = modifier.graphicsLayer {
            alpha = shown
            translationX = towardsEnd * (1f - shown) * TASK_TRANSLATION_X
        },
    ) {
        TaskRow(TaskRowState(task, subtasks, stepsOpen, enabledNow), actions) { shouldComplete = true }
    }
}

/** What a task's row shows. */
private class TaskRowState(val task: Task, val subtasks: List<Subtask>, val stepsOpen: Boolean, val enabled: Boolean)

@Composable
private fun CompleteTaskAfterAnimation(
    task: Task,
    shouldComplete: Boolean,
    onRemovingChange: (Boolean) -> Unit,
    onCompleteChange: (Boolean) -> Unit,
    onTaskToggle: (Long, Boolean) -> Unit,
) {
    // The effect outlives recompositions (it restarts only on shouldComplete); always call the
    // latest callbacks.
    val currentOnRemovingChange by rememberUpdatedState(onRemovingChange)
    val currentOnCompleteChange by rememberUpdatedState(onCompleteChange)
    val currentOnTaskToggle by rememberUpdatedState(onTaskToggle)
    val currentTask by rememberUpdatedState(task)
    LaunchedEffect(shouldComplete) {
        if (!shouldComplete) return@LaunchedEffect
        currentOnRemovingChange(true)
        delay(TASK_REMOVAL_ANIMATION_MS.toLong())
        currentOnTaskToggle(task.id, true)
        // The write is not instant. The row comes back only as the done task it then is (or, should the
        // write fail, after a moment), never fading in as the open one in the meantime.
        withTimeoutOrNull(COMPLETION_SHOWN_TIMEOUT_MS) { snapshotFlow { currentTask.isCompleted }.first { it } }
        currentOnCompleteChange(false)
        currentOnRemovingChange(false)
    }
}

/**
 * The task's own row: checkbox (completes it after the removal animation, or reopens it), title and
 * description, and at the end its priority, or when it was done. For TalkBack the row also reads how many
 * steps are done and offers Edit as an action; a right click (or the Menu key) offers the same.
 */
@Composable
private fun TaskRow(state: TaskRowState, actions: TaskItemActions, onComplete: () -> Unit) {
    val task = state.task
    val subtasks = state.subtasks
    val enabled = state.enabled
    val doneAt = task.completedDate?.takeIf { task.isCompleted }
        ?.let { formatNoteDate(Instant.fromEpochMilliseconds(it)) }
    val steps = stepsToggle(state, actions.steps.onSetOpen)
    val summary = listOfNotNull(
        stepsSummary(subtasks),
        doneAt?.let { stringResource(Res.string.task_completed_at, it) },
    ).joinToString(", ")
    val editLabel = stringResource(Res.string.task_edit_placeholder)
    EnclyContextMenu(
        entries = { listOf(MenuEntry(editLabel, { actions.onEdit(task) }, EnclyIcons.Edit)) },
        enabled = enabled,
    ) {
        EnclyTaskListRow(
            title = task.title,
            checked = task.isCompleted,
            onCheckedChange = { checked ->
                when {
                    checked && !task.isCompleted -> onComplete()
                    !checked && task.isCompleted -> actions.onToggleTask(task.id, false)
                }
            },
            description = task.description,
            end = rowEnd(task, doneAt),
            steps = steps,
            enabled = enabled,
            onClick = { actions.onEdit(task) },
            onClickLabel = editLabel,
            modifier = Modifier.semantics {
                if (summary.isNotEmpty()) stateDescription = summary
                customActions = listOfNotNull(
                    CustomAccessibilityAction(editLabel) {
                        actions.onEdit(task)
                        true
                    },
                    steps?.let { toggle ->
                        CustomAccessibilityAction(toggle.label) {
                            toggle.onToggle()
                            true
                        }
                    },
                )
            },
        )
    }
}

/** What the row ends with: when a done task was done, an open task's priority. */
@Composable
private fun rowEnd(task: Task, doneAt: String?): TaskRowEnd? = if (task.isCompleted) {
    doneAt?.let { TaskRowEnd(it) }
} else {
    val priority = PriorityValues.getById(task.priority)
    TaskRowEnd(stringResource(priority.label), emphasis = priority.emphasis)
}

/** "2 of 5 sub-tasks done", for TalkBack; null for a task without steps. */
@Composable
private fun stepsSummary(subtasks: List<Subtask>): String? = if (subtasks.isEmpty()) {
    null
} else {
    pluralStringResource(
        Res.plurals.subtasks_done_summary,
        subtasks.size,
        subtasks.count {
            it.isCompleted
        },
        subtasks.size,
    )
}

/** The count button of an open task with steps. A done task shows no steps, so it has nothing to open. */
@Composable
private fun stepsToggle(state: TaskRowState, onSetOpen: (Long, Boolean) -> Unit): TaskStepsToggle? {
    val subtasks = state.subtasks
    if (subtasks.isEmpty() || state.task.isCompleted) return null
    return TaskStepsToggle(
        count = "${subtasks.count { it.isCompleted }} / ${subtasks.size}",
        open = state.stepsOpen,
        label = stringResource(if (state.stepsOpen) Res.string.subtasks_hide else Res.string.subtasks_show),
        onToggle = { onSetOpen(state.task.id, !state.stepsOpen) },
    )
}
