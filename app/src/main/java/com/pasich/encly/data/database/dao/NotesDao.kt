package com.pasich.encly.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions") // Reads per list, and one write per note column.
@Dao
interface NotesDao {

    /** The notes of the main list: neither in the trash nor in the archive. */
    @Transaction
    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 0")
    fun getAllNotesWithTags(): Flow<List<NoteWithTag>>

    /** The archive: archived notes that are not in the trash (a trashed archived note is in the trash). */
    @Transaction
    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 1")
    fun getArchivedNotesWithTags(): Flow<List<NoteWithTag>>

    @Transaction
    @Query(
        """
    SELECT * FROM notes 
    WHERE (:tagId IS NULL AND tagId IS NULL OR tagId = :tagId)
    AND isTrash = 0 AND isArchived = 0
    """,
    )
    fun getNotesByTagId(tagId: Long?): Flow<List<NoteWithTag>>

    /** The archived notes (not in the trash) of one tag: the way to a hidden tag's archived notes. */
    @Transaction
    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 1 AND tagId = :tagId")
    fun getArchivedNotesByTagId(tagId: Long): Flow<List<NoteWithTag>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :noteId")
    fun observeNoteWithTag(noteId: Long): Flow<NoteWithTag?>

    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 0")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrash = 1")
    fun getTrashNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Long): Note?

    // Single-column writes: an action on one property must never write back the rest of a row
    // that a screen read earlier (a stale tag, colour or pin).
    @Query("UPDATE notes SET color = :color WHERE id = :noteId")
    suspend fun updateColor(noteId: Long, color: String): Int

    @Query("UPDATE notes SET tagId = :tagId WHERE id = :noteId")
    suspend fun updateTagId(noteId: Long, tagId: Long?): Int

    @Query("UPDATE notes SET description = :description WHERE id = :noteId")
    suspend fun updateDescription(noteId: Long, description: String): Int

    /** An archived note is never pinned. */
    @Query("UPDATE notes SET isPinned = :pinned WHERE id = :noteId AND (:pinned = 0 OR isArchived = 0)")
    suspend fun updatePinned(noteId: Long, pinned: Boolean): Int

    /** Archiving unpins in the same statement. */
    @Query(
        "UPDATE notes SET isArchived = :archived, " +
            "isPinned = CASE WHEN :archived = 1 THEN 0 ELSE isPinned END WHERE id = :noteId",
    )
    suspend fun updateArchived(noteId: Long, archived: Boolean): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note): Int

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Long): Int
}
