package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.AppLogger
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.repository.NotesRepository
import javax.inject.Inject


class UpdateNoteTagUseCase @Inject constructor(
    private val repository: NotesRepository
) {
    suspend operator fun invoke(targetNote: Note, tagId: Long): Boolean {
        return try {
            repository.updateNote(targetNote.copy(tagId = tagId))
        } catch (e: Exception) {
            AppLogger.e("UpdateNoteTagUseCase", "Error: ${e.message}")
            false
        }


    }
}
