package com.pasich.encly.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.pasich.encly.R
import com.pasich.encly.presentation.components.tasks.PriorityValues
import com.pasich.encly.presentation.designsystem.EnclyCard
import com.pasich.encly.presentation.designsystem.EnclyCardStyle
import com.pasich.encly.presentation.designsystem.EnclyInlineLink
import com.pasich.encly.presentation.designsystem.EnclyTaskRow
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.viewmodel.TasksViewModel
import com.pasich.encly.presentation.viewmodel.widgetTasks
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * The tasks card on the notes screen (design spec §4.3): "TASKS · N OPEN", a link to all tasks
 * and the two most urgent open tasks, which can be ticked off here. With none open it shrinks to
 * "TASKS · NONE OPEN" and a "New task" link ([onNewTask]), so the first task is one tap away.
 */
@Composable
fun HomeTaskWidget(
    modifier: Modifier = Modifier,
    onTasksClick: () -> Unit = {},
    onNewTask: () -> Unit = onTasksClick,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val preview = remember(uiState.activeTasks) { widgetTasks(uiState.activeTasks) }
    // Nothing until the first read, so the card does not flash "none open" over real tasks.
    if (uiState.isLoading) return
    val noneOpen = uiState.activeTasksCount == 0 || preview.isEmpty()

    EnclyCard(
        modifier = modifier,
        style = EnclyCardStyle.OUTLINED,
        contentPadding = PaddingValues(
            start = EnclyTheme.spacing.m,
            end = EnclyTheme.spacing.m,
            top = EnclyTheme.spacing.rowGap,
            bottom = if (noneOpen) EnclyTheme.spacing.rowGap else EnclyTheme.spacing.labelGap,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionOverline(
                text = if (noneOpen) {
                    stringResource(R.string.home_tasks_none_open)
                } else {
                    pluralStringResource(R.plurals.home_tasks_open, uiState.activeTasksCount, uiState.activeTasksCount)
                },
                modifier = Modifier.weight(1f),
            )
            if (noneOpen) {
                EnclyInlineLink(text = stringResource(R.string.task_add), onClick = onNewTask)
            } else {
                EnclyInlineLink(text = stringResource(R.string.task_filter_all), onClick = onTasksClick)
            }
        }
        if (!noneOpen) {
            preview.forEach { task ->
                val priority = PriorityValues.getById(task.priority)
                EnclyTaskRow(
                    title = task.title,
                    checked = false,
                    onCheckedChange = { checked -> if (checked) viewModel.toggleTaskCompletion(task.id, true) },
                    priority = stringResource(priority.label),
                    priorityEmphasis = priority.emphasis,
                    onClick = onTasksClick,
                )
            }
        }
    }
}
