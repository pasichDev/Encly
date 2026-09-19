package com.pasich.encly.testutil

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class TestNotesRepository : NotesRepository {
    val allNotesWithTags = MutableStateFlow<List<NoteWithTag>>(emptyList())

    var insertResult: Long = DEFAULT_INSERT_ID
    var updateResult: Boolean = true
    var insertCalls: Int = 0
        private set
    var updateCalls: Int = 0
        private set

    private val notesByTag = mutableMapOf<Long, MutableStateFlow<List<NoteWithTag>>>()

    fun tagFlow(tagId: Long): MutableStateFlow<List<NoteWithTag>> =
        notesByTag.getOrPut(tagId) { MutableStateFlow(emptyList()) }

    override suspend fun getAllNotes(): Flow<List<Note>> =
        allNotesWithTags.map { notes -> notes.map { it.note } }

    override suspend fun getAllNotesWithTag(): Flow<List<NoteWithTag>> = allNotesWithTags

    override suspend fun getNotesByTagId(tagId: Long): Flow<List<NoteWithTag>> = tagFlow(tagId)

    override suspend fun getNoteById(noteId: Long): Note? =
        allNotesWithTags.value.firstOrNull { it.note.id == noteId }?.note

    override suspend fun insertNote(note: Note): Long {
        insertCalls += 1
        return insertResult
    }

    override suspend fun updateNote(note: Note): Boolean {
        updateCalls += 1
        return updateResult
    }

    override suspend fun getTrashNotes(): Flow<List<Note>> = flowOf(emptyList())

    override suspend fun deleteNoteById(id: Long): Boolean = true

    private companion object {
        const val DEFAULT_INSERT_ID = 42L
    }
}
