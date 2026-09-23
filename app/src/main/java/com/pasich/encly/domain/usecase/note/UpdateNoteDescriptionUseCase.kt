package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import javax.inject.Inject

/** Sets the short description shown on the note's card. */
class UpdateNoteDescriptionUseCase @Inject constructor(private val repository: NotesRepository) {
    suspend operator fun invoke(targetNote: Note, description: String): Result<Unit> =
        repository.updateNote(targetNote.copy(description = description))
}
