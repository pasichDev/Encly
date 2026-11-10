package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import dev.zacsweers.metro.Inject

/**
 * Puts the tag [tagId] on the note or takes it off ([tagged]); 0 ("No tag") takes every tag off. Writes that tag
 * only: the note's other tags stay as they are.
 */
@Inject
class UpdateNoteTagUseCase(private val repository: NotesRepository) {
    suspend operator fun invoke(targetNote: Note, tagId: Long, tagged: Boolean = true): Result<Unit> = if (tagId > 0L) {
        repository.setTagged(targetNote.id, tagId, tagged)
    } else {
        repository.clearTags(targetNote.id)
    }
}
