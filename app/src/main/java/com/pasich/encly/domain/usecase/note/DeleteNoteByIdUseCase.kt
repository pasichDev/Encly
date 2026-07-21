package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.AppLogger
import com.pasich.encly.data.repository.NotesRepository
import javax.inject.Inject


class DeleteNoteByIdUseCase @Inject constructor(
    private val repository: NotesRepository
) {
    suspend operator fun invoke(idNote: Long): Boolean {
        return try {
            repository.deleteNoteById(idNote)
        } catch (e: Exception) {
            AppLogger.e("DeleteNoteByIdUseCase", "Error: ${e.message}")
            false
        }
    }
}
