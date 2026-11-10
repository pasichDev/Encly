package com.pasich.encly.data.repository

import com.pasich.encly.data.database.DatabaseLease
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.repository.NotesRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "NotesRepository"

/** Keeps the opening that created this repository; delayed calls cannot cross a re-lock. */
@Suppress("TooManyFunctions") // One function per read and per single-column write.
@Inject
class NotesRepositoryImpl(private val databaseProvider: DatabaseProvider) : NotesRepository {
    private val lease: DatabaseLease? = databaseProvider.captureLease()

    private fun dao() = databaseProvider.getDatabase(lease ?: error("Vault locked")).notesDao()

    override fun getAllNotesWithTag(): Flow<List<NoteWithTags>> =
        daoFlow { dao().getAllNotesWithTags() }.map { it.map(NoteWithTags::normalized) }

    override fun getArchivedNotesWithTag(): Flow<List<NoteWithTags>> =
        daoFlow { dao().getArchivedNotesWithTags() }.map { it.map(NoteWithTags::normalized) }

    override fun getNotesOfTagWithArchived(tagId: Long): Flow<List<NoteWithTags>> =
        daoFlow { dao().getNotesOfTagWithArchived(tagId) }.map { it.map(NoteWithTags::normalized) }

    override fun observeNote(noteId: Long): Flow<NoteWithTags?> =
        daoFlow { dao().observeNoteWithTags(noteId) }.map { it?.normalized() }

    override fun tagNoteCounts(): Flow<Map<Long, Int>> =
        daoFlow { dao().observeTagNoteCounts() }.map { counts -> counts.associate { it.tagId to it.count } }

    override suspend fun setColor(noteId: Long, color: String): Result<Unit> = storageWrite(TAG, "setColor") {
        dao().updateColor(noteId, color).requireRows()
    }

    override suspend fun setTagged(noteId: Long, tagId: Long, tagged: Boolean): Result<Unit> =
        storageWrite(TAG, "setTagged") {
            // A tag that is already on (or off) is not a failure: the note is as asked.
            if (tagged) {
                dao().changeNoteTags(noteId, added = listOf(tagId), removed = emptyList())
            } else {
                dao().changeNoteTags(noteId, added = emptyList(), removed = listOf(tagId))
            }
        }

    override suspend fun clearTags(noteId: Long): Result<Unit> = storageWrite(TAG, "clearTags") {
        dao().clearNoteTags(noteId)
        Unit
    }

    override suspend fun setDescription(noteId: Long, description: String): Result<Unit> =
        storageWrite(TAG, "setDescription") { dao().updateDescription(noteId, description).requireRows() }

    override suspend fun setPinned(noteId: Long, pinned: Boolean): Result<Unit> = storageWrite(TAG, "setPinned") {
        dao().updatePinned(noteId, pinned)
        // An archived note refuses a pin without touching a row; that is not a failure.
        Unit
    }

    override suspend fun setArchived(noteId: Long, archived: Boolean): Result<Unit> =
        storageWrite(TAG, "setArchived") { dao().updateArchived(noteId, archived).requireRows() }

    override fun getTrashNotes(): Flow<List<Note>> = daoFlow { dao().getTrashNotes() }

    override suspend fun getNoteById(noteId: Long): Note? = dao().getNoteWithTags(noteId)?.normalized()?.note

    override suspend fun insertNote(note: Note): Result<Long> = storageWrite(TAG, "insertNote") {
        dao().insertNoteWithTags(note)
    }

    override suspend fun updateEdited(note: Note, base: Note?): Result<Unit> = storageWrite(TAG, "updateEdited") {
        if (base == null) {
            // Nothing to diff against: the note gets exactly the editor's tags, in the transaction of the row.
            return@storageWrite dao().updateEditedWithAllTags(note).requireRows()
        }
        dao().saveEdited(
            noteId = note.id,
            title = note.title,
            value = note.value,
            stamp = note.date != base.date || note.description != base.description,
            description = note.description,
            date = note.date,
            setPinned = note.isPinned != base.isPinned,
            pinned = note.isPinned,
            setColor = note.color != base.color,
            color = note.color,
            setTextSize = note.textSize != base.textSize,
            textSize = note.textSize,
            setLineSpacing = note.lineSpacing != base.lineSpacing,
            lineSpacing = note.lineSpacing,
            addTags = note.tagIds - base.tagIds,
            removeTags = base.tagIds - note.tagIds,
        ).requireRows()
    }

    override suspend fun setTrashed(noteId: Long, trashed: Boolean, at: Long?): Result<Unit> =
        storageWrite(TAG, "setTrashed") { dao().updateTrashed(noteId, trashed, at.takeIf { trashed }).requireRows() }

    override suspend fun deleteNoteById(id: Long): Result<Unit> = storageWrite(TAG, "deleteNoteById") {
        dao().deleteNoteById(id).requireRows()
    }

    override suspend fun stampUntimedTrash(at: Long): Result<Unit> = storageWrite(TAG, "stampUntimedTrash") {
        // Any number of rows, none included: an empty trash is not a failed write.
        dao().stampTrashedAt(at)
        Unit
    }

    override suspend fun deleteTrashedBefore(cutoff: Long): Result<Int> = storageWrite(TAG, "deleteTrashedBefore") {
        // Any number of rows, none included: nothing expired is not a failed write.
        dao().deleteTrashedBefore(cutoff)
    }
}

/** The tags in their display order (position, then id), and their ids on the note as well. */
internal fun NoteWithTags.normalized(): NoteWithTags {
    val sorted = tags.sortedWith(TAG_ORDER)
    return NoteWithTags(note.copy(tagIds = sorted.mapTo(LinkedHashSet()) { it.id }), sorted)
}

internal val TAG_ORDER: Comparator<Tag> = compareBy<Tag>({ it.position }, { it.id })
