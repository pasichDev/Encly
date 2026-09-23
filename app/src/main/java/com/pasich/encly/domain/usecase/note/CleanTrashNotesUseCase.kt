package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.common.suspendRunCatching
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Deletes notes from the trash for good, stopping at the first one that fails. */
class CleanTrashNotesUseCase @Inject constructor(private val repository: NotesRepository) {
    /** Empties the trash. */
    suspend operator fun invoke(): Result<Unit> {
        val trashNotes = suspendRunCatching { repository.getTrashNotes().first() }
            .getOrElse { return Result.failure(it) }
        return trashNotes.deleteAll()
    }

    suspend fun cleanSelectedNotes(selectedNotes: List<Note>): Result<Unit> = selectedNotes.deleteAll()

    private suspend fun List<Note>.deleteAll(): Result<Unit> {
        for (note in this) {
            repository.deleteNoteById(note.id).onFailure { return Result.failure(it) }
        }
        return Result.success(Unit)
    }
}
