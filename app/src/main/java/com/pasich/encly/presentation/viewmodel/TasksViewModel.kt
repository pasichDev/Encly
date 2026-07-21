package com.pasich.encly.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.pasich.encly.core.AppLogger
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
import com.pasich.encly.utils.ReminderStore
import com.pasich.encly.utils.TaskReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
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

// Task groups by date
data class TaskGroup(
    val title: String,
    val tasks: List<Task>,
    val isOverdue: Boolean = false
)

data class TasksUiState(
    val activeTasks: List<Task> = emptyList(),
    val completedTasks: List<Task> = emptyList(),
    val taskGroups: List<TaskGroup> = emptyList(),
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
class TasksViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
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

    private val _showAddTaskDialog = MutableStateFlow(false)
    val showAddTaskDialog: StateFlow<Boolean> = _showAddTaskDialog.asStateFlow()

    private val _editingTask = MutableStateFlow<Task?>(null)
    val editingTask: StateFlow<Task?> = _editingTask.asStateFlow()


    init {
        applyPendingCompletions()
        observeTasks()
    }

    /**
     * Applies task completions that were queued from notification "Done" actions while
     * the database was locked. Runs here because the tasks screen is reached only after
     * the app is unlocked.
     */
    private fun applyPendingCompletions() {
        viewModelScope.launch {
            ReminderStore.drainPendingComplete(context).forEach { id ->
                updateTaskStatusUseCase(id, true)
            }
        }
    }

    private fun createDateFilters(tasks: List<Task>): List<TaskFilter> {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val tomorrow = today + 24 * 60 * 60 * 1000

        val todayTasks = tasks.filter { task ->
            task.reminderDate != null && task.reminderDate >= today && task.reminderDate < tomorrow
        }

        val tomorrowTasks = tasks.filter { task ->
            task.reminderDate != null && task.reminderDate >= tomorrow && task.reminderDate < tomorrow + 24 * 60 * 60 * 1000
        }

        val laterTasks = tasks.filter { task ->
            task.reminderDate != null && task.reminderDate >= tomorrow + 24 * 60 * 60 * 1000
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

        // Filter by date
        dateFilter?.let { filter ->
            when (filter.id) {
                "today" -> {
                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val tomorrow = today + 24 * 60 * 60 * 1000

                    filtered = filtered.filter { task ->
                        task.reminderDate != null && task.reminderDate >= today && task.reminderDate < tomorrow
                    }
                }

                "tomorrow" -> {
                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val tomorrow = today + 24 * 60 * 60 * 1000
                    val dayAfterTomorrow = tomorrow + 24 * 60 * 60 * 1000

                    filtered = filtered.filter { task ->
                        task.reminderDate != null && task.reminderDate >= tomorrow && task.reminderDate < dayAfterTomorrow
                    }
                }

                "later" -> {
                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val dayAfterTomorrow = today + 2 * 24 * 60 * 60 * 1000

                    filtered = filtered.filter { task ->
                        task.reminderDate != null && task.reminderDate >= dayAfterTomorrow
                    }
                }
            }
        }

        // Filter by priority
        priorityFilter?.let { filter ->
            when (filter.id) {
                "priority_high" -> filtered = filtered.filter { it.priority == 2 }
                "priority_medium" -> filtered = filtered.filter { it.priority == 1 }
                "priority_low" -> filtered = filtered.filter { it.priority == 0 }
            }
        }

        return filtered.sortedByDescending { it.priority }
    }

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
                    // Check whether there are tasks for today
                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val tomorrow = today + 24 * 60 * 60 * 1000

                    val todayTasks = activeTasks.filter { task ->
                        task.reminderDate != null && task.reminderDate >= today && task.reminderDate < tomorrow
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
                    taskGroups = emptyList(),
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
            val taskId = addTaskUseCase(task)

            // Schedule a reminder if needed
            if (reminderDate != null) {
                val insertedTask = task.copy(id = taskId)
                TaskReminderScheduler.scheduleReminder(context, insertedTask)
            }


            hideAddTaskDialog()
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

            existingTask?.let { task ->
                val updatedTask = task.copy(
                    title = title,
                    description = description,
                    reminderDate = reminderDate,
                    priority = priority,
                    categoryId = categoryId
                )

                updateTaskUseCase(updatedTask)

                // Update the reminder
                TaskReminderScheduler.cancelReminder(context, taskId)
                if (reminderDate != null) {
                    TaskReminderScheduler.scheduleReminder(context, updatedTask)
                }


                hideAddTaskDialog()
            }
        }
    }

    fun toggleTaskCompletion(taskId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            updateTaskStatusUseCase(taskId, isCompleted)

            // Cancel the reminder if the task is completed
            if (isCompleted) {
                TaskReminderScheduler.cancelReminder(context, taskId)
            }

        }
    }


    fun clearCompletedTasks() {
        viewModelScope.launch {
            deleteCompletedTasksUseCase()

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
                    taskGroups = emptyList()
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
                    taskGroups = emptyList()
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
                    taskGroups = emptyList()
                )
            }
        }
    }


    fun addTaskToCalendar(task: Task, context: Context) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, task.title)
                putExtra(CalendarContract.Events.DESCRIPTION, task.description ?: "")

                // If there is a reminder date, use it; otherwise use the current date
                val startTime = task.reminderDate ?: System.currentTimeMillis()
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startTime + 3600000) // +1 hour

                putExtra(
                    CalendarContract.Events.AVAILABILITY,
                    CalendarContract.Events.AVAILABILITY_BUSY
                )
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e("TasksViewModel", "Failed to add task to calendar", e)

        }
    }


}

