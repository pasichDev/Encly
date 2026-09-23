package com.pasich.encly.testutil

import com.pasich.encly.data.backup.VaultDataStore
import com.pasich.encly.data.backup.VaultSnapshot
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task

/**
 * A [VaultDataStore] with the Room vault's relevant semantics: autoincrement ids, a random uid
 * for a blank one (the VaultSchema trigger), unique uids, and all-or-nothing transactions.
 */
internal class InMemoryVaultDataStore : VaultDataStore {
    val notes = mutableListOf<Note>()
    val tags = mutableListOf<Tag>()
    val tasks = mutableListOf<Task>()

    /** Makes the n-th insert (1-based, counted across tables) fail, like a constraint error. */
    var failOnInsert: Int? = null
    private var inserts = 0
    private var nextId = 1L

    override suspend fun snapshot() = VaultSnapshot(
        notes = notes.map { it.copy() },
        tags = tags.map { it.copy() },
        tasks = tasks.toList(),
    )

    override suspend fun <R> inTransaction(block: suspend () -> R): R {
        val saved = Triple(notes.toList(), tags.toList(), tasks.toList())
        return try {
            block()
        } catch (e: RuntimeException) {
            restore(notes, saved.first)
            restore(tags, saved.second)
            restore(tasks, saved.third)
            throw e
        }
    }

    override suspend fun deleteAll() {
        notes.clear()
        tags.clear()
        tasks.clear()
    }

    override suspend fun insertTag(tag: Tag): Long {
        val id = nextInsert(tag.uid, tags.map { it.uid })
        tags += tag.copy(id = id, uid = tag.uid.ifBlank { "tag-$id" })
        return id
    }

    override suspend fun insertNote(note: Note): Long {
        val id = nextInsert(note.uid, notes.map { it.uid })
        notes += note.copy(id = id, uid = note.uid.ifBlank { "note-$id" })
        return id
    }

    override suspend fun insertTask(task: Task): Long {
        val id = nextInsert(task.uid, tasks.map { it.uid })
        tasks += task.copy(id = id, uid = task.uid.ifBlank { "task-$id" })
        return id
    }

    private fun <T> restore(target: MutableList<T>, saved: List<T>) {
        target.clear()
        target.addAll(saved)
    }

    private fun nextInsert(uid: String, existing: List<String>): Long {
        inserts++
        check(inserts != failOnInsert) { "simulated constraint failure" }
        check(uid.isBlank() || uid !in existing) { "UNIQUE constraint failed: uid" }
        return nextId++
    }
}
