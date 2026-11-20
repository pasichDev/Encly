package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.common.currentTimeMillis
import com.pasich.encly.core.platform.KeyValueStore
import com.pasich.encly.domain.repository.NotesRepository
import dev.zacsweers.metro.Inject

/** How long a note stays in the trash before it is deleted for good. */
const val TRASH_RETENTION_DAYS = 28

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/** The most a single purge moves its clock forward from the last one, so a clock jump cannot empty the trash. */
private const val MAX_ADVANCE_MILLIS = 2 * MILLIS_PER_DAY

private const val LAST_PURGE_KEY = "trash_last_purge_at"
private const val TAG = "PurgeExpiredTrash"

/** How long, in milliseconds, a note stays in the trash. */
const val TRASH_RETENTION_MILLIS = TRASH_RETENTION_DAYS * MILLIS_PER_DAY

/**
 * Deletes the notes that have been in the trash for more than [TRASH_RETENTION_DAYS] days, with the same row delete
 * as "Delete forever" ([CleanTrashNotesUseCase]), but in one statement that checks the trash flag as it deletes: a note
 * restored meanwhile is kept. A trash time in the future (the clock was set back since) never counts as expired.
 * Run when the vault is open: on unlock, and while the app is used.
 *
 * The wall clock is not trusted: the time of the last purge is kept in [flags], and one purge measures from at most two
 * days after it, so a clock set far forward deletes the trash two days further per unlock, not all of it at once. A
 * clock that went back before the last purge skips the purge until it catches up.
 */
@Inject
class PurgeExpiredTrashUseCase(private val repository: NotesRepository, private val flags: KeyValueStore) {
    /** The number of notes deleted. [now] is the time to measure from. */
    suspend operator fun invoke(now: Long = currentTimeMillis()): Result<Int> {
        val last = flags.getLong(LAST_PURGE_KEY, NEVER)
        return if (last != NEVER && now < last) {
            AppLogger.w(TAG, "Clock is behind the last purge, skipped")
            Result.success(0)
        } else {
            // No purge before: measure from now. Else from now, but no further than two days past the last purge.
            purge(now, measuredFrom = if (last == NEVER) now else minOf(now, last + MAX_ADVANCE_MILLIS))
        }
    }

    private suspend fun purge(now: Long, measuredFrom: Long): Result<Int> =
        // A trashed note without a time (imported, restored from a backup) starts its 28 days now.
        repository.stampUntimedTrash(now).mapCatching {
            // Expired means older than the retention, so a trash time after `now` is never below the cutoff.
            repository.deleteTrashedBefore(measuredFrom - TRASH_RETENTION_MILLIS).getOrThrow()
        }.onSuccess { flags.edit { putLong(LAST_PURGE_KEY, measuredFrom) } }
}

private const val NEVER = 0L
