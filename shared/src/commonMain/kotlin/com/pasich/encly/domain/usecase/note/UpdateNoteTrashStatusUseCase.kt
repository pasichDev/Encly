package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.common.currentTimeMillis
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import dev.zacsweers.metro.Inject

/**
 * Moves the note to the trash, remembering when, or back out of it. Only those two columns are written, never the
 * rest of the [Note] a screen read earlier.
 */
@Inject
class UpdateNoteTrashStatusUseCase(private val repository: NotesRepository) {
    suspend operator fun invoke(targetNote: Note, isTrash: Boolean, now: Long = currentTimeMillis()): Result<Unit> =
        repository.setTrashed(targetNote.id, isTrash, now.takeIf { isTrash })
}
