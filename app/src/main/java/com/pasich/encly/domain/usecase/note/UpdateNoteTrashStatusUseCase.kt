package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import javax.inject.Inject

/** Moves the note to the trash, or back out of it. */
class UpdateNoteTrashStatusUseCase @Inject constructor(private val repository: NotesRepository) {
    suspend operator fun invoke(targetNote: Note, isTrash: Boolean): Result<Unit> =
        repository.updateNote(targetNote.copy(isTrash = isTrash))
}
