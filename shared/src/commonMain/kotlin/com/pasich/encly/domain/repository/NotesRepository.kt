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

    /**
     * Saves what an editor owns of [note]: its title and content, and each of tag, pin, colour, text size and line
     * spacing that differs from [base] (all of them when [base] is null), plus the date and summary when those
     * differ. Never the trash or archive state, and not a column the editor did not change: a note trashed, archived
     * or pinned from another window meanwhile stays so.
     */
    suspend fun updateEdited(note: Note, base: Note?): Result<Unit>

    /** Moves the note to the trash ([at]: when) or back, writing those two columns only. */
    suspend fun setTrashed(noteId: Long, trashed: Boolean, at: Long?): Result<Unit>
    suspend fun deleteNoteById(id: Long): Result<Unit>

    /** Gives the trashed notes that have no trash time (imported ones) [at], so the trash can age them. */
    suspend fun stampUntimedTrash(at: Long): Result<Unit>

    /**
     * Deletes for good, in one write, the notes that are in the trash and were trashed before [cutoff]. A note
     * restored meanwhile is not in the trash any more and is kept. The number of notes deleted.
     */
    suspend fun deleteTrashedBefore(cutoff: Long): Result<Int>
}
