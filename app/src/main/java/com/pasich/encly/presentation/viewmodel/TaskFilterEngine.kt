package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.R
import com.pasich.encly.data.model.Task

/**
 * The task list's filtering and counting, as pure functions of the stored tasks and the
 * current selection, so it is testable without a ViewModel or a database.
 */
internal object TaskFilterEngine {
    const val ALL_FILTER_ID = "all"
    private const val COMPLETED_FILTER_ID = "completed"
    private const val PERCENT = 100

    private enum class PriorityChip(val id: String, val priority: Int, val label: Int) {
        HIGH("priority_high", 2, R.string.priority_high),
        MEDIUM("priority_medium", 1, R.string.priority_medium),
        LOW("priority_low", 0, R.string.priority_low),
    }

    /**
     * The screen state for freshly loaded tasks, keeping the user's filter selection from
     * [current] where it still applies.
     */
    fun reduce(
        current: TasksUiState,
        activeTasks: List<Task>,
        completedTasks: List<Task>,
        activeCount: Int,
        completedCount: Int,
    ): TasksUiState {
        // Start on "All tasks" until the user picks a filter
        val (selectedActiveFilter, selectedCompletedFilter) = if (
            current.selectedActiveFilter == null && current.selectedCompletedFilter == null
        ) {
            allFilter(activeTasks) to null
        } else {
            current.selectedActiveFilter to current.selectedCompletedFilter
        }

        val priorityFilters = priorityFilters(activeTasks)
        val completedFilter = completedFilter(completedTasks)

        // A priority filter whose last task was completed or deleted loses its chip;
        // drop the selection with it instead of filtering by an invisible chip.
        val selectedPriorityFilter = current.selectedPriorityFilter
            ?.takeIf { selected -> priorityFilters.any { it.id == selected.id } }

        val filteredTasks = if (selectedCompletedFilter != null) {
            completedTasks.sortedByDescending { it.priority }
        } else {
            filterTasks(activeTasks, selectedPriorityFilter)
        }

        // If the completed filter is selected, do not show the priority filters
        val availableFilters = if (selectedCompletedFilter != null) {
            listOf(completedFilter)
        } else {
            listOf(allFilter(activeTasks)) + priorityFilters + listOf(completedFilter)
        }

        val totalTasks = activeCount + completedCount
        return TasksUiState(
            activeTasks = activeTasks.sortedByDescending { it.priority },
            completedTasks = completedTasks.sortedByDescending { it.priority },
            filteredActiveTasks = filteredTasks,
            activeTasksCount = activeCount,
            completedTasksCount = completedCount,
            totalTasksCount = totalTasks,
            completionPercentage = completionPercentage(completedCount, totalTasks),
            availableFilters = availableFilters,
            selectedActiveFilter = selectedActiveFilter,
            selectedPriorityFilter = selectedPriorityFilter,
            selectedCompletedFilter = selectedCompletedFilter,
            isLoading = false,
        )
    }

    /** The state after the user taps [filter]. */
    fun select(current: TasksUiState, filter: TaskFilter): TasksUiState = when (filter.type) {
        TaskFilter.Type.ACTIVE -> current.copy(
            selectedActiveFilter = filter,
            selectedCompletedFilter = null, // Reset the completed filter
            filteredActiveTasks = filterTasks(current.activeTasks, current.selectedPriorityFilter),
        )

        TaskFilter.Type.PRIORITY -> {
            val newPriorityFilter = if (current.selectedPriorityFilter?.id == filter.id) null else filter
            current.copy(
                selectedPriorityFilter = newPriorityFilter,
                selectedCompletedFilter = null, // Reset the completed filter
                filteredActiveTasks = filterTasks(current.activeTasks, newPriorityFilter),
            )
        }

        TaskFilter.Type.COMPLETED -> current.copy(
            selectedActiveFilter = null,
            selectedPriorityFilter = null,
            selectedCompletedFilter = filter,
            filteredActiveTasks = current.completedTasks.sortedByDescending { it.priority },
        )
    }

    /** The tasks [priorityFilter] keeps (all of them for no filter), highest priority first. */
    fun filterTasks(tasks: List<Task>, priorityFilter: TaskFilter?): List<Task> {
        val chip = PriorityChip.entries.firstOrNull { it.id == priorityFilter?.id }
        val filtered = if (chip == null) tasks else tasks.filter { it.priority == chip.priority }
        return filtered.sortedByDescending { it.priority }
    }

    /** One chip per priority that has active tasks, highest first. */
    fun priorityFilters(tasks: List<Task>): List<TaskFilter> = PriorityChip.entries.map { chip ->
        TaskFilter(chip.id, chip.label, tasks.count { it.priority == chip.priority }, TaskFilter.Type.PRIORITY)
    }.filter { it.count > 0 }

    fun completionPercentage(completedCount: Int, totalCount: Int): Int = if (totalCount >
        0
    ) {
        (completedCount * PERCENT) / totalCount
    } else {
        0
    }

    private fun allFilter(tasks: List<Task>): TaskFilter =
        TaskFilter(ALL_FILTER_ID, R.string.task_filter_all, tasks.size, TaskFilter.Type.ACTIVE)

    private fun completedFilter(completedTasks: List<Task>): TaskFilter =
        TaskFilter(COMPLETED_FILTER_ID, R.string.task_filter_completed, completedTasks.size, TaskFilter.Type.COMPLETED)
}
