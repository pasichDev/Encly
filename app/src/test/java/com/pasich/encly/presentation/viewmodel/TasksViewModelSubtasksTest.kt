package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Subtask
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Sub-tasks on the Tasks screen: editing, progress, the completion offer, delete and undo. */
@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelSubtasksTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: TestTasksRepository
    private lateinit var viewModel: TasksViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = TestTasksRepository(
            initial = listOf(Task(id = 1, title = "Shop"), Task(id = 2, title = "Call")),
            initialSubtasks = listOf(
                Subtask(id = 10, taskId = 1, title = "Milk", isCompleted = true, position = 0, uid = "u10"),
                Subtask(id = 11, taskId = 1, title = "Eggs", position = 1, uid = "u11"),
            ),
        )
        viewModel = TasksViewModel(repository, UpdateTaskStatusUseCase(repository))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theListGetsEachTasksSubtasksInOrder() = runTest(dispatcher) {
        advanceUntilIdle()

        val byTask = viewModel.uiState.value.subtasks
        assertEquals(setOf(1L), byTask.keys)
        assertEquals(listOf("Milk", "Eggs"), byTask.getValue(1L).map { it.title })
    }

    @Test
    fun tickingTheLastOpenSubtaskInTheListSavesItAndOffersToComplete() = runTest(dispatcher) {
        val offers = collectOffers()
        advanceUntilIdle()
        val eggs = viewModel.uiState.value.subtasks.getValue(1L)[1]

        viewModel.toggleSubtask(eggs, done = true)
        advanceUntilIdle()

        assertTrue(repository.subtasks.value.single { it.id == 11L }.isCompleted)
        assertEquals(listOf(1L), offers)
        assertFalse(repository.tasks.value.single { it.id == 1L }.isCompleted)
        assertTrue(viewModel.uiState.value.subtasks.getValue(1L).all { it.isCompleted })
        assertFalse(viewModel.showAddTaskDialog.value)
    }

    @Test
    fun untickingInTheListSavesWithoutAnOffer() = runTest(dispatcher) {
        val offers = collectOffers()
        advanceUntilIdle()
        val milk = viewModel.uiState.value.subtasks.getValue(1L)[0]

        viewModel.toggleSubtask(milk, done = false)
        advanceUntilIdle()

        assertFalse(repository.subtasks.value.single { it.id == 10L }.isCompleted)
        assertTrue(offers.isEmpty())
    }

    @Test
    fun tickingAnOpenSubtaskThatLeavesOthersOpenDoesNotOffer() = runTest(dispatcher) {
        repository.subtasks.value += Subtask(id = 12, taskId = 1, title = "Bread", position = 2, uid = "u12")
        val offers = collectOffers()
        advanceUntilIdle()

        viewModel.toggleSubtask(repository.subtasks.value.single { it.id == 11L }, done = true)
        advanceUntilIdle()

        assertTrue(repository.subtasks.value.single { it.id == 11L }.isCompleted)
        assertTrue(offers.isEmpty())
    }

    @Test
    fun tickingTheLastSubtaskOfACompletedTaskDoesNotOffer() = runTest(dispatcher) {
        repository.tasks.value = repository.tasks.value.map { if (it.id == 1L) it.copy(isCompleted = true) else it }
        val offers = collectOffers()
        advanceUntilIdle()

        viewModel.toggleSubtask(repository.subtasks.value.single { it.id == 11L }, done = true)
        advanceUntilIdle()

        assertTrue(repository.subtasks.value.all { it.isCompleted })
        assertTrue(offers.isEmpty())
    }

    @Test
    fun aFailedListTickIsReported() = runTest(dispatcher) {
        val failures = mutableListOf<TaskOperationFailure>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.operationFailures.collect(failures::add)
        }
        val offers = collectOffers()
        advanceUntilIdle()
        repository.failSubtasks = true

        viewModel.toggleSubtask(repository.subtasks.value.single { it.id == 11L }, done = true)
        advanceUntilIdle()

        assertEquals(listOf(TaskOperationFailure.STATUS_UPDATE), failures)
        assertFalse(repository.subtasks.value.single { it.id == 11L }.isCompleted)
        assertTrue(offers.isEmpty())
    }

    @Test
    fun editingLoadsTheChecklistAfterTheSheetOpens() = runTest(dispatcher) {
        viewModel.showEditTaskDialog(repository.tasks.value.first())
        assertNull(viewModel.editingSubtasks.value)

        advanceUntilIdle()

        assertEquals(listOf("Milk", "Eggs"), viewModel.editingSubtasks.value?.map { it.title })

        viewModel.showAddTaskDialog()
        assertEquals(emptyList<SubtaskDraft>(), viewModel.editingSubtasks.value)
    }

    @Test
    fun aNewTaskIsSavedWithItsSubtasks() = runTest(dispatcher) {
        viewModel.addTask(
            "Trip",
            null,
            priority = 0,
            subtasks = listOf(SubtaskDraft(key = -1, title = "Tickets"), SubtaskDraft(key = -2, title = " ")),
        )
        advanceUntilIdle()

        val trip = repository.tasks.value.single { it.title == "Trip" }
        assertEquals(listOf("Tickets"), repository.subtasks.value.filter { it.taskId == trip.id }.map { it.title })
        assertFalse(viewModel.showAddTaskDialog.value)
    }

    @Test
    fun editingWithoutALoadedChecklistLeavesItAlone() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.editTask(1, "Shop!", null, priority = 0)
        advanceUntilIdle()

        assertEquals(listOf("Milk", "Eggs"), repository.subtasks.value.map { it.title })
    }

    @Test
    fun tickingTheLastOpenSubtaskOffersToCompleteButDoesNotComplete() = runTest(dispatcher) {
        val offers = collectOffers()
        viewModel.showEditTaskDialog(repository.tasks.value.first())
        advanceUntilIdle()
        val ticked = viewModel.editingSubtasks.value!!.map { it.copy(isCompleted = true) }

        viewModel.editTask(1, "Shop", null, priority = 0, subtasks = ticked)
        advanceUntilIdle()

        assertEquals(listOf(1L), offers)
        assertFalse(repository.tasks.value.single { it.id == 1L }.isCompleted)
        assertTrue(repository.subtasks.value.all { it.isCompleted })

        // Saving again with nothing newly ticked does not offer twice.
        viewModel.showEditTaskDialog(repository.tasks.value.first())
        advanceUntilIdle()
        viewModel.editTask(1, "Shop", null, priority = 0, subtasks = viewModel.editingSubtasks.value)
        advanceUntilIdle()
        assertEquals(listOf(1L), offers)
    }

    @Test
    fun reorderingAndRemovingKeepTheRowsIdentity() = runTest(dispatcher) {
        viewModel.showEditTaskDialog(repository.tasks.value.first())
        advanceUntilIdle()
        val loaded = viewModel.editingSubtasks.value!!
        val edited = SubtaskDrafts.move(loaded, 1, 0) + SubtaskDraft(key = -1, title = "Bread")

        viewModel.editTask(1, "Shop", null, priority = 0, subtasks = edited.filterNot { it.title == "Milk" })
        advanceUntilIdle()

        val rows = repository.subtasks.value.filter { it.taskId == 1L }.sortedBy { it.position }
        assertEquals(listOf("Eggs", "Bread"), rows.map { it.title })
        assertEquals("u11", rows.first().uid)
    }

    @Test
    fun completingTheTaskLeavesItsSubtasksAsTheyAre() = runTest(dispatcher) {
        viewModel.toggleTaskCompletion(1, true)
        advanceUntilIdle()

        assertTrue(repository.tasks.value.single { it.id == 1L }.isCompleted)
        assertEquals(listOf(true, false), repository.subtasks.value.map { it.isCompleted })
    }

    @Test
    fun deleteRemovesTheSubtasksAndUndoBringsThemBack() = runTest(dispatcher) {
        advanceUntilIdle()
        val task = repository.tasks.value.single { it.id == 1L }
        val before = repository.subtasks.value

        viewModel.deleteTask(task)
        advanceUntilIdle()
        assertTrue(repository.subtasks.value.isEmpty())

        viewModel.restoreTask(task)
        advanceUntilIdle()
        assertEquals(before, repository.subtasks.value)
        assertEquals(before, viewModel.uiState.value.subtasks[1L])
    }

    @Test
    fun aFailedChecklistSaveIsReportedAndKeepsTheSheetOpen() = runTest(dispatcher) {
        val failures = mutableListOf<TaskOperationFailure>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.operationFailures.collect(failures::add)
        }
        advanceUntilIdle()
        viewModel.showEditTaskDialog(repository.tasks.value.first())
        advanceUntilIdle()
        repository.failSubtasks = true

        viewModel.editTask(1, "Shop", null, priority = 0, subtasks = emptyList())
        advanceUntilIdle()

        assertEquals(listOf(TaskOperationFailure.UPDATE), failures)
        assertTrue(viewModel.showAddTaskDialog.value)
        assertEquals(2, repository.subtasks.value.size)
    }

    @Test
    fun aBackgroundDraftSavesTheChecklistToo() = runTest(dispatcher) {
        viewModel.showAddTaskDialog()

        viewModel.saveDraftForBackground(TaskDraft("Trip", "", 0, listOf(SubtaskDraft(key = -1, title = "Tickets"))))
        advanceUntilIdle()

        val trip = repository.tasks.value.single { it.title == "Trip" }
        assertEquals(listOf("Tickets"), repository.subtasks.value.filter { it.taskId == trip.id }.map { it.title })
    }

    private fun TestScope.collectOffers(): List<Long> {
        val offers = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.completionOffers.collect(offers::add)
        }
        return offers
    }
}
