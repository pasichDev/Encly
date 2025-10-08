package com.pasich.encly.domain.usecase.note

import android.util.Log
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
            Log.e("UpdateNoteTagUseCase", "Error: ${e.message}")
            false
        }


    }
}
