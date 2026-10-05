package com.pasich.encly.presentation.components.tiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.pasich.encly.R
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.components.tasks.NextStepLeaf
import com.pasich.encly.presentation.components.tasks.PriorityValues
import com.pasich.encly.presentation.components.tasks.SubtaskTree
import com.pasich.encly.presentation.components.tasks.SubtaskTreeActions
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyRowIconButton
import com.pasich.encly.presentation.designsystem.EnclySubtaskTree
import com.pasich.encly.presentation.designsystem.EnclyTaskRow
import com.pasich.encly.presentation.viewmodel.InlineSubtaskEdit
import com.pasich.encly.utils.rememberDateTimeFormat
import kotlinx.coroutines.delay
import java.util.Date

private const val TASK_REMOVAL_ANIMATION_MS = 400
private const val TASK_TRANSLATION_X = 100f

/**
 * What a task's block on the Tasks list can do; the screen builds it once from its ViewModel.
 * A tap on the row opens or folds its sub-task tree ([SubtaskTreeActions.onToggleTree]).
 */
@Immutable
class TaskItemActions(
    val onToggleTask: (taskId: Long, completed: Boolean) -> Unit,
    /** The pencil: opens the task sheet, the only way to it from the list. */
    val onEdit: (Task) -> Unit,
    val tree: SubtaskTreeActions,
)

private data class TaskCardState(val enabled: Boolean, val isRemoving: Boolean, val animationProgress: Float)

/**
 * A task and its sub-tasks. Folded: an open task shows its next step as one leaf
 * ([NextStepLeaf]) and every task with sub-tasks a segment bar of their progress; a task
 * without sub-tasks looks like a plain task. [expanded]: the whole tree ([SubtaskTree]). A tap on
 * the row opens or folds it; the pencil opens the task sheet. The block is one list item, so it
 * fades out as a whole when the task is completed. [inlineEdit] is the list's inline sub-task
 * field when it belongs to this task.
 */
@Composable
fun TaskItem(
    task: Task,
    subtasks: List<Subtask>,
    expanded: Boolean,
    actions: TaskItemActions,
    modifier: Modifier = Modifier,
    inlineEdit: InlineSubtaskEdit? = null,
    enabled: Boolean = true,
) {
    var isRemoving by remember(task.id) { mutableStateOf(false) }
    var shouldComplete by remember(task.id) { mutableStateOf(false) }
    val animationProgress by animateFloatAsState(
        targetValue = if (isRemoving) 0f else 1f,
        animationSpec = tween(durationMillis = TASK_REMOVAL_ANIMATION_MS),
        label = "task_removal_animation",
    )

    CompleteTaskAfterAnimation(
        task = task,
        shouldComplete = shouldComplete,
        onRemovingChange = { isRemoving = it },
        onCompleteChange = { shouldComplete = it },
        onTaskToggle = actions.onToggleTask,
    )

    val state = TaskCardState(
        enabled = enabled,
        isRemoving = isRemoving,
        animationProgress = animationProgress,
    )
    val tree = updateTransition(targetState = expanded, label = "subtasks")
    val enabledNow = state.enabled && !state.isRemoving
    // Done tasks show only the bar while folded.
    val showsNextStep = !task.isCompleted && subtasks.isNotEmpty()
    Column(
        modifier = modifier.graphicsLayer {
            alpha = state.animationProgress
            scaleX = state.animationProgress
            scaleY = state.animationProgress
            translationX = (1f - state.animationProgress) * TASK_TRANSLATION_X
        },
    ) {
        TaskRow(
            task = task,
            subtasks = subtasks,
            expanded = expanded,
            // The trunk stays drawn until the tree has fully folded.
            connectorBelow = showsNextStep || tree.currentState || tree.targetState,
            enabled = enabledNow,
            actions = actions,
            onComplete = { shouldComplete = true },
        )
        EnclySubtaskTree(
            expanded = tree,
            collapsed = {
                if (showsNextStep) NextStepLeaf(task.id, subtasks, actions.tree, enabled = enabledNow)
            },
        ) {
            SubtaskTree(
                taskId = task.id,
                subtasks = subtasks,
                edit = inlineEdit,
                actions = actions.tree,
                dimmed = task.isCompleted,
                enabled = enabledNow,
            )
        }
    }
}

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
    LaunchedEffect(shouldComplete) {
        if (!shouldComplete) return@LaunchedEffect
        currentOnRemovingChange(true)
        delay(TASK_REMOVAL_ANIMATION_MS.toLong())
        currentOnTaskToggle(task.id, true)
        currentOnCompleteChange(false)
        currentOnRemovingChange(false)
    }
}

/**
 * The task's own row: checkbox (completes it after the removal animation, or reopens it), the
 * text with the segment bar of its sub-tasks, and the pencil. A tap elsewhere opens or folds the
 * tree. For TalkBack the row is one node that reads the title, priority and how many sub-tasks
 * are done, with Expand/Collapse, Edit and Add sub-task as actions.
 */
@Suppress("LongParameterList", "LongMethod") // The row's state, callbacks and semantics, split out of TaskItem.
@Composable
private fun TaskRow(
    task: Task,
    subtasks: List<Subtask>,
    expanded: Boolean,
    connectorBelow: Boolean,
    enabled: Boolean,
    actions: TaskItemActions,
    onComplete: () -> Unit,
) {
    val dateFormat = rememberDateTimeFormat()
    val focusManager = LocalFocusManager.current
    val priority = PriorityValues.getById(task.priority)
    val completedAt = task.completedDate?.takeIf { task.isCompleted }?.let {
        stringResource(R.string.task_completed_at, dateFormat.format(Date(it)))
    }
    val done = subtasks.count { it.isCompleted }
    val summary = pluralStringResource(R.plurals.subtasks_done_summary, subtasks.size, done, subtasks.size)
        .takeIf { subtasks.isNotEmpty() }
    val toggleLabel = stringResource(if (expanded) R.string.subtasks_hide else R.string.subtasks_show)
    val stateLabel = stringResource(if (expanded) R.string.subtasks_state_shown else R.string.subtasks_state_hidden)
    val editLabel = stringResource(R.string.task_edit_placeholder)
    val addLabel = stringResource(R.string.subtask_add)
    val toggle = { actions.tree.onToggleTree(task.id) }
    EnclyTaskRow(
        title = task.title,
        checked = task.isCompleted,
        onCheckedChange = { checked ->
            // Saves and closes an inline sub-task field first, as any other tap on the list does.
            focusManager.clearFocus()
            when {
                checked && !task.isCompleted -> onComplete()
                !checked && task.isCompleted -> actions.onToggleTask(task.id, false)
            }
        },
        description = task.description,
        meta = completedAt,
        priority = stringResource(priority.label).takeIf { !task.isCompleted },
        priorityEmphasis = priority.emphasis,
        large = true,
        enabled = enabled,
        // Done tasks open too: their sub-tasks can still be ticked, renamed and added.
        onClick = toggle,
        onClickLabel = toggleLabel,
        connectorBelow = connectorBelow,
        progress = subtasks.map { it.isCompleted }.takeIf { it.isNotEmpty() },
        modifier = Modifier.semantics {
            stateDescription = listOfNotNull(summary, stateLabel).joinToString(", ")
            customActions = listOf(
                CustomAccessibilityAction(toggleLabel) {
                    toggle()
                    true
                },
                CustomAccessibilityAction(editLabel) {
                    actions.onEdit(task)
                    true
                },
                CustomAccessibilityAction(addLabel) {
                    actions.tree.onStartAdding(task.id)
                    true
                },
            )
        },
    ) {
        EnclyRowIconButton(
            icon = EnclyIcons.Edit,
            contentDescription = editLabel,
            onClick = { actions.onEdit(task) },
            enabled = enabled,
        )
    }
}
