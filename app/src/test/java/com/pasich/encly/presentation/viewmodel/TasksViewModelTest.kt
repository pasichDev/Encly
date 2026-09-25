package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.usecase.task.UpdateTaskStatusUseCase
import com.pasich.encly.testutil.TestTasksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Tasks screen: loading, create/edit/complete/delete/undo, clearing and failures. */
@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: TestTasksRepository
    private lateinit var viewModel: TasksViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = TestTasksRepository(
            listOf(
                Task(id = 1, title = "Low", priority = 0),
                Task(id = 2, title = "High", priority = 2),
                Task(id = 3, title = "Done", isCompleted = true, completedDate = 5L),
            ),
        )
        viewModel = TasksViewModel(repository, UpdateTaskStatusUseCase(repository))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadingEndsWithTheTasksSplitAndCountedHighestPriorityFirst() = runTest(dispatcher) {
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("High", "Low"), state.activeTasks.map { it.title })
        assertEquals(listOf("Done"), state.completedTasks.map { it.title })
        assertEquals(2, state.activeTasksCount)
        assertEquals(1, state.completedTasksCount)
        assertEquals(3, state.totalTasksCount)
        assertEquals(33, state.completionPercentage)
        assertEquals(TaskFilterEngine.ALL_FILTER_ID, state.selectedActiveFilter?.id)
    }

    @Test
    fun anEmptyListIsLoadedNotLoading() = runTest(dispatcher) {
        repository.tasks.value = emptyList()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.filteredActiveTasks.isEmpty())
        assertEquals(0, state.completionPercentage)
    }

    @Test
    fun theSheetOpensEmptyForANewTaskAndWithTheTaskForAnEdit() = runTest(dispatcher) {
        val task = Task(id = 1, title = "Low")

        viewModel.showEditTaskDialog(task)
        assertTrue(viewModel.showAddTaskDialog.value)
        assertEquals(task, viewModel.editingTask.value)

        viewModel.showAddTaskDialog()
        assertTrue(viewModel.showAddTaskDialog.value)
        assertNull(viewModel.editingTask.value)

        viewModel.hideAddTaskDialog()
        assertFalse(viewModel.showAddTaskDialog.value)
    }

    @Test
    fun addingATaskStoresItAndClosesTheSheet() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.showAddTaskDialog()

        viewModel.addTask("Call mom", "tonight", priority = 1)
        advanceUntilIdle()

        val added = repository.tasks.value.single { it.title == "Call mom" }
        assertEquals("tonight", added.description)
        assertEquals(1, added.priority)
        assertFalse(added.isCompleted)
        assertFalse(viewModel.showAddTaskDialog.value)
        assertTrue(viewModel.uiState.value.activeTasks.any { it.title == "Call mom" })
    }

    @Test
    fun aFailedAddKeepsTheSheetOpenAndReportsIt() = runTest(dispatcher) {
        val failures = collectFailures()
        repository.failInsert = true
        viewModel.showAddTaskDialog()

        viewModel.addTask("Call mom", null, priority = 0)
        advanceUntilIdle()

        assertEquals(listOf(TaskOperationFailure.CREATE), failures)
        assertTrue(viewModel.showAddTaskDialog.value)
    }

    @Test
    fun editingKeepsTheStoredCategoryWhenTheSheetSendsNone() = runTest(dispatcher) {
        repository.tasks.value = listOf(Task(id = 7, title = "Old", categoryId = 4))
        advanceUntilIdle()
        viewModel.showEditTaskDialog(repository.tasks.value.single())

        viewModel.editTask(7, "New", "desc", priority = 2)
        advanceUntilIdle()

        val edited = repository.tasks.value.single()
        assertEquals("New", edited.title)
        assertEquals("desc", edited.description)
        assertEquals(2, edited.priority)
        assertEquals(4L, edited.categoryId)
        assertFalse(viewModel.showAddTaskDialog.value)
    }

    @Test
    fun aCompletedTaskCanBeEditedToo() = runTest(dispatcher) {
        advanceUntilIdle()

        viewModel.editTask(3, "Done, renamed", null, priority = 0)
        advanceUntilIdle()

        assertEquals("Done, renamed", repository.tasks.value.single { it.id == 3L }.title)
    }

    @Test
    fun editingATaskThatIsGoneOrFailsIsReported() = runTest(dispatcher) {
        val failures = collectFailures()
        advanceUntilIdle()

        viewModel.editTask(99, "Ghost", null, priority = 0)
        advanceUntilIdle()
        repository.failUpdate = true
        viewModel.editTask(1, "Low!", null, priority = 0)
        advanceUntilIdle()

        assertEquals(listOf(TaskOperationFailure.UPDATE, TaskOperationFailure.UPDATE), failures)
        assertEquals("Low", repository.tasks.value.single { it.id == 1L }.title)
    }

    @Test
    fun completingATaskRecordsWhenAndReopeningClearsIt() = runTest(dispatcher) {
        advanceUntilIdle()

        viewModel.toggleTaskCompletion(1, isCompleted = true)
        advanceUntilIdle()
        val (_, completed, date) = repository.statusUpdates.last()
        assertTrue(completed)
        assertNotNull(date)
        assertEquals(2, viewModel.uiState.value.completedTasksCount)

        viewModel.toggleTaskCompletion(1, isCompleted = false)
        advanceUntilIdle()
        assertNull(repository.statusUpdates.last().third)
        assertEquals(1, viewModel.uiState.value.completedTasksCount)
    }

    @Test
    fun aFailedStatusChangeIsReported() = runTest(dispatcher) {
        val failures = collectFailures()
        repository.failStatus = true

        viewModel.toggleTaskCompletion(1, isCompleted = true)
        advanceUntilIdle()

        assertEquals(listOf(TaskOperationFailure.STATUS_UPDATE), failures)
    }

    @Test
    fun deletingOffersUndoAndUndoPutsTheSameTaskBack() = runTest(dispatcher) {
        advanceUntilIdle()
        val deleted = mutableListOf<Task>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.deletedTasks.collect(deleted::add) }
        val task = repository.tasks.value.single { it.id == 2L }
        viewModel.showEditTaskDialog(task)

        viewModel.deleteTask(task)
        advanceUntilIdle()

        assertEquals(listOf(task), deleted)
        assertFalse(viewModel.showAddTaskDialog.value)
        assertTrue(repository.tasks.value.none { it.id == 2L })

        viewModel.restoreTask(task)
        advanceUntilIdle()

        assertEquals(task, repository.tasks.value.single { it.id == 2L })
    }

    @Test
    fun failedDeleteAndFailedUndoAreReported() = runTest(dispatcher) {
        val failures = collectFailures()
        repository.failDelete = true
        viewModel.deleteTask(Task(id = 1, title = "Low"))
        advanceUntilIdle()
        repository.failInsert = true
        viewModel.restoreTask(Task(id = 1, title = "Low"))
        advanceUntilIdle()

        assertEquals(listOf(TaskOperationFailure.DELETE, TaskOperationFailure.CREATE), failures)
        assertEquals(3, repository.tasks.value.size)
    }

    @Test
    fun clearingCompletedTasksRemovesOnlyThoseAndConfirms() = runTest(dispatcher) {
        var cleared = false

        viewModel.clearCompletedTasks { cleared = true }
        advanceUntilIdle()

        assertTrue(cleared)
        assertEquals(listOf(1L, 2L), repository.tasks.value.map { it.id })
        assertEquals(0, viewModel.uiState.value.completedTasksCount)
    }

    @Test
    fun aFailedClearIsReportedAndDoesNotConfirm() = runTest(dispatcher) {
        val failures = collectFailures()
        repository.failClear = true
        var cleared = false

        viewModel.clearCompletedTasks { cleared = true }
        advanceUntilIdle()

        assertFalse(cleared)
        assertEquals(listOf(TaskOperationFailure.CLEAR_COMPLETED), failures)
    }

    @Test
    fun aPriorityFilterNarrowsTheListAndSurvivesAReload() = runTest(dispatcher) {
        advanceUntilIdle()
        val high = viewModel.uiState.value.availableFilters.single { it.id == "priority_high" }

        viewModel.onFilterSelected(high)
        assertEquals(listOf("High"), viewModel.uiState.value.filteredActiveTasks.map { it.title })

        viewModel.addTask("Also high", null, priority = 2)
        advanceUntilIdle()

        assertEquals(high.id, viewModel.uiState.value.selectedPriorityFilter?.id)
        assertEquals(setOf("High", "Also high"), viewModel.uiState.value.filteredActiveTasks.map { it.title }.toSet())
    }

    @Test
    fun theCompletedFilterShowsTheDoneTasks() = runTest(dispatcher) {
        advanceUntilIdle()
        val completed = viewModel.uiState.value.availableFilters.single { it.type == TaskFilter.Type.COMPLETED }

        viewModel.onFilterSelected(completed)

        assertEquals(listOf("Done"), viewModel.uiState.value.filteredActiveTasks.map { it.title })
    }

    private fun TestScope.collectFailures(): List<TaskOperationFailure> {
        val failures = mutableListOf<TaskOperationFailure>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.operationFailures.collect(failures::add)
        }
        return failures
    }
}
