package com.pasich.encly.domain.usecase.note

import android.util.Log
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UseCase для масового видалення нотаток з кошика з очищенням фотографій
 */
class CleanTrashNotesUseCase @Inject constructor(
    private val repository: NotesRepository
) {

    /**
     * Видаляє всі нотатки з кошика з очищенням фотографій
     */
    suspend operator fun invoke(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            // Отримуємо всі нотатки з кошика
            repository.getTrashNotes().collect { trashNotes ->
                if (trashNotes.isNotEmpty()) {
                    Log.d("CleanTrashNotesUseCase", "Видалення ${trashNotes.size} нотаток з кошика")

                    // Потім видаляємо всі нотатки
                    trashNotes.forEach { note ->
                        repository.deleteNoteById(note.id.toLong())
                    }

                    Log.d(
                        "CleanTrashNotesUseCase",
                        "Успішно видалено ${trashNotes.size} нотаток з кошика"
                    )
                }
            }
            true
        } catch (e: Exception) {
            Log.e("CleanTrashNotesUseCase", "Помилка очищення кошика: ${e.message}")
            false
        }
    }

    /**
     * Видаляє вибрані нотатки з кошика з очищенням фотографій
     */
    suspend fun cleanSelectedNotes(selectedNotes: List<Note>): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext try {
                if (selectedNotes.isNotEmpty()) {
                    Log.d(
                        "CleanTrashNotesUseCase",
                        "Видалення ${selectedNotes.size} вибраних нотаток"
                    )


                    // Потім видаляємо вибрані нотатки
                    selectedNotes.forEach { note ->
                        repository.deleteNoteById(note.id.toLong())
                    }

                    Log.d(
                        "CleanTrashNotesUseCase",
                        "Успішно видалено ${selectedNotes.size} вибраних нотаток"
                    )
                }
                true
            } catch (e: Exception) {
                Log.e("CleanTrashNotesUseCase", "Помилка видалення вибраних нотаток: ${e.message}")
                false
            }
        }
}
