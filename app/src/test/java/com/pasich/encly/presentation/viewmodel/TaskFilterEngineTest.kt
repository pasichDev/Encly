package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskFilterEngineTest {
    private val high = Task(id = 1, title = "high", priority = 2)
    private val low = Task(id = 2, title = "low", priority = 0)
    private val medium = Task(id = 3, title = "medium", priority = 1)
    private val done = Task(id = 4, title = "done", priority = 1, isCompleted = true)

    @Test
    fun startsOnAllTasksWithOneChipPerPriorityInUse() {
        val state = TaskFilterEngine.reduce(TasksUiState(), listOf(low, high), listOf(done), 2, 1)

        assertEquals(TaskFilterEngine.ALL_FILTER_ID, state.selectedActiveFilter?.id)
        assertEquals(
            listOf("all", "priority_high", "priority_low", "completed"),
            state.availableFilters.map { it.id },
        )
        assertEquals(listOf(high, low), state.filteredActiveTasks)
        assertEquals(33, state.completionPercentage)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun aPriorityChipFiltersAndTappingItAgainClearsIt() {
        val loaded = TaskFilterEngine.reduce(TasksUiState(), listOf(low, high, medium), emptyList(), 3, 0)
        val highChip = loaded.availableFilters.first { it.id == "priority_high" }

        val filtered = TaskFilterEngine.select(loaded, highChip)
        assertEquals(listOf(high), filtered.filteredActiveTasks)

        val cleared = TaskFilterEngine.select(filtered, highChip)
        assertNull(cleared.selectedPriorityFilter)
        assertEquals(listOf(high, medium, low), cleared.filteredActiveTasks)
    }

    @Test
    fun aPriorityFilterWhoseLastTaskIsGoneIsDropped() {
        val loaded = TaskFilterEngine.reduce(TasksUiState(), listOf(low, high), emptyList(), 2, 0)
        val lowChip = loaded.availableFilters.first { it.id == "priority_low" }
        val filtered = TaskFilterEngine.select(loaded, lowChip)

        val afterCompletingLow = TaskFilterEngine.reduce(filtered, listOf(high), listOf(low), 1, 1)

        assertNull(afterCompletingLow.selectedPriorityFilter)
        assertEquals(listOf(high), afterCompletingLow.filteredActiveTasks)
    }

    @Test
    fun theCompletedFilterShowsOnlyCompletedTasksAndHidesPriorityChips() {
        val loaded = TaskFilterEngine.reduce(TasksUiState(), listOf(high), listOf(done), 1, 1)
        val completedChip = loaded.availableFilters.first { it.type == TaskFilter.Type.COMPLETED }

        val selected = TaskFilterEngine.select(loaded, completedChip)
        val reloaded = TaskFilterEngine.reduce(selected, listOf(high), listOf(done), 1, 1)

        assertEquals(listOf(done), reloaded.filteredActiveTasks)
        assertEquals(listOf("all", "completed"), reloaded.availableFilters.map { it.id })
    }

    @Test
    fun uncheckingTheLastCompletedTaskLeavesTheCompletedFilter() {
        val loaded = TaskFilterEngine.reduce(TasksUiState(), listOf(high), listOf(done), 1, 1)
        val completedChip = loaded.availableFilters.first { it.type == TaskFilter.Type.COMPLETED }
        val selected = TaskFilterEngine.select(loaded, completedChip)

        val undone = done.copy(isCompleted = false)
        val afterUncheck = TaskFilterEngine.reduce(selected, listOf(high, undone), emptyList(), 2, 0)

        assertNull(afterUncheck.selectedCompletedFilter)
        assertEquals(TaskFilterEngine.ALL_FILTER_ID, afterUncheck.selectedActiveFilter?.id)
        assertEquals(listOf(high, undone), afterUncheck.filteredActiveTasks)
        assertEquals(
            listOf("all", "priority_high", "priority_medium", "completed"),
            afterUncheck.availableFilters.map { it.id },
        )
    }

    @Test
    fun tappingCompletedAgainOrAllTasksGoesBackToTheActiveTasks() {
        val loaded = TaskFilterEngine.reduce(TasksUiState(), listOf(high, low), listOf(done), 2, 1)
        val completedChip = loaded.availableFilters.first { it.type == TaskFilter.Type.COMPLETED }
        val selected = TaskFilterEngine.select(loaded, completedChip)

        listOf(completedChip, selected.availableFilters.first { it.id == TaskFilterEngine.ALL_FILTER_ID })
            .forEach { chip ->
                val back = TaskFilterEngine.select(selected, chip)

                assertNull(back.selectedCompletedFilter)
                assertEquals(TaskFilterEngine.ALL_FILTER_ID, back.selectedActiveFilter?.id)
                assertEquals(listOf(high, low), back.filteredActiveTasks)
                assertEquals(
                    listOf("all", "priority_high", "priority_low", "completed"),
                    back.availableFilters.map { it.id },
                )
            }
    }

    @Test
    fun noTasksIsZeroPercent() {
        assertEquals(0, TaskFilterEngine.completionPercentage(0, 0))
    }
}
