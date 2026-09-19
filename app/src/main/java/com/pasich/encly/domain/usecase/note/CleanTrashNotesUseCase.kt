package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.AppLogger
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UseCase for bulk deletion of notes from the trash, including cleanup of photos.
 */
class CleanTrashNotesUseCase @Inject constructor(
    private val repository: NotesRepository
) {
    suspend operator fun invoke(): Boolean = withContext(Dispatchers.IO) {
        try {
            val trashNotes = repository.getTrashNotes().first()
            trashNotes.allDeleted()
        } catch (e: Exception) {
            AppLogger.e("CleanTrashNotesUseCase", "Error clearing trash", e)
            false
        }
    }

    suspend fun cleanSelectedNotes(selectedNotes: List<Note>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                selectedNotes.allDeleted()
            } catch (e: Exception) {
                AppLogger.e("CleanTrashNotesUseCase", "Error deleting selected notes", e)
                false
            }
        }

    private suspend fun List<Note>.allDeleted(): Boolean {
        for (note in this) {
            if (!repository.deleteNoteById(note.id)) return false
        }
        return true
    }
}
