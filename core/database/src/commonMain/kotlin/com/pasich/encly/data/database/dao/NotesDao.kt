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
public interface NotesDao {

    /** The notes of the main list: neither in the trash nor in the archive. */
    @Transaction
    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 0")
    public fun getAllNotesWithTags(): Flow<List<NoteWithTag>>

    /** The archive: archived notes that are not in the trash (a trashed archived note is in the trash). */
    @Transaction
    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 1")
    public fun getArchivedNotesWithTags(): Flow<List<NoteWithTag>>

    @Transaction
    @Query(
        """
    SELECT * FROM notes 
    WHERE (:tagId IS NULL AND tagId IS NULL OR tagId = :tagId)
    AND isTrash = 0 AND isArchived = 0
    """,
    )
    public fun getNotesByTagId(tagId: Long?): Flow<List<NoteWithTag>>

    /** The archived notes (not in the trash) of one tag: the way to a hidden tag's archived notes. */
    @Transaction
    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 1 AND tagId = :tagId")
    public fun getArchivedNotesByTagId(tagId: Long): Flow<List<NoteWithTag>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :noteId")
    public fun observeNoteWithTag(noteId: Long): Flow<NoteWithTag?>

    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 0")
    public fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrash = 1")
    public fun getTrashNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    public suspend fun getNoteById(noteId: Long): Note?

    // Single-column writes: an action on one property must never write back the rest of a row
    // that a screen read earlier (a stale tag, colour or pin).
    @Query("UPDATE notes SET color = :color WHERE id = :noteId")
    public suspend fun updateColor(noteId: Long, color: String): Int

    @Query("UPDATE notes SET tagId = :tagId WHERE id = :noteId")
    public suspend fun updateTagId(noteId: Long, tagId: Long?): Int

    @Query("UPDATE notes SET description = :description WHERE id = :noteId")
    public suspend fun updateDescription(noteId: Long, description: String): Int

    /** An archived note is never pinned. */
    @Query("UPDATE notes SET isPinned = :pinned WHERE id = :noteId AND (:pinned = 0 OR isArchived = 0)")
    public suspend fun updatePinned(noteId: Long, pinned: Boolean): Int

    /** Archiving unpins in the same statement. */
    @Query(
        "UPDATE notes SET isArchived = :archived, " +
            "isPinned = CASE WHEN :archived = 1 THEN 0 ELSE isPinned END WHERE id = :noteId",
    )
    public suspend fun updateArchived(noteId: Long, archived: Boolean): Int

    /** Moves a note to the trash (with the time) or back, and writes nothing else of the row. */
    @Query("UPDATE notes SET isTrash = :trashed, trashedAt = :trashedAt WHERE id = :noteId")
    public suspend fun updateTrashed(noteId: Long, trashed: Boolean, trashedAt: Long?): Int

    /**
     * What an editor saves: the title and content always, and every other column only when its flag says the editor
     * changed it. A note trashed, archived, pinned or recoloured from another window meanwhile keeps that: nothing
     * here writes `isTrash`, `trashedAt` or `isArchived`, and a column the editor left alone is not written back.
     * [stamp] also writes [date] and [description] (the content changed). A pin never lands on an archived note.
     */
    @Suppress("LongParameterList") // One value and one flag per column of the row.
    @Query(
        """
    UPDATE notes SET title = :title, value = :value,
    description = CASE WHEN :stamp = 1 THEN :description ELSE description END,
    date = CASE WHEN :stamp = 1 THEN :date ELSE date END,
    tagId = CASE WHEN :setTag = 1 THEN :tagId ELSE tagId END,
    isPinned = CASE WHEN :setPinned = 1 THEN (:pinned = 1 AND isArchived = 0) ELSE isPinned END,
    color = CASE WHEN :setColor = 1 THEN :color ELSE color END,
    textSize = CASE WHEN :setTextSize = 1 THEN :textSize ELSE textSize END,
    lineSpacing = CASE WHEN :setLineSpacing = 1 THEN :lineSpacing ELSE lineSpacing END
    WHERE id = :noteId
    """,
    )
    public suspend fun updateEdited(
        noteId: Long,
        title: String,
        value: String,
        stamp: Boolean,
        description: String,
        date: Long,
        setTag: Boolean,
        tagId: Long?,
        setPinned: Boolean,
        pinned: Boolean,
        setColor: Boolean,
        color: String,
        setTextSize: Boolean,
        textSize: Int?,
        setLineSpacing: Boolean,
        lineSpacing: Int?,
    ): Int

    /** Gives the trashed notes that have no trash time (imported ones) [at], so the trash can age them. */
    @Query("UPDATE notes SET trashedAt = :at WHERE isTrash = 1 AND trashedAt IS NULL")
    public suspend fun stampTrashedAt(at: Long): Int

    /**
     * Deletes the trashed notes that were trashed before [cutoff], in one statement: a note restored a moment ago is
     * no longer trashed when the delete runs, so it stays. Returns the number of rows deleted.
     */
    @Query("DELETE FROM notes WHERE isTrash = 1 AND trashedAt IS NOT NULL AND trashedAt < :cutoff")
    public suspend fun deleteTrashedBefore(cutoff: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public suspend fun insertNote(note: Note): Long

    @Update
    public suspend fun updateNote(note: Note): Int

    @Query("DELETE FROM notes WHERE id = :noteId")
    public suspend fun deleteNoteById(noteId: Long): Int
}
