package com.pasich.encly.data.repository

import com.pasich.encly.data.database.DatabaseLease
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

private const val TAG = "NotesRepository"

/** Keeps the opening that created this repository; delayed calls cannot cross a re-lock. */
@Suppress("TooManyFunctions") // One function per read and per single-column write.
class NotesRepositoryImpl @Inject constructor(private val databaseProvider: DatabaseProvider) : NotesRepository {
    private val lease: DatabaseLease? = databaseProvider.captureLease()

    private fun dao() = databaseProvider.getDatabase(lease ?: error("Vault locked")).notesDao()

    override fun getAllNotesWithTag(): Flow<List<NoteWithTag>> = daoFlow { dao().getAllNotesWithTags() }

    override fun getNotesByTagId(tagId: Long): Flow<List<NoteWithTag>> = daoFlow { dao().getNotesByTagId(tagId) }

    override fun getArchivedNotesWithTag(): Flow<List<NoteWithTag>> = daoFlow { dao().getArchivedNotesWithTags() }

    override fun getArchivedNotesByTagId(tagId: Long): Flow<List<NoteWithTag>> =
        daoFlow { dao().getArchivedNotesByTagId(tagId) }

    override fun observeNote(noteId: Long): Flow<NoteWithTag?> = daoFlow { dao().observeNoteWithTag(noteId) }

    override suspend fun setColor(noteId: Long, color: String): Result<Unit> = storageWrite(TAG, "setColor") {
        dao().updateColor(noteId, color).requireRows()
    }

    override suspend fun setTag(noteId: Long, tagId: Long?): Result<Unit> = storageWrite(TAG, "setTag") {
        dao().updateTagId(noteId, tagId).requireRows()
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

    override suspend fun getNoteById(noteId: Long): Note? = dao().getNoteById(noteId)

    override suspend fun insertNote(note: Note): Result<Long> = storageWrite(TAG, "insertNote") {
        dao().insertNote(note)
    }

    override suspend fun updateNote(note: Note): Result<Unit> = storageWrite(TAG, "updateNote") {
        dao().updateNote(note).requireRows()
    }

    override suspend fun deleteNoteById(id: Long): Result<Unit> = storageWrite(TAG, "deleteNoteById") {
        dao().deleteNoteById(id).requireRows()
    }
}
