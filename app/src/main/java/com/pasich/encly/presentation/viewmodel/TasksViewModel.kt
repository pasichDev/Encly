package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.usecase.task.AddTaskUseCase
import com.pasich.encly.domain.usecase.task.DeleteCompletedTasksUseCase
import com.pasich.encly.domain.usecase.task.GetActiveTasksUseCase
import com.pasich.encly.domain.usecase.task.GetCompletedTasksUseCase
import com.pasich.encly.domain.usecase.task.GetTasksCountUseCase
import com.pasich.encly.domain.usecase.task.UpdateTaskStatusUseCase
import com.pasich.encly.domain.usecase.task.UpdateTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

// Filters for tasks
data class TaskFilter(
    val id: String,
    val label: String,
    val count: Int = 0,
    val type: Type
) {
    enum class Type {
        DATE, PRIORITY, COMPLETED
    }
}

enum class TaskOperationFailure {
    CREATE,
    UPDATE,
    STATUS_UPDATE,
    CLEAR_COMPLETED
}

data class TasksUiState(
    val activeTasks: List<Task> = emptyList(),
    val completedTasks: List<Task> = emptyList(),
    val activeTasksCount: Int = 0,
    val completedTasksCount: Int = 0,
    val totalTasksCount: Int = 0,
    val completionPercentage: Int = 0,
    val availableFilters: List<TaskFilter> = emptyList(),
    val selectedDateFilter: TaskFilter? = null,
    val selectedPriorityFilter: TaskFilter? = null,
    val selectedCompletedFilter: TaskFilter? = null,
    val filteredActiveTasks: List<Task> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class TasksViewModel
@Suppress("LongParameterList") // Hilt wiring: bundling independent task use cases would hide dependencies.
@Inject constructor(
    private val getActiveTasksUseCase: GetActiveTasksUseCase,
    private val getCompletedTasksUseCase: GetCompletedTasksUseCase,
    private val getTasksCountUseCase: GetTasksCountUseCase,
    private val addTaskUseCase: AddTaskUseCase,
    private val updateTaskStatusUseCase: UpdateTaskStatusUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val deleteCompletedTasksUseCase: DeleteCompletedTasksUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    private val _operationFailures = MutableSharedFlow<TaskOperationFailure>(extraBufferCapacity = 1)
    val operationFailures: SharedFlow<TaskOperationFailure> = _operationFailures.asSharedFlow()

    private val _showAddTaskDialog = MutableStateFlow(false)
    val showAddTaskDialog: StateFlow<Boolean> = _showAddTaskDialog.asStateFlow()

    private val _editingTask = MutableStateFlow<Task?>(null)
    val editingTask: StateFlow<Task?> = _editingTask.asStateFlow()


    init {
        observeTasks()
    }

    private fun createDateFilters(tasks: List<Task>): List<TaskFilter> {
        val boundaries = currentDayBoundaries()

        val todayTasks = tasks.filter { task ->
            task.reminderDate != null &&
                task.reminderDate >= boundaries.today &&
                task.reminderDate < boundaries.tomorrow
        }
        val tomorrowTasks = tasks.filter { task ->
            task.reminderDate != null &&
                task.reminderDate >= boundaries.tomorrow &&
                task.reminderDate < boundaries.dayAfterTomorrow
        }
        val laterTasks = tasks.filter { task ->
            task.reminderDate != null && task.reminderDate >= boundaries.dayAfterTomorrow
        }

        return listOf(
            TaskFilter("all", "Всі завдання", tasks.size, TaskFilter.Type.DATE),
            TaskFilter("today", "Сьогодні", todayTasks.size, TaskFilter.Type.DATE),
            TaskFilter("tomorrow", "Завтра", tomorrowTasks.size, TaskFilter.Type.DATE),
            TaskFilter("later", "Пізніше", laterTasks.size, TaskFilter.Type.DATE)
        ).filter { it.count > 0 || it.id == "all" }
    }

    private fun createPriorityFilters(tasks: List<Task>): List<TaskFilter> {
        val highTasks = tasks.filter { it.priority == 2 }
        val mediumTasks = tasks.filter { it.priority == 1 }
        val lowTasks = tasks.filter { it.priority == 0 }

        return listOf(
            TaskFilter("priority_high", "Високий", highTasks.size, TaskFilter.Type.PRIORITY),
            TaskFilter("priority_medium", "Середній", mediumTasks.size, TaskFilter.Type.PRIORITY),
            TaskFilter("priority_low", "Низький", lowTasks.size, TaskFilter.Type.PRIORITY)
        ).filter { it.count > 0 }
    }

    private fun createCompletedFilter(completedTasks: List<Task>): TaskFilter {
        return TaskFilter("completed", "Виконані", completedTasks.size, TaskFilter.Type.COMPLETED)
    }

    private fun filterTasks(
        tasks: List<Task>,
        dateFilter: TaskFilter?,
        priorityFilter: TaskFilter?
    ): List<Task> {
        var filtered = tasks
        val boundaries = currentDayBoundaries()

        dateFilter?.let { filter ->
            filtered = when (filter.id) {
                "today" -> filtered.filter { task ->
                    task.reminderDate != null &&
                        task.reminderDate >= boundaries.today &&
                        task.reminderDate < boundaries.tomorrow
                }

                "tomorrow" -> filtered.filter { task ->
                    task.reminderDate != null &&
                        task.reminderDate >= boundaries.tomorrow &&
                        task.reminderDate < boundaries.dayAfterTomorrow
                }

                "later" -> filtered.filter { task ->
                    task.reminderDate != null &&
                        task.reminderDate >= boundaries.dayAfterTomorrow
                }

                else -> filtered
            }
        }

        priorityFilter?.let { filter ->
            filtered = when (filter.id) {
                "priority_high" -> filtered.filter { it.priority == 2 }
                "priority_medium" -> filtered.filter { it.priority == 1 }
                "priority_low" -> filtered.filter { it.priority == 0 }
                else -> filtered
            }
        }

        return filtered.sortedByDescending { it.priority }
    }

    private data class DayBoundaries(
        val today: Long,
        val tomorrow: Long,
        val dayAfterTomorrow: Long
    )

    private fun currentDayBoundaries(
        today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DayBoundaries = DayBoundaries(
        today = today.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        tomorrow = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
        dayAfterTomorrow = today.plusDays(2).atStartOfDay(zoneId).toInstant().toEpochMilli()
    )

    private fun observeTasks() {
        viewModelScope.launch {
            combine(
                getActiveTasksUseCase(),
                getCompletedTasksUseCase(),
                getTasksCountUseCase.getActiveCount(),
                getTasksCountUseCase.getCompletedCount()
            ) { activeTasks, completedTasks, activeCount, completedCount ->
                val currentState = _uiState.value

                // Automatically set the initial filter if none is selected
                val (selectedDateFilter, selectedCompletedFilter) = if (
                    currentState.selectedDateFilter == null &&
                    currentState.selectedCompletedFilter == null
                ) {
                    val boundaries = currentDayBoundaries()
                    val todayTasks = activeTasks.filter { task ->
                        task.reminderDate != null &&
                            task.reminderDate >= boundaries.today &&
                            task.reminderDate < boundaries.tomorrow
                    }

                    if (todayTasks.isNotEmpty()) {
                        // There are tasks for today - select "Today"
                        TaskFilter(
                            "today",
                            "Сьогодні",
                            todayTasks.size,
                            TaskFilter.Type.DATE
                        ) to null
                    } else {
                        // No tasks for today - select "All tasks"
                        TaskFilter(
                            "all",
                            "Всі завдання",
                            activeTasks.size,
                            TaskFilter.Type.DATE
                        ) to null
                    }
                } else {
                    currentState.selectedDateFilter to currentState.selectedCompletedFilter
                }

                val filteredTasks = if (selectedCompletedFilter != null) {
                    completedTasks.sortedByDescending { it.priority }
                } else {
                    filterTasks(
                        activeTasks,
                        selectedDateFilter,
                        currentState.selectedPriorityFilter
                    )
                }

                val dateFilters = createDateFilters(activeTasks)
                val priorityFilters = createPriorityFilters(activeTasks)
                val completedFilter = createCompletedFilter(completedTasks)

                // If the completed filter is selected, do not show the priority filters
                val availableFilters = if (selectedCompletedFilter != null) {
                    listOf(completedFilter)
                } else {
                    dateFilters + priorityFilters + listOf(completedFilter)
                }

                val totalTasks = activeCount + completedCount
                val completionPercentage = if (totalTasks > 0) {
                    (completedCount * 100) / totalTasks
                } else 0

                TasksUiState(
                    activeTasks = activeTasks.sortedByDescending { it.priority },
                    completedTasks = completedTasks.sortedByDescending { it.priority },
                    filteredActiveTasks = filteredTasks,
                    activeTasksCount = activeCount,
                    completedTasksCount = completedCount,
                    totalTasksCount = totalTasks,
                    completionPercentage = completionPercentage,
                    availableFilters = availableFilters,
                    selectedDateFilter = selectedDateFilter,
                    selectedPriorityFilter = currentState.selectedPriorityFilter,
                    selectedCompletedFilter = selectedCompletedFilter,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun showAddTaskDialog() {
        _editingTask.value = null
        _showAddTaskDialog.value = true
    }

    fun showEditTaskDialog(task: Task) {
        _editingTask.value = task
        _showAddTaskDialog.value = true
    }

    fun hideAddTaskDialog() {
        _showAddTaskDialog.value = false
        _editingTask.value = null
    }


    fun addTask(
        title: String,
        description: String?,
        reminderDate: Long?,
        priority: Int,
        categoryId: Long? = null
    ) {
        viewModelScope.launch {
            val task = Task.new(
                title = title,
                description = description,
                reminderDate = reminderDate,
                priority = priority,
                categoryId = categoryId
            )
            if (addTaskUseCase(task) > 0L) {
                hideAddTaskDialog()
            } else {
                _operationFailures.emit(TaskOperationFailure.CREATE)
            }
        }
    }

    fun editTask(
        taskId: Long,
        title: String,
        description: String?,
        reminderDate: Long?,
        priority: Int,
        categoryId: Long? = null
    ) {
        viewModelScope.launch {
            val existingTask = uiState.value.activeTasks.find { it.id == taskId }
                ?: uiState.value.completedTasks.find { it.id == taskId }

            if (existingTask == null) {
                _operationFailures.emit(TaskOperationFailure.UPDATE)
                return@launch
            }

            val updatedTask = existingTask.copy(
                title = title,
                description = description,
                reminderDate = reminderDate,
                priority = priority,
                categoryId = categoryId
            )

            if (updateTaskUseCase(updatedTask)) {
                hideAddTaskDialog()
            } else {
                _operationFailures.emit(TaskOperationFailure.UPDATE)
            }
        }
    }

    fun toggleTaskCompletion(taskId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            if (!updateTaskStatusUseCase(taskId, isCompleted)) {
                _operationFailures.emit(TaskOperationFailure.STATUS_UPDATE)
            }
        }
    }


    fun clearCompletedTasks(onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (deleteCompletedTasksUseCase()) {
                onSuccess()
            } else {
                _operationFailures.emit(TaskOperationFailure.CLEAR_COMPLETED)
            }
        }
    }

    fun onFilterSelected(filter: TaskFilter) {
        val currentState = _uiState.value

        when (filter.type) {
            TaskFilter.Type.DATE -> {
                // Do not allow resetting the date filter if it is already selected
                val newDateFilter = if (currentState.selectedDateFilter?.id == filter.id) {
                    currentState.selectedDateFilter // Keep it selected
                } else {
                    filter
                }
                val filteredTasks = filterTasks(
                    currentState.activeTasks,
                    newDateFilter,
                    currentState.selectedPriorityFilter
                )

                _uiState.value = currentState.copy(
                    selectedDateFilter = newDateFilter,
                    selectedCompletedFilter = null, // Reset the completed filter
                    filteredActiveTasks = filteredTasks.sortedByDescending { it.priority },
                )
            }

            TaskFilter.Type.PRIORITY -> {
                val newPriorityFilter =
                    if (currentState.selectedPriorityFilter?.id == filter.id) null else filter
                val filteredTasks = filterTasks(
                    currentState.activeTasks,
                    currentState.selectedDateFilter,
                    newPriorityFilter
                )

                _uiState.value = currentState.copy(
                    selectedPriorityFilter = newPriorityFilter,
                    selectedCompletedFilter = null, // Reset the completed filter
                    filteredActiveTasks = filteredTasks.sortedByDescending { it.priority },
                )
            }

            TaskFilter.Type.COMPLETED -> {
                // Do not allow resetting the COMPLETED filter if it is already selected
                val newCompletedFilter =
                    if (currentState.selectedCompletedFilter?.id == filter.id) {
                        currentState.selectedCompletedFilter // Keep it selected
                    } else {
                        filter
                    }

                val filteredTasks = if (newCompletedFilter != null) {
                    currentState.completedTasks
                } else {
                    // If resetting COMPLETED, select "All tasks" by default
                    TaskFilter(
                        "all",
                        "Всі завдання",
                        currentState.activeTasks.size,
                        TaskFilter.Type.DATE
                    )
                    currentState.activeTasks
                }

                _uiState.value = currentState.copy(
                    selectedDateFilter = if (newCompletedFilter != null) null else
                        TaskFilter(
                            "all",
                            "Всі завдання",
                            currentState.activeTasks.size,
                            TaskFilter.Type.DATE
                        ),
                    selectedPriorityFilter = null,
                    selectedCompletedFilter = newCompletedFilter,
                    filteredActiveTasks = filteredTasks.sortedByDescending { it.priority },
                )
            }
        }
    }


}

