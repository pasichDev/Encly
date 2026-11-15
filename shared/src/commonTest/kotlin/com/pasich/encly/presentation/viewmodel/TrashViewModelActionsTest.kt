package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.usecase.note.CleanTrashNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Trash: selection, restore, delete forever, empty trash. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelActionsTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestNotesRepository().apply {
        trashNotes.value = listOf(
            Note(id = 1, title = "a", isTrash = true),
            Note(id = 2, title = "b", isTrash = true),
            Note(id = 3, title = "c", isTrash = true),
        )
    }
    private lateinit var viewModel: TrashViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = TrashViewModel(
            repository,
            UpdateNoteTrashStatusUseCase(repository),
            CleanTrashNotesUseCase(repository),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theTrashLoadsFromLoading() = runTest(dispatcher) {
        val fresh =
            TrashViewModel(repository, UpdateNoteTrashStatusUseCase(repository), CleanTrashNotesUseCase(repository))
        assertEquals(LoadState.Loading, fresh.state.value.notesLoad)

        advanceUntilIdle()

        assertEquals(listOf(1L, 2L, 3L), fresh.state.value.notes.map { it.id })

        assertEquals(listOf(1L, 2L, 3L), viewModel.state.value.notes.map { it.id })
    }

    @Test
    fun anEmptyTrashIsReadyAndEmpty() = runTest(dispatcher) {
        repository.trashNotes.value = emptyList()
        advanceUntilIdle()

        assertEquals(LoadState.Ready(emptyList<Note>()), viewModel.state.value.notesLoad)
    }

    @Test
    fun togglingTwiceUnselectsAndOutOfRangeIsIgnored() = runTest(dispatcher) {
        advanceUntilIdle()

        viewModel.onEvent(TrashListEvent.ToggleCheckItem(2L))
        assertEquals(1, viewModel.state.value.checkedCount)
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(2L))
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(7L))
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(-1L))

        assertEquals(0, viewModel.state.value.checkedCount)
        assertFalse(viewModel.state.value.canCheck)
    }

    @Test
    fun selectingBeforeTheTrashLoadedDoesNothing() {
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(1L))

        assertEquals(LoadState.Loading, viewModel.state.value.notesLoad)
        assertEquals(0, viewModel.state.value.checkedCount)
    }

    @Test
    fun restoreTakesOnlyTheSelectedNotesOutOfTheTrash() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(1L))
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(3L))

        viewModel.onEvent(TrashListEvent.RestoreNotes())
        advanceUntilIdle()

        assertEquals(listOf(1L, 3L), repository.updatedNotes.map { it.id })
        assertTrue(repository.updatedNotes.none { it.isTrash })
        assertFalse(viewModel.state.value.canCheck)
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun deleteForeverRemovesOnlyTheSelectedNotes() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(2L))

        viewModel.onEvent(TrashListEvent.CleanNotes())
        advanceUntilIdle()

        assertEquals(listOf(2L), repository.deletedIds)
        assertEquals(0, viewModel.state.value.checkedCount)
    }

    @Test
    fun deleteForeverWithNothingSelectedDeletesNothing() = runTest(dispatcher) {
        advanceUntilIdle()

        viewModel.onEvent(TrashListEvent.CleanNotes())
        advanceUntilIdle()

        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun emptyTrashDeletesEveryTrashedNote() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(1L))

        viewModel.onEvent(TrashListEvent.CleanAll())
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L, 3L), repository.deletedIds)
        assertFalse(viewModel.state.value.canCheck)
    }

    @Test
    fun emptyingAnEmptyTrashTouchesNothing() = runTest(dispatcher) {
        repository.trashNotes.value = emptyList()
        advanceUntilIdle()

        viewModel.onEvent(TrashListEvent.CleanAll())
        advanceUntilIdle()

        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun aFailedRestoreKeepsTheNotesSelectedAndReportsIt() = runTest(dispatcher) {
        val failures = mutableListOf<TrashOperationFailure>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.operationFailures.toList(failures) }
        advanceUntilIdle()
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(1L))
        repository.columnWriteResult = false
        repository.updateResult = false

        viewModel.onEvent(TrashListEvent.RestoreNotes())
        advanceUntilIdle()

        assertEquals(listOf(TrashOperationFailure.RESTORE), failures)
        assertEquals(1, viewModel.state.value.checkedCount)
    }

    @Test
    fun aFailedDeleteForeverKeepsTheSelectionAndReportsIt() = runTest(dispatcher) {
        val failures = mutableListOf<TrashOperationFailure>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.operationFailures.toList(failures) }
        advanceUntilIdle()
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(2L))
        repository.trashDeleteFails = true

        viewModel.onEvent(TrashListEvent.CleanNotes())
        advanceUntilIdle()

        assertEquals(listOf(TrashOperationFailure.DELETE), failures)
        assertEquals(1, viewModel.state.value.checkedCount)
        assertEquals(3, viewModel.state.value.notes.size)
    }

    @Test
    fun aFailedEmptyTrashReportsItAndKeepsTheNotes() = runTest(dispatcher) {
        val failures = mutableListOf<TrashOperationFailure>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.operationFailures.toList(failures) }
        advanceUntilIdle()
        repository.trashDeleteFails = true

        viewModel.onEvent(TrashListEvent.CleanAll())
        advanceUntilIdle()

        assertEquals(listOf(TrashOperationFailure.DELETE), failures)
        assertEquals(3, viewModel.state.value.notes.size)
    }

    @Test
    fun theSelectionSurvivesTheTableBeingReEmittedAndDropsNotesThatAreGone() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(1L))
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(2L))

        repository.trashNotes.value = repository.trashNotes.value.filterNot { it.id == 1L }.map { it.copy(title = "x") }
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.checkedCount)
        assertEquals(listOf(2L), state.notes.filter { it.isChecked }.map { it.id })

        viewModel.onEvent(TrashListEvent.CleanNotes())
        advanceUntilIdle()

        assertEquals(listOf(2L), repository.deletedIds)
    }
}
