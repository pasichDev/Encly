package com.pasich.encly.data.backup

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteTag
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task

/**
 * Every note (trash included), tag, task and sub-task in the vault, as stored, and which note has which tag
 * ([noteTags]; the notes' own [Note.tagIds] are not filled here).
 */
data class VaultSnapshot(
    val notes: List<Note>,
    val tags: List<Tag>,
    val tasks: List<Task>,
    val subtasks: List<Subtask> = emptyList(),
    val noteTags: List<NoteTag> = emptyList(),
) {
    private val tagsByNote: Map<Long, List<Tag>> by lazy {
        val byId = tags.associateBy { it.id }
        noteTags.groupBy({ it.noteId }, { it.tagId }).mapValues { (_, ids) ->
            ids.distinct().mapNotNull(byId::get).sortedWith(compareBy<Tag>({ it.position }, { it.id }))
        }
    }

    /** The tags of note [noteId] that are in this snapshot, in their display order (position, then id). */
    fun tagsOf(noteId: Long): List<Tag> = tagsByNote[noteId].orEmpty()
}

/**
 * The narrow storage contract encrypted export/import needs. The Room implementation is
 * [RoomVaultDataStore]; tests use an in-memory one.
 */
@Suppress("TooManyFunctions") // One function per vault table operation of the contract.
interface VaultDataStore {
    /** Capture one vault opening for a multi-step export or import. */
    fun bindToCurrentSession(): VaultDataStore = this

    /** Refuse a result if its vault closed while the operation was suspended. */
    fun assertCurrentSession() = Unit

    suspend fun snapshot(): VaultSnapshot

    /** Only note [noteId] and its tags: for exporting one note without reading the whole vault. */
    suspend fun snapshotOfNote(noteId: Long): VaultSnapshot = snapshot().let { all ->
        val note = all.notes.firstOrNull { it.id == noteId }
        val links = all.noteTags.filter { note != null && it.noteId == note.id }
        val tagIds = links.mapTo(HashSet()) { it.tagId }
        VaultSnapshot(listOfNotNull(note), all.tags.filter { it.id in tagIds }, emptyList(), noteTags = links)
    }

    /** Runs [block] atomically: if it throws, none of its writes are kept. */
    suspend fun <R> inTransaction(block: suspend () -> R): R

    suspend fun deleteAll()

    suspend fun insertTag(tag: Tag): Long

    suspend fun insertNote(note: Note): Long

    /** Puts the tag on the note; both are in the vault already. */
    suspend fun insertNoteTag(link: NoteTag)

    suspend fun insertTask(task: Task): Long

    suspend fun insertSubtask(subtask: Subtask): Long
}
