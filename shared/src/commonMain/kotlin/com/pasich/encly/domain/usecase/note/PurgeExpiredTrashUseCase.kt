package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.common.currentTimeMillis
import com.pasich.encly.domain.repository.NotesRepository
import dev.zacsweers.metro.Inject

/** How long a note stays in the trash before it is deleted for good. */
const val TRASH_RETENTION_DAYS = 28

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/** How long, in milliseconds, a note stays in the trash. */
const val TRASH_RETENTION_MILLIS = TRASH_RETENTION_DAYS * MILLIS_PER_DAY

/**
 * Deletes the notes that have been in the trash for more than [TRASH_RETENTION_DAYS] days, with the same row delete
 * as "Delete forever" ([CleanTrashNotesUseCase]), but in one statement that checks the trash flag as it deletes: a note
 * restored meanwhile is kept. A trash time in the future (the clock was set back since) never counts as expired.
 * Run when the vault is open: on unlock, and while the app is used.
 */
@Inject
class PurgeExpiredTrashUseCase(private val repository: NotesRepository) {
    /** The number of notes deleted. [now] is the time to measure from. */
    suspend operator fun invoke(now: Long = currentTimeMillis()): Result<Int> {
        // A trashed note without a time (imported, restored from a backup) starts its 28 days now.
        repository.stampUntimedTrash(now).onFailure { return Result.failure(it) }
        // Expired means older than the retention, so a trash time after `now` is never below the cutoff.
        return repository.deleteTrashedBefore(now - TRASH_RETENTION_MILLIS)
    }
}
