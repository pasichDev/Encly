package com.pasich.encly.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteTag
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.data.model.TagNoteCount
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions") // Reads per list, and one write per note column.
@Dao
public interface NotesDao {

    /** The notes of the main list: neither in the trash nor in the archive. */
    @Transaction
    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 0")
    public fun getAllNotesWithTags(): Flow<List<NoteWithTags>>

    /** The archive: archived notes that are not in the trash (a trashed archived note is in the trash). */
    @Transaction
    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isArchived = 1")
    public fun getArchivedNotesWithTags(): Flow<List<NoteWithTags>>

    /**
     * The notes (not in the trash) that carry the tag [tagId], archived ones included: the way to a hidden tag's
     * archived notes. One query, so a note being archived or unarchived is never read twice, and a note is listed
     * once however many tags it has.
     */
    @Transaction
    @Query("SELECT * FROM notes WHERE isTrash = 0 AND id IN (SELECT noteId FROM note_tags WHERE tagId = :tagId)")
    public fun getNotesOfTagWithArchived(tagId: Long): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :noteId")
    public fun observeNoteWithTags(noteId: Long): Flow<NoteWithTags?>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :noteId")
    public suspend fun getNoteWithTags(noteId: Long): NoteWithTags?

    /** How many notes outside the trash, archived ones included, carry each tag that has any. */
    @Query(
        "SELECT note_tags.tagId AS tagId, COUNT(*) AS count FROM note_tags " +
            "INNER JOIN notes ON notes.id = note_tags.noteId WHERE notes.isTrash = 0 GROUP BY note_tags.tagId",
    )
    public fun observeTagNoteCounts(): Flow<List<TagNoteCount>>

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

    /** Links that exist already are kept as they are. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertNoteTags(links: List<NoteTag>)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId IN (:tagIds)")
    public suspend fun deleteNoteTags(noteId: Long, tagIds: List<Long>): Int

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId NOT IN (:keep)")
    public suspend fun deleteNoteTagsExcept(noteId: Long, keep: List<Long>): Int

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    public suspend fun clearNoteTags(noteId: Long): Int

    /**
     * Adds the tags [added] to the note and takes [removed] off it, leaving its other tags as they are: a tag
     * changed meanwhile from another window or the list stays changed.
     */
    @Transaction
    public suspend fun changeNoteTags(noteId: Long, added: Collection<Long>, removed: Collection<Long>) {
        if (removed.isNotEmpty()) deleteNoteTags(noteId, removed.toList())
        if (added.isNotEmpty()) insertNoteTags(added.map { NoteTag(noteId, it) })
    }

    /** Makes [tagIds] exactly the note's tags. */
    @Transaction
    public suspend fun setNoteTags(noteId: Long, tagIds: Collection<Long>) {
        deleteNoteTagsExcept(noteId, tagIds.toList())
        if (tagIds.isNotEmpty()) insertNoteTags(tagIds.map { NoteTag(noteId, it) })
    }

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
     * The tags are not a column: see [saveEdited].
     * [stamp] also writes [date] and [description] (the content changed). A pin never lands on an archived note.
     */
    @Suppress("LongParameterList") // One value and one flag per column of the row.
    @Query(
        """
    UPDATE notes SET title = :title, value = :value,
    description = CASE WHEN :stamp = 1 THEN :description ELSE description END,
    date = CASE WHEN :stamp = 1 THEN :date ELSE date END,
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
        setPinned: Boolean,
        pinned: Boolean,
        setColor: Boolean,
        color: String,
        setTextSize: Boolean,
        textSize: Int?,
        setLineSpacing: Boolean,
        lineSpacing: Int?,
    ): Int

    /**
     * [updateEdited] and the editor's tag changes ([addTags], [removeTags]) in one transaction. The tags change only
     * when the row was there to update.
     */
    @Suppress("LongParameterList") // updateEdited's columns and the tag changes.
    @Transaction
    public suspend fun saveEdited(
        noteId: Long,
        title: String,
        value: String,
        stamp: Boolean,
        description: String,
        date: Long,
        setPinned: Boolean,
        pinned: Boolean,
        setColor: Boolean,
        color: String,
        setTextSize: Boolean,
        textSize: Int?,
        setLineSpacing: Boolean,
        lineSpacing: Int?,
        addTags: Collection<Long>,
        removeTags: Collection<Long>,
    ): Int {
        val rows = updateEdited(
            noteId, title, value, stamp, description, date, setPinned, pinned, setColor, color,
            setTextSize, textSize, setLineSpacing, lineSpacing,
        )
        if (rows > 0) changeNoteTags(noteId, addTags, removeTags)
        return rows
    }

    /** Writes every column the editor owns of [note], and makes [Note.tagIds] exactly its tags, in one transaction. */
    @Transaction
    public suspend fun updateEditedWithAllTags(note: Note): Int {
        val rows = updateEdited(
            note.id, note.title, note.value, true, note.description, note.date, true, note.isPinned, true, note.color,
            true, note.textSize, true, note.lineSpacing,
        )
        if (rows > 0) setNoteTags(note.id, note.tagIds)
        return rows
    }

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

    /** Inserts [note] with its tags [Note.tagIds] in one transaction; the new row id. */
    @Transaction
    public suspend fun insertNoteWithTags(note: Note): Long {
        val id = insertNote(note)
        if (note.tagIds.isNotEmpty()) insertNoteTags(note.tagIds.map { NoteTag(id, it) })
        return id
    }

    @Update
    public suspend fun updateNote(note: Note): Int

    @Query("DELETE FROM notes WHERE id = :noteId")
    public suspend fun deleteNoteById(noteId: Long): Int
}
