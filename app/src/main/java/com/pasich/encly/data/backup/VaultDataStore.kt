package com.pasich.encly.data.backup

import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task

/** Every note (trash included), tag, task and sub-task in the vault, as stored. */
data class VaultSnapshot(
    val notes: List<Note>,
    val tags: List<Tag>,
    val tasks: List<Task>,
    val subtasks: List<Subtask> = emptyList(),
)

/**
 * The narrow storage contract encrypted export/import needs. The Room implementation is
 * [RoomVaultDataStore]; tests use an in-memory one.
 */
interface VaultDataStore {
    /** Capture one vault opening for a multi-step export or import. */
    fun bindToCurrentSession(): VaultDataStore = this

    /** Refuse a result if its vault closed while the operation was suspended. */
    fun assertCurrentSession() = Unit

    suspend fun snapshot(): VaultSnapshot

    /** Runs [block] atomically: if it throws, none of its writes are kept. */
    suspend fun <R> inTransaction(block: suspend () -> R): R

    suspend fun deleteAll()

    suspend fun insertTag(tag: Tag): Long

    suspend fun insertNote(note: Note): Long

    suspend fun insertTask(task: Task): Long

    suspend fun insertSubtask(subtask: Subtask): Long
}
