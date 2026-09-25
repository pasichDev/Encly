package com.pasich.encly.testutil

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.repository.NotesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class TestNotesRepository : NotesRepository {
    val allNotesWithTags = MutableStateFlow<List<NoteWithTag>>(emptyList())

    /** The id an insert returns; zero or less makes the insert fail. */
    var insertResult: Long = DEFAULT_INSERT_ID
    var updateResult: Boolean = true
    var insertCalls: Int = 0
        private set
    var updateCalls: Int = 0
        private set

    private val notesByTag = mutableMapOf<Long, MutableStateFlow<List<NoteWithTag>>>()

    fun tagFlow(tagId: Long): MutableStateFlow<List<NoteWithTag>> = notesByTag.getOrPut(tagId) {
        MutableStateFlow(emptyList())
    }

    override fun getAllNotesWithTag(): Flow<List<NoteWithTag>> = allNotesWithTags

    override fun getNotesByTagId(tagId: Long): Flow<List<NoteWithTag>> = tagFlow(tagId)

    override suspend fun getNoteById(noteId: Long): Note? = allNotesWithTags.value.firstOrNull {
        it.note.id == noteId
    }?.note

    /** Every note passed to [insertNote], in call order. */
    val insertedNotes = mutableListOf<Note>()

    /** When set, [insertNote] waits for it: a test can act while an insert is in flight. */
    var insertGate: CompletableDeferred<Unit>? = null

    override suspend fun insertNote(note: Note): Result<Long> {
        insertCalls += 1
        insertedNotes += note
        insertGate?.await()
        return if (insertResult > 0L) Result.success(insertResult) else Result.failure(IllegalStateException())
    }

    /** Every note passed to [updateNote], in call order. */
    val updatedNotes = mutableListOf<Note>()

    override suspend fun updateNote(note: Note): Result<Unit> {
        updateCalls += 1
        updatedNotes += note
        return if (updateResult) Result.success(Unit) else Result.failure(IllegalStateException())
    }

    val trashNotes = MutableStateFlow<List<Note>>(emptyList())

    override fun getTrashNotes(): Flow<List<Note>> = trashNotes

    /** Every id passed to [deleteNoteById], in call order. */
    val deletedIds = mutableListOf<Long>()

    override suspend fun deleteNoteById(id: Long): Result<Unit> {
        deletedIds += id
        return Result.success(Unit)
    }

    private companion object {
        const val DEFAULT_INSERT_ID = 42L
    }
}
