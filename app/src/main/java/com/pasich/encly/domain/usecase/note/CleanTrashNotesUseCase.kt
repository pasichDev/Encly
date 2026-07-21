package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.AppLogger
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UseCase for bulk deletion of notes from the trash, including cleanup of photos.
 */
class CleanTrashNotesUseCase @Inject constructor(
    private val repository: NotesRepository
) {

    /**
     * Deletes all notes from the trash, including cleanup of photos.
     */
    suspend operator fun invoke(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            // Get all notes from the trash
            repository.getTrashNotes().collect { trashNotes ->
                if (trashNotes.isNotEmpty()) {
                    AppLogger.d("CleanTrashNotesUseCase", "Deleting ${trashNotes.size} notes from trash")

                    // Then delete all notes
                    trashNotes.forEach { note ->
                        repository.deleteNoteById(note.id.toLong())
                    }

                    AppLogger.d(
                        "CleanTrashNotesUseCase",
                        "Deleted ${trashNotes.size} notes from trash"
                    )
                }
            }
            true
        } catch (e: Exception) {
            AppLogger.e("CleanTrashNotesUseCase", "Error clearing trash: ${e.message}")
            false
        }
    }

    /**
     * Deletes the selected notes from the trash, including cleanup of photos.
     */
    suspend fun cleanSelectedNotes(selectedNotes: List<Note>): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext try {
                if (selectedNotes.isNotEmpty()) {
                    AppLogger.d(
                        "CleanTrashNotesUseCase",
                        "Deleting ${selectedNotes.size} selected notes"
                    )


                    // Then delete the selected notes
                    selectedNotes.forEach { note ->
                        repository.deleteNoteById(note.id.toLong())
                    }

                    AppLogger.d(
                        "CleanTrashNotesUseCase",
                        "Deleted ${selectedNotes.size} selected notes"
                    )
                }
                true
            } catch (e: Exception) {
                AppLogger.e("CleanTrashNotesUseCase", "Error deleting selected notes: ${e.message}")
                false
            }
        }
}
