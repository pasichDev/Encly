package com.pasich.encly.domain.usecase.note

import android.util.Log
import com.pasich.encly.data.repository.NotesRepository
import javax.inject.Inject


class DeleteNoteByIdUseCase @Inject constructor(
    private val repository: NotesRepository
) {
    suspend operator fun invoke(idNote: Long): Boolean {
        return try {
            repository.deleteNoteById(idNote)
        } catch (e: Exception) {
            Log.e("DeleteNoteByIdUseCase", "Error: ${e.message}")
            false
        }
    }
}
