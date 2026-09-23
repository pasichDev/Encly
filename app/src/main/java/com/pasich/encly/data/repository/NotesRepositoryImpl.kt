package com.pasich.encly.data.repository

import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NotesRepository"

/**
 * Resolves the DAO from the currently unlocked Room instance on every call. The encrypted
 * database is closed on every app background, so a cached AppDatabase or DAO would point at a
 * closed instance after the first re-lock/unlock cycle.
 */
@Singleton
class NotesRepositoryImpl @Inject constructor(private val databaseProvider: DatabaseProvider) : NotesRepository {

    private fun dao() = databaseProvider.getDatabase().notesDao()

    override fun getAllNotesWithTag(): Flow<List<NoteWithTag>> = daoFlow { dao().getAllNotesWithTags() }

    override fun getNotesByTagId(tagId: Long): Flow<List<NoteWithTag>> = daoFlow { dao().getNotesByTagId(tagId) }

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
