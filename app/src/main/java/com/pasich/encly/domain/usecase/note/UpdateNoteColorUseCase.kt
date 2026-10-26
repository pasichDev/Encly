package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.repository.NotesRepository
import javax.inject.Inject

/** Marks the note with [color] ([NoteColor.NONE] clears it). Writes the colour only. */
class UpdateNoteColorUseCase @Inject constructor(private val repository: NotesRepository) {
    suspend operator fun invoke(targetNote: Note, color: NoteColor): Result<Unit> =
        repository.setColor(targetNote.id, color.key)
}
