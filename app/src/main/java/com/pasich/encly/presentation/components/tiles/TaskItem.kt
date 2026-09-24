package com.pasich.encly.presentation.components.tiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.components.tasks.PriorityValues
import com.pasich.encly.presentation.designsystem.EnclyTaskRow
import com.pasich.encly.utils.rememberDateTimeFormat
import kotlinx.coroutines.delay
import java.util.Date

private const val TASK_REMOVAL_ANIMATION_MS = 400
private const val TASK_TRANSLATION_X = 100f

private data class TaskCardState(val enabled: Boolean, val isRemoving: Boolean, val animationProgress: Float)

private data class TaskCardActions(val onClick: () -> Unit, val onComplete: () -> Unit, val onUndo: () -> Unit)

@Composable
fun TaskItem(
    task: Task,
    onTaskToggle: (Long, Boolean) -> Unit,
    onTaskClick: ((Task) -> Unit)? = null,
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
        onTaskToggle = onTaskToggle,
    )

    val state = TaskCardState(
        enabled = enabled,
        isRemoving = isRemoving,
        animationProgress = animationProgress,
    )
    val actions = TaskCardActions(
        onClick = { onTaskClick?.invoke(task) },
        onComplete = { shouldComplete = true },
        onUndo = { onTaskToggle(task.id, false) },
    )
    TaskCard(task, state, actions)
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

@Composable
private fun TaskCard(task: Task, state: TaskCardState, actions: TaskCardActions) {
    val dateFormat = rememberDateTimeFormat()
    val priority = PriorityValues.getById(task.priority)
    EnclyTaskRow(
        title = task.title,
        checked = task.isCompleted,
        onCheckedChange = { checked ->
            when {
                checked && !task.isCompleted -> actions.onComplete()
                !checked && task.isCompleted -> actions.onUndo()
            }
        },
        description = task.description,
        meta = task.completedDate?.takeIf { task.isCompleted }?.let {
            stringResource(R.string.task_completed_at, dateFormat.format(Date(it)))
        },
        priority = stringResource(priority.label).takeIf { !task.isCompleted },
        priorityEmphasis = priority.emphasis,
        large = true,
        enabled = state.enabled && !state.isRemoving,
        // Completed tasks open too, to read or edit them.
        onClick = actions.onClick,
        modifier = Modifier.graphicsLayer {
            alpha = state.animationProgress
            scaleX = state.animationProgress
            scaleY = state.animationProgress
            translationX = (1f - state.animationProgress) * TASK_TRANSLATION_X
        },
    )
}
