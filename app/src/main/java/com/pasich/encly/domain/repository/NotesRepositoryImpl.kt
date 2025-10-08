package com.pasich.encly.domain.repository

import com.pasich.encly.data.datasource.local.DatabaseLocalDataSource
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.repository.NotesRepository
import javax.inject.Inject

class NotesRepositoryImpl @Inject constructor(
    private val databaseLocalDataSource: DatabaseLocalDataSource
) : NotesRepository {

    // Notes
    override suspend fun getAllNotes() = databaseLocalDataSource.getNotes()
    override suspend fun getAllNotesWithTag() = databaseLocalDataSource.getAllNotesWithTag()
    override suspend fun getTrashNotes() = databaseLocalDataSource.getTrashNotes()

    override suspend fun insertNote(note: Note): Long {
        return databaseLocalDataSource.insertNote(note)
    }

    override suspend fun updateNote(note: Note) = databaseLocalDataSource.updateNote(note)
    override suspend fun getNotesByTagId(tagId: Long) = databaseLocalDataSource.getNotesByTagId(tagId)
    override suspend fun deleteNoteById(id: Long): Boolean = databaseLocalDataSource.deleteNoteById(id)
    override suspend fun getNoteById(noteId: Long) = databaseLocalDataSource.getNoteById(noteId)

}
