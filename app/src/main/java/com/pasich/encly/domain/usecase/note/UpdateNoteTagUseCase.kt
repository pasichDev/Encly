package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import javax.inject.Inject

/** Moves the note to the tag [tagId]. */
class UpdateNoteTagUseCase @Inject constructor(private val repository: NotesRepository) {
    suspend operator fun invoke(targetNote: Note, tagId: Long): Result<Unit> =
        repository.updateNote(targetNote.copy(tagId = tagId))
}
