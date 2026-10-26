package com.pasich.encly.testutil

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.repository.NotesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

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

    val archivedNotesWithTags = MutableStateFlow<List<NoteWithTag>>(emptyList())

    override fun getArchivedNotesWithTag(): Flow<List<NoteWithTag>> = archivedNotesWithTags

    override fun getArchivedNotesByTagId(tagId: Long): Flow<List<NoteWithTag>> =
        archivedNotesWithTags.map { list -> list.filter { it.note.tagId == tagId } }

    override fun observeNote(noteId: Long): Flow<NoteWithTag?> = combine(allNotesWithTags, archivedNotesWithTags) {
            a,
            b,
        ->
        (a + b).firstOrNull { it.note.id == noteId }
    }

    /** Every single-column write, as "column:id=value", in call order. */
    val columnWrites = mutableListOf<String>()

    var columnWriteResult: Boolean = true

    private fun column(write: String): Result<Unit> {
        columnWrites += write
        return if (columnWriteResult) Result.success(Unit) else Result.failure(IllegalStateException())
    }

    override suspend fun setColor(noteId: Long, color: String) = column("color:$noteId=$color")

    override suspend fun setTag(noteId: Long, tagId: Long?) = column("tag:$noteId=$tagId")

    override suspend fun setDescription(noteId: Long, description: String) = column("description:$noteId=$description")

    override suspend fun setPinned(noteId: Long, pinned: Boolean) = column("pinned:$noteId=$pinned")

    override suspend fun setArchived(noteId: Long, archived: Boolean) = column("archived:$noteId=$archived")

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
