package com.pasich.encly.data.repository

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import kotlinx.coroutines.flow.Flow

interface NotesRepository {

    // Notes
    suspend fun getAllNotes(): Flow<List<Note>>
    suspend fun getAllNotesWithTag(): Flow<List<NoteWithTag>>
    suspend fun getNotesByTagId(tagId: Long): Flow<List<NoteWithTag>>
    suspend fun getNoteById(noteId: Long): Note?
    suspend fun insertNote(note: Note): Long
    suspend fun updateNote(note: Note): Boolean

    // Trash
    suspend fun getTrashNotes(): Flow<List<Note>>
    suspend fun deleteNoteById(id: Long): Boolean
}