package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.repository.TasksRepository
import com.pasich.encly.domain.usecase.task.UpdateTaskStatusUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** Unsaved task-editor input must survive the background re-lock (which pops every screen). */
@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelDraftTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemoryTasksRepository
    private lateinit var viewModel: TasksViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = InMemoryTasksRepository()
        viewModel = TasksViewModel(
            tasksRepository = repository,
            updateTaskStatusUseCase = UpdateTaskStatusUseCase(repository),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun newTaskDraftIsSavedOnceAndThenUpdatedInPlace() = runTest(dispatcher) {
        viewModel.showAddTaskDialog()

        viewModel.saveDraftForBackground(TaskDraft("Buy milk", "", 1))
        viewModel.saveDraftForBackground(TaskDraft("Buy milk and bread", "2 l", 2))
        advanceUntilIdle()

        assertEquals(1, repository.tasks.value.size)
        val saved = repository.tasks.value.single()
        assertEquals("Buy milk and bread", saved.title)
        assertEquals("2 l", saved.description)
        assertEquals(2, saved.priority)
        assertEquals(saved.id, viewModel.editingTask.value?.id)
        assertTrue(viewModel.showAddTaskDialog.value)
    }

    @Test
    fun editedTaskDraftIsWrittenToTheExistingRow() = runTest(dispatcher) {
        val id = repository.insertTask(Task.new(title = "Old", priority = 0)).getOrThrow()
        viewModel.showEditTaskDialog(repository.tasks.value.single())

        viewModel.saveDraftForBackground(TaskDraft("New", "details", 1))
        advanceUntilIdle()

        val saved = repository.tasks.value.single()
        assertEquals(id, saved.id)
        assertEquals("New", saved.title)
        assertEquals(1, saved.priority)
    }

    @Test
    fun filtersAreAllPrioritiesAndCompletedWithAllSelectedFirst() = runTest(dispatcher) {
        repository.insertTask(Task.new(title = "High", priority = 2))
        repository.insertTask(Task.new(title = "Low", priority = 0))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            listOf("all", "priority_high", "priority_low", "completed"),
            state.availableFilters.map { it.id },
        )
        assertEquals("all", state.selectedActiveFilter?.id)
        assertEquals(listOf("High", "Low"), state.filteredActiveTasks.map { it.title })
    }

    @Test
    fun aSingleTaskCanBeDeletedAndTheDeleteUndone() = runTest(dispatcher) {
        repository.insertTask(Task.new(title = "Keep", priority = 0))
        repository.insertTask(Task.new(title = "Remove", priority = 0))
        val deleted = mutableListOf<Task>()
        backgroundScope.launch { viewModel.deletedTasks.collect { deleted += it } }
        advanceUntilIdle()
        val target = repository.tasks.value.single { it.title == "Remove" }
        viewModel.showEditTaskDialog(target)

        viewModel.deleteTask(target)
        advanceUntilIdle()

        assertEquals(listOf("Keep"), repository.tasks.value.map { it.title })
        assertEquals(listOf("Remove"), deleted.map { it.title })
        assertFalse(viewModel.showAddTaskDialog.value)

        viewModel.restoreTask(deleted.single())
        advanceUntilIdle()
        assertEquals(setOf("Keep", "Remove"), repository.tasks.value.map { it.title }.toSet())
    }

    @Test
    fun completingTheLastTaskOfTheSelectedPriorityDropsThatFilter() = runTest(dispatcher) {
        repository.insertTask(Task.new(title = "High", priority = 2))
        repository.insertTask(Task.new(title = "Low", priority = 0))
        advanceUntilIdle()
        val high = viewModel.uiState.value.availableFilters.single { it.id == "priority_high" }
        viewModel.onFilterSelected(high)
        assertEquals(listOf("High"), viewModel.uiState.value.filteredActiveTasks.map { it.title })

        viewModel.toggleTaskCompletion(repository.tasks.value.single { it.title == "High" }.id, true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedPriorityFilter)
        assertEquals(listOf("Low"), viewModel.uiState.value.filteredActiveTasks.map { it.title })
    }

    @Test
    fun editingATaskKeepsItsCategory() = runTest(dispatcher) {
        repository.insertTask(Task.new(title = "Old", priority = 0, categoryId = CATEGORY_ID))
        advanceUntilIdle()

        viewModel.editTask(repository.tasks.value.single().id, "New", null, 1)
        advanceUntilIdle()

        assertEquals(CATEGORY_ID, repository.tasks.value.single().categoryId)
    }

    @Test
    fun blankOrClosedEditorSavesNothing() = runTest(dispatcher) {
        viewModel.saveDraftForBackground(TaskDraft("Not open", "", 0))
        viewModel.showAddTaskDialog()
        viewModel.saveDraftForBackground(TaskDraft("   ", "desc", 0))
        advanceUntilIdle()

        assertTrue(repository.tasks.value.isEmpty())
    }
}

private const val CATEGORY_ID = 4L

private class InMemoryTasksRepository : TasksRepository {
    val tasks = MutableStateFlow<List<Task>>(emptyList())
    private var nextId = 1L

    override fun getAllActiveTasks(): Flow<List<Task>> = tasks.map { l -> l.filterNot { it.isCompleted } }
    override fun getAllCompletedTasks(): Flow<List<Task>> = tasks.map { l -> l.filter { it.isCompleted } }
    override fun getAllTasks(): Flow<List<Task>> = tasks
    override fun getActiveTasksCount(): Flow<Int> = getAllActiveTasks().map { it.size }
    override fun getCompletedTasksCount(): Flow<Int> = getAllCompletedTasks().map { it.size }

    override suspend fun insertTask(task: Task): Result<Long> {
        val id = nextId++
        tasks.value = tasks.value + task.copy(id = id)
        return Result.success(id)
    }

    override suspend fun updateTask(task: Task): Result<Unit> = runCatching {
        check(tasks.value.any { it.id == task.id })
        tasks.value = tasks.value.map { if (it.id == task.id) task else it }
    }

    override suspend fun updateTaskStatus(id: Long, isCompleted: Boolean, completedDate: Long?): Result<Unit> {
        tasks.value = tasks.value.map { if (it.id == id) it.copy(isCompleted = isCompleted) else it }
        return Result.success(Unit)
    }

    override suspend fun deleteAllCompletedTasks(): Result<Unit> = Result.success(Unit)
    override suspend fun deleteTaskById(id: Long): Result<Unit> = runCatching {
        check(tasks.value.any { it.id == id })
        tasks.value = tasks.value.filterNot { it.id == id }
    }
}
