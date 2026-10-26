package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.common.LoadState
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.usecase.note.ObserveNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteArchiveStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteColorUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteDescriptionUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TestNotesRepository()
    private lateinit var viewModel: ArchiveViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = ArchiveViewModel(
            observeNotesUseCase = ObserveNotesUseCase(repository, dispatcher),
            updateNoteTrashStatusUseCase = UpdateNoteTrashStatusUseCase(repository),
            updateNoteArchiveStatusUseCase = UpdateNoteArchiveStatusUseCase(repository),
            updateNoteTagUseCase = UpdateNoteTagUseCase(repository),
            updateNoteDescriptionUseCase = UpdateNoteDescriptionUseCase(repository),
            updateNoteColorUseCase = UpdateNoteColorUseCase(repository),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun archived(id: Long, date: Long) =
        NoteWithTag(Note(id = id, title = "n$id", date = date, isArchived = true), null)

    @Test
    fun showsTheArchivedNotesNewestFirst() = runTest(dispatcher) {
        repository.archivedNotesWithTags.value = listOf(archived(1, 10), archived(2, 20))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.notesLoad is LoadState.Ready)
        assertEquals(listOf(2L, 1L), viewModel.state.value.notes.map { it.note.id })
    }

    @Test
    fun unarchivingWritesTheNoteBackToTheList() = runTest(dispatcher) {
        val note = archived(1, 10).note

        viewModel.onEvent(NoteListEvent.ArchiveNote(note, archived = false))
        advanceUntilIdle()

        assertEquals(listOf("archived:1=false"), repository.columnWrites)
    }

    @Test
    fun deletingAnArchivedNoteMovesItToTheTrashKeepingItArchived() = runTest(dispatcher) {
        val note = archived(1, 10).note

        viewModel.onEvent(NoteListEvent.NoteToTrash(note))
        advanceUntilIdle()

        assertEquals(note.copy(isTrash = true), repository.updatedNotes.single())
    }

    @Test
    fun colourChangesAreStoredAndListOnlyEventsAreIgnored() = runTest(dispatcher) {
        val note = archived(1, 10).note

        viewModel.onEvent(NoteListEvent.ChangeColor(note, NoteColor.BLUE))
        viewModel.onEvent(NoteListEvent.PinNote(note, pinned = true))
        advanceUntilIdle()

        assertEquals(listOf("color:1=blue"), repository.columnWrites)
        assertEquals(0, repository.updateCalls)
    }
}
