package com.pasich.encly.presentation.screen

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.components.tasks.TaskFilterChips
import com.pasich.encly.presentation.components.tasks.TaskProgressCard
import com.pasich.encly.presentation.components.tiles.TaskItem
import com.pasich.encly.presentation.designsystem.EnclyEmptyState
import com.pasich.encly.presentation.designsystem.EnclyFab
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyInlineLink
import com.pasich.encly.presentation.designsystem.EnclySnackbarHost
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.RowSkeleton
import com.pasich.encly.presentation.designsystem.SectionOverline
import com.pasich.encly.presentation.dialogs.RequestCleanCompleteTaskDialog
import com.pasich.encly.presentation.dialogs.tasks.AddTaskDialog
import com.pasich.encly.presentation.viewmodel.TaskFilter
import com.pasich.encly.presentation.viewmodel.TaskOperationFailure
import com.pasich.encly.presentation.viewmodel.TasksUiState
import com.pasich.encly.presentation.viewmodel.TasksViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val SKELETON_ROWS = 4

/**
 * The tasks screen: progress, filter chips, the open tasks and, below them, a "Done" section
 * with its "Clear" action. [openNewTask] opens the new-task sheet on arrival (the home card's
 * "New task" link).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    openNewTask: Boolean = false,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    OpenOnArrival(openNewTask, viewModel::showAddTaskDialog)
    val context = LocalContext.current
    LaunchedEffect(viewModel) { showTaskMessages(viewModel, snackbarHostState, context) }
    val listState = rememberTasksListState(uiState)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { EnclySnackbarHost(snackbarHostState) },
        topBar = {
            EnclyTopBar(title = stringResource(R.string.main_drawer_tasks), onBack = navController::popBackStack)
        },
        floatingActionButton = {
            EnclyFab(
                text = stringResource(R.string.task_add),
                icon = EnclyIcons.Plus,
                expanded = listState.firstVisibleItemIndex == 0,
                onClick = viewModel::showAddTaskDialog,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs),
            contentPadding = tasksListPadding(),
        ) {
            tasksContent(
                state = uiState,
                onToggle = viewModel::toggleTaskCompletion,
                onOpen = viewModel::showEditTaskDialog,
                onFilter = viewModel::onFilterSelected,
                onNewTask = viewModel::showAddTaskDialog,
                onClearCompleted = { confirmClear = true },
            )
        }

        if (viewModel.showAddTaskDialog.collectAsState().value) {
            AddTaskDialog(
                onDismiss = viewModel::hideAddTaskDialog,
                onAddTask = viewModel::addTask,
                editTask = viewModel.editingTask.collectAsState().value,
                onEditTask = viewModel::editTask,
                onBackgroundSave = viewModel::saveDraftForBackground,
                onDeleteTask = viewModel::deleteTask,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            )
        }
        RequestCleanCompleteTaskDialog(
            showDialog = confirmClear,
            onDismiss = { confirmClear = false },
            onConfirm = { viewModel.clearCompletedTasks { confirmClear = false } },
        )
    }
}

/** Runs [open] once when [requested], and not again after a rotation. */
@Composable
private fun OpenOnArrival(requested: Boolean, open: () -> Unit) {
    var handled by rememberSaveable { mutableStateOf(false) }
    val currentOpen by rememberUpdatedState(open)
    LaunchedEffect(requested) {
        if (requested && !handled) {
            handled = true
            currentOpen()
        }
    }
}

/** The list's scroll state; it returns to the top whenever the filter changes. */
@Composable
private fun rememberTasksListState(state: TasksUiState): LazyListState {
    val listState = rememberLazyListState()
    LaunchedEffect(state.selectedActiveFilter, state.selectedPriorityFilter, state.selectedCompletedFilter) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.animateScrollToItem(0)
        }
    }
    return listState
}

/** The gutter, and room below the last task for the FAB. */
@Composable
private fun tasksListPadding() = PaddingValues(
    start = EnclyTheme.spacing.gutter,
    end = EnclyTheme.spacing.gutter,
    top = EnclyTheme.spacing.xs,
    bottom = EnclyTheme.spacing.fabHeight + EnclyTheme.spacing.l + EnclyTheme.spacing.m,
)

/** Failures, and "Task deleted" with Undo, as snackbars, until the calling effect ends. */
private suspend fun showTaskMessages(
    viewModel: TasksViewModel,
    snackbarHostState: SnackbarHostState,
    context: Context,
) = coroutineScope {
    launch {
        viewModel.operationFailures.collect { failure ->
            snackbarHostState.showSnackbar(context.getString(failure.message()), duration = SnackbarDuration.Long)
        }
    }
    launch {
        viewModel.deletedTasks.collect { task ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.task_deleted),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreTask(task)
        }
    }
}

/** The list: a skeleton while loading, else progress, chips, open tasks and the Done section. */
@Suppress("LongParameterList") // One lazy-list builder fed by the screen's callbacks.
private fun LazyListScope.tasksContent(
    state: TasksUiState,
    onToggle: (Long, Boolean) -> Unit,
    onOpen: (Task) -> Unit,
    onFilter: (TaskFilter) -> Unit,
    onNewTask: () -> Unit,
    onClearCompleted: () -> Unit,
) {
    if (state.isLoading) {
        items(SKELETON_ROWS) { RowSkeleton() }
        return
    }
    if (state.totalTasksCount == 0) {
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
    progressAndFilters(state, onFilter)
    val showingCompleted = state.selectedCompletedFilter != null
    val open = if (showingCompleted) emptyList() else state.filteredActiveTasks
    val done = when {
        showingCompleted -> state.completedTasks

        // A priority filter narrows the open tasks only; the Done section belongs to "All tasks".
        state.selectedPriorityFilter == null -> state.completedTasks

        else -> emptyList()
    }
    if (!showingCompleted && open.isEmpty()) {
        item(key = "empty") {
            EnclyEmptyState(
                icon = EnclyIcons.Checklist,
                title = stringResource(R.string.task_all_done_title).takeIf { state.selectedPriorityFilter == null },
                body = stringResource(
                    if (state.selectedPriorityFilter !=
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
    items(open, key = { it.id }) { task ->
        TaskItem(task = task, onTaskToggle = onToggle, onTaskClick = onOpen, modifier = Modifier.animateItem())
    }
    if (done.isNotEmpty()) {
        item(key = "done") {
            DoneHeader(count = done.size, onClear = onClearCompleted)
        }
        items(done, key = { it.id }) { task ->
            TaskItem(task = task, onTaskToggle = onToggle, onTaskClick = onOpen, modifier = Modifier.animateItem())
        }
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

/** What the user is told when a task operation fails. */
@StringRes
private fun TaskOperationFailure.message(): Int = when (this) {
    TaskOperationFailure.CREATE -> R.string.task_create_failed
    TaskOperationFailure.UPDATE -> R.string.task_update_failed
    TaskOperationFailure.STATUS_UPDATE -> R.string.task_status_update_failed
    TaskOperationFailure.CLEAR_COMPLETED -> R.string.task_clear_completed_failed
    TaskOperationFailure.DELETE -> R.string.task_delete_failed
}
