package com.pasich.encly.domain.usecase.note

import android.util.Log
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.repository.NotesRepository
import javax.inject.Inject


class UpdateNoteTrashStatusUseCase @Inject constructor(
    private val repository: NotesRepository
) {
    suspend operator fun invoke(targetNote: Note, isTrash: Boolean): Boolean {
        return try {
            val tNote = targetNote.copy(isTrash = isTrash)
            repository.updateNote(tNote)
        } catch (e: Exception) {
            Log.e("NoteToTrashUserCase", "Error: ${e.message}")
            false
        }
    }
}
