package com.pasich.encly.domain.repository

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import kotlinx.coroutines.flow.Flow

/**
 * The notes in the encrypted vault. Flows fail through the flow; a read throws; a write
 * returns [Result.failure] (including when no row was written) and never a sentinel value.
 */
@Suppress("TooManyFunctions") // One function per read and per single-column write.
interface NotesRepository {
    // A note read here carries its tags twice, the same: [NoteWithTags.tags] sorted by the tags' order (position,
    // then id), and their ids in [Note.tagIds].
    fun getAllNotesWithTag(): Flow<List<NoteWithTags>>
    fun getArchivedNotesWithTag(): Flow<List<NoteWithTags>>

    /** The notes (not in the trash, archived ones included) that carry the tag [tagId], in one read. */
    fun getNotesOfTagWithArchived(tagId: Long): Flow<List<NoteWithTags>>
    fun getTrashNotes(): Flow<List<Note>>

    /** The note as stored now (null once it is gone), following every change to it. */
    fun observeNote(noteId: Long): Flow<NoteWithTags?>

    /**
     * How many notes outside the trash (archived ones included) carry each tag, by tag id; a tag without notes is
     * left out. One read, so a note being archived is never counted twice.
     */
    fun tagNoteCounts(): Flow<Map<Long, Int>>

    // Each writes one column of the stored row (or one tag of the note) and nothing else, so a caller holding an
    // older copy of the note cannot undo another change.
    suspend fun setColor(noteId: Long, color: String): Result<Unit>

    /** Puts the tag [tagId] on the note ([tagged]) or takes it off; the note's other tags stay as they are. */
    suspend fun setTagged(noteId: Long, tagId: Long, tagged: Boolean): Result<Unit>

    /** Takes every tag off the note. */
    suspend fun clearTags(noteId: Long): Result<Unit>
    suspend fun setDescription(noteId: Long, description: String): Result<Unit>

    /** Pins or unpins; an archived note stays unpinned. */
    suspend fun setPinned(noteId: Long, pinned: Boolean): Result<Unit>

    /** Archives (and unpins) or unarchives. */
    suspend fun setArchived(noteId: Long, archived: Boolean): Result<Unit>

    /** The note with its [Note.tagIds]. */
    suspend fun getNoteById(noteId: Long): Note?

    /** The new note's row id. It gets the tags [Note.tagIds], in the same write. */
    suspend fun insertNote(note: Note): Result<Long>

    /**
     * Saves what an editor owns of [note]: its title and content, and each of pin, colour, text size and line
     * spacing that differs from [base] (all of them when [base] is null), plus the date and summary when those
     * differ. Its tags change by what differs from [base]: a tag put on or taken off since [base] is put on or
     * taken off, and a tag the editor did not touch stays as another window left it (with no [base], the note gets
     * exactly [Note.tagIds]). Never the trash or archive state, and not a column the editor did not change: a note
     * trashed, archived or pinned from another window meanwhile stays so.
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
