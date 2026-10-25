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

    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 0")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrash = 1")
    fun getTrashNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Long): Note?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note): Int

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Long): Int
}
