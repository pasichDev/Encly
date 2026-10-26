package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import javax.inject.Inject

/** Pins the note to the top of the list, or unpins it. An archived note cannot be pinned. */
class UpdateNotePinStatusUseCase @Inject constructor(private val repository: NotesRepository) {
    suspend operator fun invoke(targetNote: Note, isPinned: Boolean): Result<Unit> =
        repository.setPinned(targetNote.id, isPinned)
}
