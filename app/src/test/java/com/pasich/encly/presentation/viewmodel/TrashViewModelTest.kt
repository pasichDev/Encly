package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.usecase.note.CleanTrashNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestNotesRepository().apply {
        trashNotes.value = listOf(Note(id = 1, title = "a", isTrash = true), Note(id = 2, title = "b", isTrash = true))
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TrashViewModel(
        repository,
        UpdateNoteTrashStatusUseCase(repository),
        CleanTrashNotesUseCase(repository),
    )

    @Test
    fun backClearsTheSelectionAndKeepsTheNotes() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(TrashListEvent.ToggleCheckItem(0))
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(1))
        assertTrue(viewModel.state.value.canCheck)
        assertEquals(2, viewModel.state.value.checkedCount)

        viewModel.onEvent(TrashListEvent.ClearSelection)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.canCheck)
        assertEquals(0, state.checkedCount)
        assertEquals(listOf(1L, 2L), state.notes.map { it.id })
        assertTrue(state.notes.none { it.isChecked })
        assertTrue(repository.deletedIds.isEmpty())
    }
}
