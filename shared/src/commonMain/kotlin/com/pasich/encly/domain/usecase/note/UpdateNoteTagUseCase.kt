package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import dev.zacsweers.metro.Inject

/** Moves the note to the tag [tagId] (0: no tag). Writes the tag only. */
@Inject
class UpdateNoteTagUseCase(private val repository: NotesRepository) {
    suspend operator fun invoke(targetNote: Note, tagId: Long): Result<Unit> =
        repository.setTag(targetNote.id, tagId.takeIf { it > 0L })
}
