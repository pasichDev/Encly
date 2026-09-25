package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.IOException

class NoteUseCasesTest {
    private val repository = TestNotesRepository()
    private val note = Note(id = 4, title = "Plan", description = "old", tagId = 1)

    @Test
    fun trashAndRestoreChangeOnlyTheTrashFlag() = runTest {
        val useCase = UpdateNoteTrashStatusUseCase(repository)

        assertTrue(useCase(note, isTrash = true).isSuccess)
        assertTrue(useCase(note.copy(isTrash = true), isTrash = false).isSuccess)

        assertEquals(listOf(true, false), repository.updatedNotes.map { it.isTrash })
        assertTrue(repository.updatedNotes.all { it.title == "Plan" && it.tagId == 1L })
    }

    @Test
    fun theTagAndTheDescriptionAreChangedAlone() = runTest {
        UpdateNoteTagUseCase(repository)(note, tagId = 9)
        UpdateNoteDescriptionUseCase(repository)(note, "new")

        assertEquals(note.copy(tagId = 9), repository.updatedNotes[0])
        assertEquals(note.copy(description = "new"), repository.updatedNotes[1])
    }

    @Test
    fun aFailedWriteIsReturnedNotThrown() = runTest {
        repository.updateResult = false

        assertTrue(UpdateNoteTrashStatusUseCase(repository)(note, true).isFailure)
        assertTrue(UpdateNoteTagUseCase(repository)(note, 2).isFailure)
        assertTrue(UpdateNoteDescriptionUseCase(repository)(note, "x").isFailure)
    }

    @Test
    fun emptyingTheTrashDeletesEveryTrashedNote() = runTest {
        repository.trashNotes.value = listOf(Note(id = 1, isTrash = true), Note(id = 2, isTrash = true))

        assertTrue(CleanTrashNotesUseCase(repository)().isSuccess)

        assertEquals(listOf(1L, 2L), repository.deletedIds)
    }

    @Test
    fun deletingSelectedNotesDeletesOnlyThose() = runTest {
        val result = CleanTrashNotesUseCase(repository).cleanSelectedNotes(listOf(Note(id = 5), Note(id = 7)))

        assertTrue(result.isSuccess)
        assertEquals(listOf(5L, 7L), repository.deletedIds)
    }

    @Test
    fun emptyingStopsAtTheFirstFailedDelete() = runTest {
        val notes = mock(NotesRepository::class.java)
        `when`(notes.getTrashNotes()).thenReturn(flowOf(listOf(Note(id = 1), Note(id = 2), Note(id = 3))))
        `when`(notes.deleteNoteById(1)).thenReturn(Result.success(Unit))
        `when`(notes.deleteNoteById(2)).thenReturn(Result.failure(IOException("disk")))

        val result = CleanTrashNotesUseCase(notes)()

        assertTrue(result.exceptionOrNull() is IOException)
        verify(notes, never()).deleteNoteById(3)
    }

    @Test
    fun aTrashThatCannotBeReadIsAFailureAndDeletesNothing() = runTest {
        val notes = mock(NotesRepository::class.java)
        `when`(notes.getTrashNotes()).thenReturn(flow { throw IllegalStateException("vault locked") })

        val result = CleanTrashNotesUseCase(notes)()

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
