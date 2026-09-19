package com.pasich.encly.presentation.screen

import android.widget.Toast

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash
import com.pasich.encly.R
import com.pasich.encly.presentation.components.tasks.TaskFilterChips
import com.pasich.encly.presentation.components.tasks.TaskProgressCard
import com.pasich.encly.presentation.components.tiles.TaskItem
import com.pasich.encly.presentation.dialogs.RequestCleanCompleteTaskDialog
import com.pasich.encly.presentation.dialogs.tasks.AddTaskDialog
import com.pasich.encly.presentation.viewmodel.TaskFilter
import com.pasich.encly.presentation.viewmodel.TaskOperationFailure
import com.pasich.encly.presentation.viewmodel.TasksViewModel

data class TasksScreenUiState(
    val showDialogCleanComplete: Boolean = false
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    navController: NavHostController,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showAddTaskDialog by viewModel.showAddTaskDialog.collectAsState()
    val editingTask by viewModel.editingTask.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.operationFailures.collect { failure ->
            val message = when (failure) {
                TaskOperationFailure.CREATE -> context.getString(R.string.task_create_failed)
                TaskOperationFailure.UPDATE -> context.getString(R.string.task_update_failed)
                TaskOperationFailure.STATUS_UPDATE ->
                    context.getString(R.string.task_status_update_failed)
                TaskOperationFailure.CLEAR_COMPLETED ->
                    context.getString(R.string.task_clear_completed_failed)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Centralized UI state
    var screenUiState by remember { mutableStateOf(TasksScreenUiState()) }

    // Scroll state for LazyColumn
    val lazyListState = rememberLazyListState()

    // Reset scroll to the top when filters change
    LaunchedEffect(
        uiState.selectedDateFilter,
        uiState.selectedPriorityFilter,
        uiState.selectedCompletedFilter
    ) {
        if (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0) {
            lazyListState.animateScrollToItem(0)
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )


    val isShowingCompletedTasks = uiState.selectedCompletedFilter != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_drawer_tasks)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                containerColor = if (isShowingCompletedTasks) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                onClick = {
                    if (isShowingCompletedTasks)
                        screenUiState = screenUiState.copy(
                            showDialogCleanComplete = true,
                        )
                    else viewModel.showAddTaskDialog()

                }
            ) {
                Icon(
                    if (isShowingCompletedTasks) Lucide.Trash else Icons.Default.Add,
                    contentDescription = if (isShowingCompletedTasks) "Очистити всі виконані" else "Додати завдання"
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {

            // Progress panel
            if (uiState.totalTasksCount > 0) {
                TaskProgressCard(
                    completionPercentage = uiState.completionPercentage,
                    completedTasksCount = uiState.completedTasksCount,
                    totalTasksCount = uiState.totalTasksCount
                )

            }

            // Task filters
            TaskFilterChips(
                availableFilters = uiState.availableFilters,
                selectedDateFilter = uiState.selectedDateFilter,
                selectedPriorityFilter = uiState.selectedPriorityFilter,
                selectedCompletedFilter = uiState.selectedCompletedFilter,
                onFilterSelected = viewModel::onFilterSelected,
                modifier = Modifier.padding(vertical = 8.dp)
            )



            Spacer(modifier = Modifier.height(2.dp))



            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (uiState.filteredActiveTasks.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Image(
                            painter = painterResource(R.drawable.ic_empty_task),
                            modifier = Modifier
                                .size(180.dp)
                                .padding(vertical = 15.dp),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(
                                color = MaterialTheme.colorScheme.onSurface,
                                blendMode = BlendMode.SrcIn
                            )

                        )
                        Text(
                            text = when {
                                uiState.selectedCompletedFilter != null -> "Немає виконаних завдань."
                                uiState.selectedDateFilter != null || uiState.selectedPriorityFilter != null -> "Немає завдань за вибраними фільтрами."
                                else -> "Немає активних завдань."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Filtered tasks
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(uiState.filteredActiveTasks, key = { it.id }) { task ->
                        TaskItem(
                            task = task,
                            onTaskToggle = viewModel::toggleTaskCompletion,
                            onTaskClick = { selectedTask ->
                                viewModel.showEditTaskDialog(selectedTask)
                            }
                        )
                    }
                }

            }

        }

        if (showAddTaskDialog) {
            AddTaskDialog(
                onDismiss = { viewModel.hideAddTaskDialog() },
                onAddTask = { title, description, reminderDate, priority ->
                    viewModel.addTask(title, description, reminderDate, priority)
                },
                editTask = editingTask,
                onEditTask = { taskId, title, description, reminderDate, priority ->
                    viewModel.editTask(taskId, title, description, reminderDate, priority)
                },
                sheetState = sheetState
            )
        }
        RequestCleanCompleteTaskDialog(
            showDialog = screenUiState.showDialogCleanComplete,
            onDismiss = {
                screenUiState = screenUiState.copy(showDialogCleanComplete = false)
            },
            onConfirm = {
                viewModel.clearCompletedTasks()
                screenUiState = screenUiState.copy(showDialogCleanComplete = false)
            })

    }
}
