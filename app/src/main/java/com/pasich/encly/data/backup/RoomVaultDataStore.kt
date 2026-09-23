package com.pasich.encly.data.backup

import androidx.room.withTransaction
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import javax.inject.Inject

/** [VaultDataStore] over the currently unlocked SQLCipher/Room vault. */
class RoomVaultDataStore @Inject constructor(private val databaseProvider: DatabaseProvider) : VaultDataStore {
    private fun database() = databaseProvider.getDatabase()
    private fun dao() = database().backupDao()

    override suspend fun snapshot(): VaultSnapshot = database().withTransaction {
        VaultSnapshot(notes = dao().allNotes(), tags = dao().allTags(), tasks = dao().allTasks())
    }

    override suspend fun <R> inTransaction(block: suspend () -> R): R = database().withTransaction { block() }

    override suspend fun deleteAll() {
        dao().deleteAllNotes()
        dao().deleteAllTasks()
        dao().deleteAllTags()
    }

    override suspend fun insertTag(tag: Tag): Long = dao().insertTag(tag)

    override suspend fun insertNote(note: Note): Long = dao().insertNote(note)

    override suspend fun insertTask(task: Task): Long = dao().insertTask(task)
}
