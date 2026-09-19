package com.pasich.encly.presentation.components.tiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.components.tasks.CompletedIndicator
import com.pasich.encly.presentation.components.tasks.PriorityIndicator
import com.pasich.encly.presentation.components.tasks.ReminderIndicator
import kotlinx.coroutines.delay

private const val TASK_REMOVAL_ANIMATION_MS = 400
private const val TASK_TRANSLATION_X = 100f

@Composable
fun TaskItem(
    task: Task,
    onTaskToggle: (Long, Boolean) -> Unit,
    onTaskClick: ((Task) -> Unit)? = null,
    enabled: Boolean = true
) {
    var isRemoving by remember(task.id) { mutableStateOf(false) }
    var shouldComplete by remember(task.id) { mutableStateOf(false) }
    val animationProgress by animateFloatAsState(
        targetValue = if (isRemoving) 0f else 1f,
        animationSpec = tween(durationMillis = TASK_REMOVAL_ANIMATION_MS),
        label = "task_removal_animation"
    )

    CompleteTaskAfterAnimation(
        task = task,
        shouldComplete = shouldComplete,
        onRemovingChange = { isRemoving = it },
        onCompleteChange = { shouldComplete = it },
        onTaskToggle = onTaskToggle
    )

    TaskCard(
        task = task,
        enabled = enabled,
        isRemoving = isRemoving,
        animationProgress = animationProgress,
        onClick = { onTaskClick?.invoke(task) },
        onComplete = { shouldComplete = true },
        onUndo = { onTaskToggle(task.id, false) }
    )
}

@Composable
private fun CompleteTaskAfterAnimation(
    task: Task,
    shouldComplete: Boolean,
    onRemovingChange: (Boolean) -> Unit,
    onCompleteChange: (Boolean) -> Unit,
    onTaskToggle: (Long, Boolean) -> Unit
) {
    LaunchedEffect(shouldComplete) {
        if (!shouldComplete) return@LaunchedEffect
        onRemovingChange(true)
        delay(TASK_REMOVAL_ANIMATION_MS.toLong())
        onTaskToggle(task.id, true)
        onCompleteChange(false)
        onRemovingChange(false)
    }
}

@Composable
private fun TaskCard(
    task: Task,
    enabled: Boolean,
    isRemoving: Boolean,
    animationProgress: Float,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onUndo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animationProgress
                scaleX = animationProgress
                scaleY = animationProgress
                translationX = (1f - animationProgress) * TASK_TRANSLATION_X
            }
            .clickable(enabled = enabled && !task.isCompleted && !isRemoving, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        TaskCardContent(
            task = task,
            enabled = enabled,
            isRemoving = isRemoving,
            onComplete = onComplete,
            onUndo = onUndo
        )
    }
}

@Composable
private fun TaskCardContent(
    task: Task,
    enabled: Boolean,
    isRemoving: Boolean,
    onComplete: () -> Unit,
    onUndo: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 15.dp, horizontal = 5.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.weight(1f)
        ) {
            TaskCheckbox(
                task = task,
                enabled = enabled && !isRemoving,
                onComplete = onComplete,
                onUndo = onUndo
            )
            TaskTextContent(task)
        }
    }
}

@Composable
private fun TaskCheckbox(
    task: Task,
    enabled: Boolean,
    onComplete: () -> Unit,
    onUndo: () -> Unit
) {
    Checkbox(
        checked = task.isCompleted,
        onCheckedChange = { checked ->
            when {
                checked && !task.isCompleted -> onComplete()
                !checked && task.isCompleted -> onUndo()
            }
        },
        enabled = enabled,
        modifier = Modifier
            .scale(0.8f)
            .padding(end = 8.dp)
    )
}

@Composable
private fun TaskTextContent(task: Task) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                textDecoration = completedDecoration(task.isCompleted)
            ),
            color = completedTextColor(task.isCompleted),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        task.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = completedDecoration(task.isCompleted)
            )
        }

        TaskIndicators(task)
    }
}

@Composable
private fun TaskIndicators(task: Task) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!task.isCompleted) {
            PriorityIndicator(priority = task.priority)
            task.reminderDate?.let { ReminderIndicator(it) }
        }
        if (task.isCompleted) {
            task.completedDate?.let { CompletedIndicator(it) }
        }
    }
}

@Composable
private fun completedTextColor(isCompleted: Boolean) =
    if (isCompleted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

private fun completedDecoration(isCompleted: Boolean): TextDecoration =
    if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
