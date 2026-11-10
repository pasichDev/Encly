package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.testutil.TestNotesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class TrashExpiryTest {
    private val repository = TestNotesRepository()
    private val purge = PurgeExpiredTrashUseCase(repository)

    private fun trashed(id: Long, trashedAt: Long?) = Note(id = id, isTrash = true, trashedAt = trashedAt)

    @Test
    fun trashingRemembersWhenAndRestoringForgetsIt() = runTest {
        val useCase = UpdateNoteTrashStatusUseCase(repository)
        val note = Note(id = 4, title = "Plan")

        useCase(note, isTrash = true, now = NOW)
        useCase(note.copy(isTrash = true, trashedAt = NOW), isTrash = false, now = NOW + 1)

        assertEquals(listOf(NOW, null), repository.updatedNotes.map { it.trashedAt })
    }

    @Test
    fun aNoteIsDeletedOnlyOnceItIsOlderThan28Days() = runTest {
        repository.trashNotes.value = listOf(
            trashed(1, NOW - TRASH_RETENTION_MILLIS - 1),
            trashed(2, NOW - TRASH_RETENTION_MILLIS),
            trashed(3, NOW - TRASH_RETENTION_MILLIS + 1),
            trashed(4, NOW),
        )

        val result = purge(NOW)

        assertEquals(1, result.getOrNull())
        assertEquals(listOf(1L), repository.deletedIds)
    }

    @Test
    fun theRetentionIs28Days() {
        assertEquals(28, TRASH_RETENTION_DAYS)
        assertEquals(28L * 24 * 60 * 60 * 1000, TRASH_RETENTION_MILLIS)
    }

    @Test
    fun aTrashedNoteWithoutATimeStartsItsDaysNowAndIsKept() = runTest {
        repository.trashNotes.value = listOf(trashed(1, null))

        assertEquals(0, purge(NOW).getOrNull())

        assertTrue(repository.deletedIds.isEmpty())
        assertEquals(NOW, repository.trashNotes.value.single().trashedAt)
        // Twenty-eight days and a moment later it is gone.
        assertEquals(1, purge(NOW + TRASH_RETENTION_MILLIS + 1).getOrNull())
        assertEquals(listOf(1L), repository.deletedIds)
    }

    @Test
    fun anEmptyTrashDeletesNothing() = runTest {
        assertEquals(0, purge(NOW).getOrNull())
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun aNoteRestoredMeanwhileIsNotDeleted() = runTest {
        // The old note was read as expired, then restored: the delete must see it is no longer trashed.
        repository.trashNotes.value = listOf(
            trashed(1, NOW - TRASH_RETENTION_MILLIS - 1),
            Note(id = 2, isTrash = false, trashedAt = NOW - TRASH_RETENTION_MILLIS - 1),
        )

        assertEquals(1, purge(NOW).getOrNull())

        assertEquals(listOf(1L), repository.deletedIds)
        assertEquals(listOf(2L), repository.trashNotes.value.map { it.id })
    }

    @Test
    fun aTrashTimeInTheFutureIsNeverExpired() = runTest {
        repository.trashNotes.value = listOf(trashed(1, NOW + TRASH_RETENTION_MILLIS * 2))

        assertEquals(0, purge(NOW).getOrNull())

        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun theCutoffIsTheRetentionBeforeNow() = runTest {
        purge(NOW)

        assertEquals(listOf(NOW - TRASH_RETENTION_MILLIS), repository.purgeCutoffs)
    }

    @Test
    fun aFailedStampStopsBeforeAnythingIsDeleted() = runTest {
        val notes = mock(NotesRepository::class.java)
        `when`(notes.stampUntimedTrash(NOW)).thenReturn(Result.failure(IllegalStateException("disk")))

        assertTrue(PurgeExpiredTrashUseCase(notes)(NOW).isFailure)
        verify(notes, never()).deleteTrashedBefore(anyLong())
    }

    @Test
    fun aFailedDeleteIsAFailure() = runTest {
        val notes = mock(NotesRepository::class.java)
        `when`(notes.stampUntimedTrash(NOW)).thenReturn(Result.success(Unit))
        `when`(notes.deleteTrashedBefore(NOW - TRASH_RETENTION_MILLIS))
            .thenReturn(Result.failure(IllegalStateException("vault locked")))

        assertTrue(PurgeExpiredTrashUseCase(notes)(NOW).isFailure)
    }

    private companion object {
        const val NOW = 2_000_000_000_000L
    }
}
