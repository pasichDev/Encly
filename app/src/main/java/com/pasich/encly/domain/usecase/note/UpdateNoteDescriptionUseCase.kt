package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.AppLogger
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.repository.NotesRepository
import javax.inject.Inject


class UpdateNoteDescriptionUseCase @Inject constructor(
    private val repository: NotesRepository
) {
    suspend operator fun invoke(targetNote: Note, description: String): Boolean {
        return try {
            repository.updateNote(targetNote.copy(description = description))
        } catch (e: Exception) {
            AppLogger.e("UpdateNoteDescriptionUseCase", "Error: ${e.message}")
            false
        }


    }
}
