package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import javax.inject.Inject

/** Moves the note to the archive, or back to the list. Archiving unpins it. */
class UpdateNoteArchiveStatusUseCase @Inject constructor(private val repository: NotesRepository) {
    suspend operator fun invoke(targetNote: Note, isArchived: Boolean): Result<Unit> =
        repository.setArchived(targetNote.id, isArchived)
}
