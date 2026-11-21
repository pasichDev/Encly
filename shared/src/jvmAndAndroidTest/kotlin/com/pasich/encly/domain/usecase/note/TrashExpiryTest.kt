package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.testutil.InMemoryKeyValueStore
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
    private val flags = InMemoryKeyValueStore()
    private val security = mock(SecurityManager::class.java)
    private val purge = PurgeExpiredTrashUseCase(repository, flags, security)

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
        // Twenty-eight days and a moment later, on the unlocks of the days in between, it is gone.
        var day = 1
        while (day < TRASH_RETENTION_DAYS) purge(NOW + DAY * day++).getOrThrow()
        assertTrue(repository.deletedIds.isEmpty())
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

        assertTrue(PurgeExpiredTrashUseCase(notes, flags, security)(NOW).isFailure)
        verify(notes, never()).deleteTrashedBefore(anyLong())
    }

    @Test
    fun aFailedDeleteIsAFailure() = runTest {
        val notes = mock(NotesRepository::class.java)
        `when`(notes.stampUntimedTrash(NOW)).thenReturn(Result.success(Unit))
        `when`(notes.deleteTrashedBefore(NOW - TRASH_RETENTION_MILLIS))
            .thenReturn(Result.failure(IllegalStateException("vault locked")))

        assertTrue(PurgeExpiredTrashUseCase(notes, flags, security)(NOW).isFailure)
    }

    @Test
    fun aClockJumpedForwardDeletesOnlyTwoDaysFurtherPerUnlock() = runTest {
        purge(NOW)
        repository.trashNotes.value = listOf(
            trashed(1, NOW - 10 * DAY),
            trashed(2, NOW + 3 * DAY),
            trashed(3, NOW + 20 * DAY),
        )

        // A year ahead: without the cap all three are older than 28 days.
        assertEquals(0, purge(NOW + 365 * DAY).getOrNull())
        assertEquals(listOf(NOW + 2 * DAY - TRASH_RETENTION_MILLIS), repository.purgeCutoffs.takeLast(1))
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun theCutoffAdvancesTwoDaysPerUnlockAfterAJump() = runTest {
        purge(NOW)
        purge(NOW + 365 * DAY)
        purge(NOW + 365 * DAY)

        assertEquals(
            listOf(NOW, NOW + 2 * DAY, NOW + 4 * DAY).map { it - TRASH_RETENTION_MILLIS },
            repository.purgeCutoffs,
        )
    }

    @Test
    fun aClockSetBackBeforeTheLastPurgeSkipsIt() = runTest {
        purge(NOW)
        repository.trashNotes.value = listOf(trashed(1, NOW - TRASH_RETENTION_MILLIS * 2))

        assertEquals(0, purge(NOW - 1).getOrNull())

        assertTrue(repository.deletedIds.isEmpty())
        assertEquals(1, repository.purgeCutoffs.size)
    }

    @Test
    fun aFailedDeleteDoesNotMoveTheLastPurge() = runTest {
        val notes = mock(NotesRepository::class.java)
        `when`(notes.stampUntimedTrash(NOW)).thenReturn(Result.success(Unit))
        `when`(notes.deleteTrashedBefore(NOW - TRASH_RETENTION_MILLIS))
            .thenReturn(Result.failure(IllegalStateException("vault locked")))

        PurgeExpiredTrashUseCase(notes, flags, security)(NOW)

        assertEquals(0L, flags.getLong("trash_last_purge_at", 0L))
    }

    @Test
    fun theDecoyVaultKeepsItsOwnPurgeTimeAndNeverMovesTheRealOne() = runTest {
        purge(NOW) // the real vault purged once
        `when`(security.isDecoySession()).thenReturn(true)

        // The decoy is unlocked again and again with the clock a year ahead: 14 times is far more than 28 days.
        repeat(20) { purge(NOW + 365 * DAY) }

        assertEquals(NOW, flags.getLong("trash_last_purge_at", 0L))
        assertEquals(NOW + 365 * DAY, flags.getLong("trash_last_purge_at_d", 0L))
        `when`(security.isDecoySession()).thenReturn(false)
        repository.purgeCutoffs.clear()
        purge(NOW + 365 * DAY)
        assertEquals(
            "the real vault still advances two days at most",
            listOf(NOW + 2 * DAY - TRASH_RETENTION_MILLIS),
            repository.purgeCutoffs,
        )
    }

    @Test
    fun theRealVaultWritesNoDecoyKey() = runTest {
        purge(NOW)

        assertEquals(0L, flags.getLong("trash_last_purge_at_d", 0L))
    }

    @Test
    fun theDecoyVaultWritesOnlyItsOwnKeyAndOnlyWhenItPurged() = runTest {
        `when`(security.isDecoySession()).thenReturn(true)

        purge(NOW)

        assertEquals(0L, flags.getLong("trash_last_purge_at", 0L))
        assertEquals(NOW, flags.getLong("trash_last_purge_at_d", 0L))
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val NOW = 2_000_000_000_000L
    }
}
