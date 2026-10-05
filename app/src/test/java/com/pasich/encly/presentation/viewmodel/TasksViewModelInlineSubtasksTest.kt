package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.usecase.task.UpdateTaskStatusUseCase
import com.pasich.encly.presentation.dialogs.tasks.SUBTASK_TITLE_MAX_LENGTH
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

/**
 * Sub-tasks edited on the Tasks list itself: open/closed trees, the inline "Add sub-task" field,
 * renaming and deleting a sub-task in place (with Undo), and saving on backgrounding.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelInlineSubtasksTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: TestTasksRepository
    private lateinit var savedState: SavedStateHandle
    private lateinit var viewModel: TasksViewModel

    private val shop get() = repository.subtasks.value.filter { it.taskId == 1L }.sortedBy { it.position }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = TestTasksRepository(
            initial = listOf(Task(id = 1, title = "Shop"), Task(id = 2, title = "Call")),
            initialSubtasks = listOf(
                Subtask(id = 10, taskId = 1, title = "Milk", isCompleted = true, position = 0, uid = "u10"),
                Subtask(id = 11, taskId = 1, title = "Eggs", position = 1, uid = "u11"),
                Subtask(id = 12, taskId = 1, title = "Bread", position = 2, uid = "u12"),
            ),
        )
        savedState = SavedStateHandle()
        viewModel = newViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() =
        TasksViewModel(repository, UpdateTaskStatusUseCase(repository), savedStateHandle = savedState)

    @Test
    fun treesAreClosedAtFirstAndATapTogglesOne() = runTest(dispatcher) {
        advanceUntilIdle()
        assertEquals(emptySet<Long>(), viewModel.expandedTaskIds.value)

        viewModel.toggleSubtasks(1)
        viewModel.toggleSubtasks(2)
        assertEquals(setOf(1L, 2L), viewModel.expandedTaskIds.value)

        viewModel.toggleSubtasks(1)
        assertEquals(setOf(2L), viewModel.expandedTaskIds.value)
    }

    @Test
    fun openTreesSurviveARecreatedViewModelAndListUpdates() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.toggleSubtasks(1)

        val recreated = newViewModel()
        advanceUntilIdle()
        assertEquals(setOf(1L), recreated.expandedTaskIds.value)

        repository.tasks.value += Task(id = 3, title = "New")
        repository.subtasks.value += Subtask(id = 20, taskId = 3, title = "x", uid = "u20")
        advanceUntilIdle()
        assertEquals(setOf(1L), recreated.expandedTaskIds.value)
    }

    @Test
    fun aTaskThatIsGoneIsDroppedFromTheOpenTrees() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.toggleSubtasks(1)
        viewModel.toggleSubtasks(2)

        viewModel.deleteTask(repository.tasks.value.single { it.id == 2L })
        advanceUntilIdle()

        assertEquals(setOf(1L), viewModel.expandedTaskIds.value)
        assertEquals(longArrayOf(1L).toList(), savedState.get<LongArray>("expandedTaskIds")?.toList())
        // Ids restored from the saved state of tasks that no longer exist are dropped too.
        savedState["expandedTaskIds"] = longArrayOf(1L, 99L)
        val recreated = newViewModel()
        advanceUntilIdle()
        assertEquals(setOf(1L), recreated.expandedTaskIds.value)
    }

    @Test
    fun backFoldsTheTreeOpenedLastAmongTheVisibleOnes() = runTest(dispatcher) {
        repository.tasks.value += Task(id = 3, title = "Read")
        advanceUntilIdle()
        viewModel.toggleSubtasks(2)
        viewModel.toggleSubtasks(1)
        viewModel.toggleSubtasks(3)

        assertTrue(viewModel.collapseLastExpanded(visible = setOf(1L, 2L)))
        assertEquals(setOf(2L, 3L), viewModel.expandedTaskIds.value)
        assertTrue(viewModel.collapseLastExpanded(visible = setOf(1L, 2L)))
        assertEquals(setOf(3L), viewModel.expandedTaskIds.value)
        assertFalse(viewModel.collapseLastExpanded(visible = setOf(1L, 2L)))
        // The order survives the saved state.
        viewModel.toggleSubtasks(1)
        assertEquals(listOf(3L, 1L), newViewModel().expandedTaskIds.value.toList())
    }

    @Test
    fun movingASubtaskStoresTheNewOrderKeepingItsIdentity() = runTest(dispatcher) {
        advanceUntilIdle()

        viewModel.moveSubtask(1, from = 2, to = 0)
        advanceUntilIdle()

        assertEquals(listOf("Bread", "Milk", "Eggs"), shop.map { it.title })
        assertEquals(listOf("u12", "u10", "u11"), shop.map { it.uid })
        viewModel.moveSubtask(1, from = 0, to = 5)
        advanceUntilIdle()
        assertEquals(listOf("Bread", "Milk", "Eggs"), shop.map { it.title })
    }

    @Test
    fun doneAddsTheSubtaskLastAndKeepsTheFieldOpenForTheNext() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.startAddingSubtask(1)
        assertEquals(InlineSubtaskTarget.Add(1), viewModel.inlineEdit?.target)
        assertTrue(1L in viewModel.expandedTaskIds.value)

        viewModel.onInlineTextChange("  Butter ")
        viewModel.submitInlineEdit()
        assertEquals("", viewModel.inlineEdit?.text)
        viewModel.onInlineTextChange("Jam")
        viewModel.submitInlineEdit()
        advanceUntilIdle()

        assertEquals(listOf("Milk", "Eggs", "Bread", "Butter", "Jam"), shop.map { it.title })
        assertEquals(listOf(0, 1, 2, 3, 4), shop.map { it.position })
        assertEquals(InlineSubtaskEdit(InlineSubtaskTarget.Add(1), ""), viewModel.inlineEdit)
        assertEquals("Jam", viewModel.uiState.value.subtasks.getValue(1L).last().title)
    }

    @Test
    fun doneOnABlankFieldClosesItWithoutAdding() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.startAddingSubtask(2)
        viewModel.onInlineTextChange("   ")

        viewModel.submitInlineEdit()
        advanceUntilIdle()

        assertNull(viewModel.inlineEdit)
        assertTrue(repository.subtasks.value.none { it.taskId == 2L })
    }

    @Test
    fun losingFocusSavesAFilledFieldAndDropsABlankOne() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.startAddingSubtask(2)
        viewModel.onInlineTextChange("Mom")
        viewModel.closeInlineEdit(InlineSubtaskTarget.Add(2))
        viewModel.startAddingSubtask(2)
        viewModel.closeInlineEdit(InlineSubtaskTarget.Add(2))
        advanceUntilIdle()

        assertNull(viewModel.inlineEdit)
        assertEquals(listOf("Mom"), repository.subtasks.value.filter { it.taskId == 2L }.map { it.title })
    }

    @Test
    fun theTitleIsCutToTheSheetsLimit() = runTest(dispatcher) {
        viewModel.startAddingSubtask(2)
        viewModel.onInlineTextChange("x".repeat(SUBTASK_TITLE_MAX_LENGTH + 20))

        assertEquals(SUBTASK_TITLE_MAX_LENGTH, viewModel.inlineEdit?.text?.length)
    }

    @Test
    fun onlyOneFieldIsOpenAndOpeningAnotherSavesTheFirst() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.startAddingSubtask(1)
        viewModel.onInlineTextChange("Butter")

        viewModel.startAddingSubtask(2)
        // The first field's late focus loss must not close the second one.
        viewModel.closeInlineEdit(InlineSubtaskTarget.Add(1))
        advanceUntilIdle()

        assertEquals(InlineSubtaskTarget.Add(2), viewModel.inlineEdit?.target)
        assertEquals("Butter", shop.last().title)

        viewModel.onInlineTextChange("Mom")
        viewModel.startRenamingSubtask(shop.first())
        advanceUntilIdle()
        assertEquals(InlineSubtaskTarget.Rename(1, 10), viewModel.inlineEdit?.target)
        assertEquals("Milk", viewModel.inlineEdit?.text)
        assertEquals(listOf("Mom"), repository.subtasks.value.filter { it.taskId == 2L }.map { it.title })
    }

    @Test
    fun otherTapsOnTheListSaveTheFieldFirst() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.startAddingSubtask(1)
        viewModel.onInlineTextChange("Butter")

        viewModel.toggleSubtasks(2)
        advanceUntilIdle()

        assertNull(viewModel.inlineEdit)
        assertEquals("Butter", shop.last().title)
    }

    @Test
    fun renamingSavesTheNewTitleInPlace() = runTest(dispatcher) {
        advanceUntilIdle()
        val eggs = shop[1]
        viewModel.startRenamingSubtask(eggs)
        viewModel.onInlineTextChange(" Brown eggs ")

        viewModel.submitInlineEdit()
        advanceUntilIdle()

        assertNull(viewModel.inlineEdit)
        assertEquals(eggs.copy(title = "Brown eggs"), shop[1])
    }

    @Test
    fun aRenameLeftBlankDeletesTheSubtaskAndUndoPutsItBack() = runTest(dispatcher) {
        val deletions = collect<Subtask> { viewModel.subtaskDeletions.collect(it::add) }
        advanceUntilIdle()
        val eggs = shop[1]
        viewModel.startRenamingSubtask(eggs)
        viewModel.onInlineTextChange(" ")

        viewModel.closeInlineEdit(InlineSubtaskTarget.Rename(1, eggs.id))
        advanceUntilIdle()

        assertEquals(listOf("Milk", "Bread"), shop.map { it.title })
        assertEquals(listOf(eggs), deletions)

        viewModel.restoreSubtask(eggs)
        advanceUntilIdle()
        assertEquals(listOf("Milk", "Eggs", "Bread"), shop.map { it.title })
        assertEquals("u11", shop[1].uid)
    }

    @Test
    fun theCrossDeletesTheSubtaskBeingRenamedWithUndo() = runTest(dispatcher) {
        val deletions = collect<Subtask> { viewModel.subtaskDeletions.collect(it::add) }
        advanceUntilIdle()
        val milk = shop[0]
        viewModel.startRenamingSubtask(milk)
        viewModel.onInlineTextChange("Changed")

        viewModel.deleteInlineSubtask()
        advanceUntilIdle()

        assertNull(viewModel.inlineEdit)
        assertEquals(listOf(milk), deletions)
        viewModel.restoreSubtask(deletions.single())
        advanceUntilIdle()
        assertEquals(listOf("Milk", "Eggs", "Bread"), shop.map { it.title })
        assertTrue(shop[0].isCompleted)
    }

    @Test
    fun deletingTheLastOpenSubtaskOffersToCompleteTheTask() = runTest(dispatcher) {
        val offers = collect<Long> { viewModel.completionOffers.collect(it::add) }
        advanceUntilIdle()
        viewModel.toggleSubtask(shop[1], true)
        advanceUntilIdle()
        assertTrue(offers.isEmpty())

        viewModel.startRenamingSubtask(shop.single { it.title == "Bread" })
        viewModel.deleteInlineSubtask()
        advanceUntilIdle()

        assertEquals(listOf(1L), offers)
        assertTrue(shop.all { it.isCompleted })
    }

    @Test
    fun deletingAnOpenSubtaskWhileOthersStayOpenOffersNothing() = runTest(dispatcher) {
        val offers = collect<Long> { viewModel.completionOffers.collect(it::add) }
        advanceUntilIdle()

        viewModel.startRenamingSubtask(shop.single { it.title == "Bread" })
        viewModel.deleteInlineSubtask()
        advanceUntilIdle()

        assertTrue(offers.isEmpty())
    }

    @Test
    fun backgroundingSavesTheFieldAndKeepsItOpen() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.startAddingSubtask(2)
        viewModel.onInlineTextChange("Mom")

        viewModel.flushInlineEditForBackground()
        viewModel.flushInlineEditForBackground()
        advanceUntilIdle()

        assertEquals(listOf("Mom"), repository.subtasks.value.filter { it.taskId == 2L }.map { it.title })
        assertEquals(InlineSubtaskEdit(InlineSubtaskTarget.Add(2), ""), viewModel.inlineEdit)

        viewModel.startRenamingSubtask(shop[2])
        viewModel.onInlineTextChange("Rye bread")
        viewModel.flushInlineEditForBackground()
        advanceUntilIdle()
        assertEquals("Rye bread", shop[2].title)
        assertEquals("Rye bread", viewModel.inlineEdit?.text)

        // A blank field saves nothing, and a rename is not turned into a delete.
        viewModel.onInlineTextChange("")
        viewModel.flushInlineEditForBackground()
        advanceUntilIdle()
        assertEquals(3, shop.size)
    }

    @Test
    fun theSheetShowsWhatWasJustAddedInline() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.startAddingSubtask(1)
        viewModel.onInlineTextChange("Butter")

        viewModel.showEditTaskDialog(repository.tasks.value.single { it.id == 1L })
        advanceUntilIdle()

        assertNull(viewModel.inlineEdit)
        assertEquals(listOf("Milk", "Eggs", "Bread", "Butter"), viewModel.editingSubtasks.value?.map { it.title })
    }

    @Test
    fun addingToACompletedTaskLeavesItCompletedAndOffersNothing() = runTest(dispatcher) {
        repository.tasks.value = repository.tasks.value.map { if (it.id == 1L) it.copy(isCompleted = true) else it }
        val offers = collect<Long> { viewModel.completionOffers.collect(it::add) }
        advanceUntilIdle()

        viewModel.startAddingSubtask(1)
        viewModel.onInlineTextChange("Butter")
        viewModel.submitInlineEdit()
        advanceUntilIdle()

        assertTrue(repository.tasks.value.single { it.id == 1L }.isCompleted)
        assertEquals("Butter", shop.last().title)
        assertTrue(offers.isEmpty())
    }

    @Test
    fun theFieldOfATaskThatIsGoneIsClosed() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.startRenamingSubtask(shop[0])

        repository.subtasks.value = repository.subtasks.value.filterNot { it.id == 10L }
        advanceUntilIdle()
        assertNull(viewModel.inlineEdit)

        viewModel.startAddingSubtask(2)
        repository.tasks.value = repository.tasks.value.filterNot { it.id == 2L }
        advanceUntilIdle()
        assertNull(viewModel.inlineEdit)
    }

    @Test
    fun aFailedInlineWriteIsReported() = runTest(dispatcher) {
        val failures = collect<TaskOperationFailure> { viewModel.operationFailures.collect(it::add) }
        advanceUntilIdle()
        repository.failSubtasks = true

        viewModel.startAddingSubtask(2)
        viewModel.onInlineTextChange("Mom")
        viewModel.submitInlineEdit()
        viewModel.startRenamingSubtask(shop[0])
        viewModel.onInlineTextChange("Oat milk")
        viewModel.submitInlineEdit()
        advanceUntilIdle()

        assertEquals(listOf(TaskOperationFailure.UPDATE, TaskOperationFailure.UPDATE), failures)
        assertEquals("Milk", shop[0].title)
    }

    private fun <T> TestScope.collect(block: suspend (MutableList<T>) -> Unit): List<T> {
        val items = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { block(items) }
        return items
    }
}
