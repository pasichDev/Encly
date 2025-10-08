package com.pasich.encly.presentation.components.tiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.Lucide
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.components.tasks.CompletedIndicator
import com.pasich.encly.presentation.components.tasks.PriorityIndicator
import com.pasich.encly.presentation.components.tasks.ReminderIndicator
import kotlinx.coroutines.delay

@Composable
fun TaskItem(
    task: Task,
    onTaskToggle: (Long, Boolean) -> Unit,
    onTaskClick: ((Task) -> Unit)? = null,
    onAddToCalendar: ((Task) -> Unit)? = null,
    enabled: Boolean = true
) {
    // Состояние для анимации исчезновения
    var isRemoving by remember(task.id) { mutableStateOf(false) }
    var shouldComplete by remember(task.id) { mutableStateOf(false) }

    // Анимация исчезновения
    val animationProgress by animateFloatAsState(
        targetValue = if (isRemoving) 0f else 1f,
        animationSpec = tween(durationMillis = 400),
        label = "task_removal_animation"
    )

    // Обработка завершения анимации
    LaunchedEffect(shouldComplete) {
        if (shouldComplete) {
            isRemoving = true
            delay(400) // Ждем завершения анимации
            onTaskToggle(task.id, true)
            shouldComplete = false
            isRemoving = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animationProgress
                scaleX = animationProgress
                scaleY = animationProgress
                translationX = (1f - animationProgress) * 100f
            }
            .clickable(enabled = enabled && !task.isCompleted && !isRemoving) {
                onTaskClick?.invoke(task)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = { isChecked ->
                        if (isChecked && !task.isCompleted) {
                            // Запускаем анимацию исчезновения для завершения задачи
                            shouldComplete = true
                        } else if (!isChecked && task.isCompleted) {
                            // Для отмены завершения сразу вызываем onTaskToggle
                            onTaskToggle(task.id, false)
                        }
                    },
                    enabled = enabled && !isRemoving,
                    modifier = Modifier
                        .scale(0.8f)
                        .padding(end = 8.dp)
                        .align(Alignment.Top)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (task.isCompleted)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    task.description?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (task.isCompleted)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!task.isCompleted) {
                            PriorityIndicator(priority = task.priority)
                        }

                        task.reminderDate?.let { reminderDate ->
                            if (!task.isCompleted) {
                                ReminderIndicator(reminderDate)
                            }
                        }

                        if (task.isCompleted && task.completedDate != null) {
                            CompletedIndicator(task.completedDate)
                        }
                    }
                }
            }

            if (!task.isCompleted && onAddToCalendar != null) {
                IconButton(
                    onClick = { onAddToCalendar(task) },
                    modifier = Modifier.align(Alignment.Top)
                ) {
                    Icon(
                        imageVector = Lucide.Calendar,
                        contentDescription = "Додати до календаря",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

    }
}


// Кнопка додавання до календаря (тільки для активних завдань)
/*  if (!task.isCompleted && onAddToCalendar != null) {
      IconButton(
          onClick = { onAddToCalendar(task) },
          modifier = Modifier.size(24.dp)
      ) {
          Icon(
              Lucide.Calendar,
              contentDescription = "Додати до календаря",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
          )
      }
  }

 */