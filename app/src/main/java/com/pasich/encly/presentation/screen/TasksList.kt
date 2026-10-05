package com.pasich.encly.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.components.tasks.InlineSubtaskActions
import com.pasich.encly.presentation.components.tasks.SubtaskTreeActions
import com.pasich.encly.presentation.components.tasks.TaskFilterChips
import com.pasich.encly.presentation.components.tasks.TaskProgressCard
import com.pasich.encly.presentation.components.tiles.TaskItem
import com.pasich.encly.presentation.components.tiles.TaskItemActions
import com.pasich.encly.presentation.designsystem.EnclyEmptyState
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyInlineLink
import com.pasich.encly.presentation.designsystem.RowSkeleton
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.viewmodel.InlineSubtaskEdit
import com.pasich.encly.presentation.viewmodel.TaskFilter
import com.pasich.encly.presentation.viewmodel.TasksUiState
import com.pasich.encly.presentation.viewmodel.TasksViewModel
import com.pasich.encly.ui.theme.EnclyTheme

private const val SKELETON_ROWS = 4

/** What the list shows: the tasks, which trees are open, and the inline sub-task field. */
internal class TasksListState(val ui: TasksUiState, val expanded: Set<Long>, val inlineEdit: InlineSubtaskEdit?)

/** The list's task and sub-task callbacks, all into [viewModel]. */
internal fun taskItemActions(viewModel: TasksViewModel) = TaskItemActions(
    onToggleTask = viewModel::toggleTaskCompletion,
    onEdit = viewModel::showEditTaskDialog,
    tree = SubtaskTreeActions(
        onToggle = viewModel::toggleSubtask,
        onStartAdding = viewModel::startAddingSubtask,
        onStartRenaming = viewModel::startRenamingSubtask,
        onMove = viewModel::moveSubtask,
        onToggleTree = viewModel::toggleSubtasks,
        inline = InlineSubtaskActions(
            onTextChange = viewModel::onInlineTextChange,
            onDone = viewModel::submitInlineEdit,
            onClose = viewModel::closeInlineEdit,
            onDelete = viewModel::deleteInlineSubtask,
        ),
    ),
)

/** The list's own callbacks besides the tasks'. */
internal class TasksListCallbacks(
    val onFilter: (TaskFilter) -> Unit,
    val onNewTask: () -> Unit,
    val onClearCompleted: () -> Unit,
)

/**
 * The tasks list: kept above the keyboard (for an inline sub-task field), and a tap on nothing
 * in particular closes (and saves) that field.
 */
@Composable
internal fun TasksList(
    state: LazyListState,
    list: TasksListState,
    actions: TaskItemActions,
    callbacks: TasksListCallbacks,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    LazyColumn(
        state = state,
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .pointerInput(focusManager) { detectTapGestures { focusManager.clearFocus() } },
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
        contentPadding = tasksListPadding(),
    ) {
        tasksContent(
            state = list,
            actions = actions,
            onFilter = callbacks.onFilter,
            onNewTask = callbacks.onNewTask,
            onClearCompleted = callbacks.onClearCompleted,
        )
    }
}

/**
 * Back folds the tree opened last among the tasks the list shows ([onFold] gets their ids).
 * An open inline field takes Back first, to close itself.
 */
@Composable
internal fun FoldTreesOnBack(list: TasksListState, onFold: (Set<Long>) -> Boolean) {
    val visible = visibleTasks(list.ui).let { (open, done) -> (open + done).mapTo(HashSet()) { it.id } }
    BackHandler(enabled = list.inlineEdit == null && list.expanded.any { it in visible }) { onFold(visible) }
}

/** The gutter, and room below the last task for the FAB. */
@Composable
private fun tasksListPadding() = PaddingValues(
    start = EnclyTheme.spacing.gutter,
    end = EnclyTheme.spacing.gutter,
    top = EnclyTheme.spacing.xs,
    bottom = EnclyTheme.spacing.fabHeight + EnclyTheme.spacing.l + EnclyTheme.spacing.m,
)

/** The list: a skeleton while loading, else progress, chips, open tasks and the Done section. */
private fun LazyListScope.tasksContent(
    state: TasksListState,
    actions: TaskItemActions,
    onFilter: (TaskFilter) -> Unit,
    onNewTask: () -> Unit,
    onClearCompleted: () -> Unit,
) {
    val ui = state.ui
    if (ui.isLoading) {
        items(SKELETON_ROWS) { RowSkeleton() }
        return
    }
    if (ui.totalTasksCount == 0) {
        item {
            EnclyEmptyState(
                icon = EnclyIcons.Checklist,
                title = stringResource(R.string.task_empty_title),
                body = stringResource(R.string.task_empty_desc),
                actionLabel = stringResource(R.string.task_add),
                onAction = onNewTask,
            )
        }
        return
    }
    progressAndFilters(ui, onFilter)
    val showingCompleted = ui.selectedCompletedFilter != null
    val (open, done) = visibleTasks(ui)
    if (!showingCompleted && open.isEmpty()) {
        item(key = "empty") {
            EnclyEmptyState(
                icon = EnclyIcons.Checklist,
                title = stringResource(R.string.task_all_done_title).takeIf { ui.selectedPriorityFilter == null },
                body = stringResource(
                    if (ui.selectedPriorityFilter !=
                        null
                    ) {
                        R.string.task_empty_filtered
                    } else {
                        R.string.task_empty_active
                    },
                ),
            )
        }
    }
    taskRows(open, state, actions)
    if (done.isNotEmpty()) {
        item(key = "done") {
            DoneHeader(count = done.size, onClear = onClearCompleted)
        }
        taskRows(done, state, actions)
    }
}

/** The open tasks and the Done section's tasks the list shows under the current filter. */
private fun visibleTasks(ui: TasksUiState): Pair<List<Task>, List<Task>> {
    val showingCompleted = ui.selectedCompletedFilter != null
    val open = if (showingCompleted) emptyList() else ui.filteredActiveTasks
    val done = when {
        showingCompleted -> ui.completedTasks

        // A priority filter narrows the open tasks only; the Done section belongs to "All tasks".
        ui.selectedPriorityFilter == null -> ui.completedTasks

        else -> emptyList()
    }
    return open to done
}

/** One item per task: the task and, when open, its sub-task tree under it, so they move as one. */
private fun LazyListScope.taskRows(tasks: List<Task>, state: TasksListState, actions: TaskItemActions) {
    items(tasks, key = { it.id }) { task ->
        TaskItem(
            task = task,
            subtasks = state.ui.subtasks[task.id].orEmpty(),
            expanded = task.id in state.expanded,
            actions = actions,
            inlineEdit = state.inlineEdit?.takeIf { it.target.taskId == task.id },
            modifier = Modifier.animateItem(),
        )
    }
}

private fun LazyListScope.progressAndFilters(state: TasksUiState, onFilter: (TaskFilter) -> Unit) {
    item(key = "progress") {
        Column(verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s)) {
            TaskProgressCard(
                completionPercentage = state.completionPercentage,
                completedTasksCount = state.completedTasksCount,
                totalTasksCount = state.totalTasksCount,
            )
            TaskFilterChips(
                availableFilters = state.availableFilters,
                selectedFilterIds = setOfNotNull(
                    state.selectedActiveFilter?.id,
                    state.selectedPriorityFilter?.id,
                    state.selectedCompletedFilter?.id,
                ),
                onFilterSelect = onFilter,
            )
        }
    }
}

/** "DONE · 3" and the Clear action for the completed tasks below it. */
@Composable
private fun DoneHeader(count: Int, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = EnclyTheme.spacing.m),
    ) {
        SectionOverline(stringResource(R.string.task_done_section, count), modifier = Modifier.weight(1f))
        EnclyInlineLink(text = stringResource(R.string.clear), onClick = onClear)
    }
}
