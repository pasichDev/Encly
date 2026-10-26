package com.pasich.encly.domain.repository

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import kotlinx.coroutines.flow.Flow

/**
 * The notes in the encrypted vault. Flows fail through the flow; a read throws; a write
 * returns [Result.failure] (including when no row was written) and never a sentinel value.
 */
@Suppress("TooManyFunctions") // One function per read and per single-column write.
interface NotesRepository {
    fun getAllNotesWithTag(): Flow<List<NoteWithTag>>
    fun getNotesByTagId(tagId: Long): Flow<List<NoteWithTag>>
    fun getArchivedNotesWithTag(): Flow<List<NoteWithTag>>
    fun getArchivedNotesByTagId(tagId: Long): Flow<List<NoteWithTag>>
    fun getTrashNotes(): Flow<List<Note>>

    /** The note as stored now (null once it is gone), following every change to it. */
    fun observeNote(noteId: Long): Flow<NoteWithTag?>

    // Each writes one column of the stored row and nothing else, so a caller holding an older
    // copy of the note cannot undo another change.
    suspend fun setColor(noteId: Long, color: String): Result<Unit>
    suspend fun setTag(noteId: Long, tagId: Long?): Result<Unit>
    suspend fun setDescription(noteId: Long, description: String): Result<Unit>

    /** Pins or unpins; an archived note stays unpinned. */
    suspend fun setPinned(noteId: Long, pinned: Boolean): Result<Unit>

    /** Archives (and unpins) or unarchives. */
    suspend fun setArchived(noteId: Long, archived: Boolean): Result<Unit>

    suspend fun getNoteById(noteId: Long): Note?

    /** The new note's row id. */
    suspend fun insertNote(note: Note): Result<Long>
    suspend fun updateNote(note: Note): Result<Unit>
    suspend fun deleteNoteById(id: Long): Result<Unit>
}
