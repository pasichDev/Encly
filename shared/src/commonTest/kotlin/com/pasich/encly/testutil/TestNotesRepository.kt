package com.pasich.encly.testutil

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.domain.repository.NotesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class TestNotesRepository : NotesRepository {
    val allNotesWithTags = MutableStateFlow<List<NoteWithTags>>(emptyList())

    /** The id an insert returns; zero or less makes the insert fail. */
    var insertResult: Long = DEFAULT_INSERT_ID
    var updateResult: Boolean = true
    var insertCalls: Int = 0
        private set
    var updateCalls: Int = 0
        private set

    private val notesByTag = mutableMapOf<Long, MutableStateFlow<List<NoteWithTags>>>()

    fun tagFlow(tagId: Long): MutableStateFlow<List<NoteWithTags>> = notesByTag.getOrPut(tagId) {
        MutableStateFlow(emptyList())
    }

    override fun getAllNotesWithTag(): Flow<List<NoteWithTags>> = allNotesWithTags

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

    /** Every note passed to [updateEdited], and every trash write as the note it was made on, in call order. */
    val updatedNotes = mutableListOf<Note>()

    /** The `base` of every [updateEdited], in call order. */
    val updateBases = mutableListOf<Note?>()

    override suspend fun updateEdited(note: Note, base: Note?): Result<Unit> {
        updateCalls += 1
        updatedNotes += note
        updateBases += base
        return if (updateResult) Result.success(Unit) else Result.failure(IllegalStateException())
    }

    override suspend fun setTrashed(noteId: Long, trashed: Boolean, at: Long?): Result<Unit> {
        updateCalls += 1
        val known =
            (allNotesWithTags.value.map { it.note } + archivedNotesWithTags.value.map { it.note } + trashNotes.value)
                .firstOrNull { it.id == noteId } ?: Note(id = noteId)
        updatedNotes += known.copy(isTrash = trashed, trashedAt = at)
        return if (updateResult) Result.success(Unit) else Result.failure(IllegalStateException())
    }

    val archivedNotesWithTags = MutableStateFlow<List<NoteWithTags>>(emptyList())

    override fun getArchivedNotesWithTag(): Flow<List<NoteWithTags>> = archivedNotesWithTags

    override fun getNotesOfTagWithArchived(tagId: Long): Flow<List<NoteWithTags>> =
        combine(tagFlow(tagId), archivedNotesWithTags) { live, archived ->
            live + archived.filter { item -> item.tags.any { it.id == tagId } }
        }

    override fun observeNote(noteId: Long): Flow<NoteWithTags?> = combine(allNotesWithTags, archivedNotesWithTags) {
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

    override suspend fun setTagged(noteId: Long, tagId: Long, tagged: Boolean) =
        column(if (tagged) "tag:$noteId=$tagId" else "untag:$noteId=$tagId")

    override suspend fun clearTags(noteId: Long) = column("tag:$noteId=null")

    /** What [tagNoteCounts] gives: every note of the list and the archive, counted per tag. */
    override fun tagNoteCounts(): Flow<Map<Long, Int>> = combine(allNotesWithTags, archivedNotesWithTags) { a, b ->
        (a + b).flatMap { item -> item.tags.map { it.id } }.groupingBy { it }.eachCount()
    }

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

    override suspend fun stampUntimedTrash(at: Long): Result<Unit> {
        trashNotes.value = trashNotes.value.map { if (it.trashedAt == null) it.copy(trashedAt = at) else it }
        return Result.success(Unit)
    }

    /** Every cutoff passed to [deleteTrashedBefore], in call order. */
    val purgeCutoffs = mutableListOf<Long>()

    override suspend fun deleteTrashedBefore(cutoff: Long): Result<Int> {
        purgeCutoffs += cutoff
        val (gone, kept) = trashNotes.value.partition { it.isTrash && (it.trashedAt ?: Long.MAX_VALUE) < cutoff }
        deletedIds += gone.map { it.id }
        trashNotes.value = kept
        return Result.success(gone.size)
    }

    private companion object {
        const val DEFAULT_INSERT_ID = 42L
    }
}
