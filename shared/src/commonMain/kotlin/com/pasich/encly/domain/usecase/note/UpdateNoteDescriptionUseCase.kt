package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import dev.zacsweers.metro.Inject

/** Sets the short description shown on the note's card. Writes the description only. */
@Inject
class UpdateNoteDescriptionUseCase(private val repository: NotesRepository) {
    suspend operator fun invoke(targetNote: Note, description: String): Result<Unit> =
        repository.setDescription(targetNote.id, description)
}
