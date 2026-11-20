package com.pasich.encly.domain.usecase.note

import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import dev.zacsweers.metro.Inject

/** Deletes notes from the trash for good, each request in one write: all of them go or none. */
@Inject
class CleanTrashNotesUseCase(private val repository: NotesRepository) {
    /** Empties the trash. */
    suspend operator fun invoke(): Result<Unit> = repository.deleteAllTrashed().map { }

    /** Deletes [selectedNotes] for good; one that is not in the trash any more is kept. */
    suspend fun cleanSelectedNotes(selectedNotes: List<Note>): Result<Unit> =
        repository.deleteTrashedNotes(selectedNotes.map { it.id }).map { }
}
