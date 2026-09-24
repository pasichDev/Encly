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
 * and the two most urgent open tasks, which can be ticked off here. Nothing when none is open.
 */
@Composable
fun HomeTaskWidget(
    modifier: Modifier = Modifier,
    onTasksClick: () -> Unit = {},
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val preview = remember(uiState.activeTasks) { widgetTasks(uiState.activeTasks) }
    if (uiState.activeTasksCount == 0 || preview.isEmpty()) return

    EnclyCard(
        modifier = modifier,
        style = EnclyCardStyle.OUTLINED,
        contentPadding = PaddingValues(
            start = EnclyTheme.spacing.m,
            end = EnclyTheme.spacing.m,
            top = EnclyTheme.spacing.rowGap,
            bottom = EnclyTheme.spacing.labelGap,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionOverline(
                text = pluralStringResource(
                    R.plurals.home_tasks_open,
                    uiState.activeTasksCount,
                    uiState.activeTasksCount,
                ),
                modifier = Modifier.weight(1f),
            )
            EnclyInlineLink(text = stringResource(R.string.task_filter_all), onClick = onTasksClick)
        }
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
