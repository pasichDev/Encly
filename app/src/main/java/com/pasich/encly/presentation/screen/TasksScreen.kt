package com.pasich.encly.presentation.screen

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.presentation.designsystem.EnclyFab
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclySnackbarHost
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.dialogs.RequestCleanCompleteTaskDialog
import com.pasich.encly.presentation.dialogs.tasks.AddTaskDialog
import com.pasich.encly.presentation.viewmodel.TaskOperationFailure
import com.pasich.encly.presentation.viewmodel.TasksUiState
import com.pasich.encly.presentation.viewmodel.TasksViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
    val list = TasksListState(uiState, viewModel.expandedTaskIds.collectAsState().value, viewModel.inlineEdit)
    val itemActions = remember(viewModel) { taskItemActions(viewModel) }
    // Backgrounding re-locks the vault and drops this screen: save the inline field first (see OnPause).
    OnPause(viewModel::flushInlineEditForBackground)
    FoldTreesOnBack(list, viewModel::collapseLastExpanded)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { EnclySnackbarHost(snackbarHostState) },
        topBar = {
            EnclyTopBar(
                title = stringResource(R.string.main_drawer_tasks),
                onBack = {
                    viewModel.commitInlineEdit()
                    navController.popBackStack()
                },
            )
        },
        floatingActionButton = {
            // Out of the way of the keyboard and the inline field while one is open.
            if (list.inlineEdit == null) TasksFab(listState, viewModel::showAddTaskDialog)
        },
    ) { padding ->
        TasksList(
            state = listState,
            list = list,
            actions = itemActions,
            callbacks = TasksListCallbacks(
                onFilter = viewModel::onFilterSelected,
                onNewTask = viewModel::showAddTaskDialog,
                onClearCompleted = { confirmClear = true },
            ),
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )

        if (viewModel.showAddTaskDialog.collectAsState().value) {
            AddTaskDialog(
                onDismiss = viewModel::hideAddTaskDialog,
                onAddTask = viewModel::addTask,
                editTask = viewModel.editingTask.collectAsState().value,
                onEditTask = viewModel::editTask,
                onBackgroundSave = viewModel::saveDraftForBackground,
                onDeleteTask = viewModel::deleteTask,
                subtasks = viewModel.checklist,
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

/** "New task", extended while the list is at its top. */
@Composable
private fun TasksFab(listState: LazyListState, onClick: () -> Unit) {
    val expanded by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    EnclyFab(text = stringResource(R.string.task_add), icon = EnclyIcons.Plus, expanded = expanded, onClick = onClick)
}

/**
 * Runs [action] whenever the screen's lifecycle pauses because the app leaves the foreground,
 * not for a configuration change (the activity is only recreated). ON_PAUSE comes well before
 * the re-lock: that waits for the process to stop and then for the auto-lock delay.
 */
@Composable
private fun OnPause(action: () -> Unit) {
    val currentAction by rememberUpdatedState(action)
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalActivity.current
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && activity?.isChangingConfigurations != true) currentAction()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

/**
 * Failures, "Task deleted" and "Sub-task deleted" with Undo, and "All sub-tasks done" with
 * Complete task, as snackbars, until the calling effect ends.
 */
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
    launch {
        viewModel.subtaskDeletions.collect { subtask ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.subtask_deleted),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreSubtask(subtask)
        }
    }
    launch {
        viewModel.completionOffers.collect { taskId ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.subtasks_all_done),
                actionLabel = context.getString(R.string.subtasks_complete_task),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.toggleTaskCompletion(taskId, true)
        }
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
