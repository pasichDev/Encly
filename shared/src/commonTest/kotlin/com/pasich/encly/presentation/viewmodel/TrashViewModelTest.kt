package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestNotesRepository().apply {
        trashNotes.value = listOf(Note(id = 1, title = "a", isTrash = true), Note(id = 2, title = "b", isTrash = true))
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
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

        viewModel.onEvent(TrashListEvent.ToggleCheckItem(1L))
        viewModel.onEvent(TrashListEvent.ToggleCheckItem(2L))
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

    @Test
    fun aNoteWithAHiddenTagIsMarkedHidden() = runTest(dispatcher) {
        repository.trashTags.value = mapOf(
            1L to listOf(Tag(id = 5, nameTag = "Open"), Tag(id = 6, nameTag = "Private", isVisible = false)),
            2L to listOf(Tag(id = 5, nameTag = "Open")),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(setOf(1L), viewModel.state.value.hiddenIds)
        assertEquals(listOf(1L, 2L), viewModel.state.value.notes.map { it.id })
    }
}
