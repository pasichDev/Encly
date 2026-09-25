package com.pasich.encly.domain.repository

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import kotlinx.coroutines.flow.Flow

/**
 * The notes in the encrypted vault. Flows fail through the flow; a read throws; a write
 * returns [Result.failure] (including when no row was written) and never a sentinel value.
 */
interface NotesRepository {
    fun getAllNotesWithTag(): Flow<List<NoteWithTag>>
    fun getNotesByTagId(tagId: Long): Flow<List<NoteWithTag>>
    fun getTrashNotes(): Flow<List<Note>>

    suspend fun getNoteById(noteId: Long): Note?

    /** The new note's row id. */
    suspend fun insertNote(note: Note): Result<Long>
    suspend fun updateNote(note: Note): Result<Unit>
    suspend fun deleteNoteById(id: Long): Result<Unit>
}
