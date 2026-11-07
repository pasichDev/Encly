package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.common.currentTimeMillis
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first

/** How long a note stays in the trash before it is deleted for good. */
const val TRASH_RETENTION_DAYS = 28

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/** How long, in milliseconds, a note stays in the trash. */
const val TRASH_RETENTION_MILLIS = TRASH_RETENTION_DAYS * MILLIS_PER_DAY

/**
 * Deletes the notes that have been in the trash for more than [TRASH_RETENTION_DAYS] days, the same way "Delete
 * forever" does ([CleanTrashNotesUseCase]). Run when the vault is open: on unlock, and while the app is used.
 */
@Inject
class PurgeExpiredTrashUseCase(
    private val repository: NotesRepository,
    private val cleanTrash: CleanTrashNotesUseCase,
) {
    /** The number of notes deleted. [now] is the time to measure from. */
    suspend operator fun invoke(now: Long = currentTimeMillis()): Result<Int> {
        // A trashed note without a time (imported, restored from a backup) starts its 28 days now.
        val trashed = repository.stampUntimedTrash(now).mapCatching { repository.getTrashNotes().first() }
            .getOrElse { return Result.failure(it) }
        val expired = trashed.filter { it.isExpired(now) }
        return cleanTrash.cleanSelectedNotes(expired).map { expired.size }
    }
}

private fun Note.isExpired(now: Long): Boolean {
    val trashedAt = trashedAt ?: return false
    return now - trashedAt > TRASH_RETENTION_MILLIS
}
