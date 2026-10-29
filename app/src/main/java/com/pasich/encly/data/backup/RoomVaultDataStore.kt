package com.pasich.encly.data.backup

import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import com.pasich.encly.data.database.DatabaseLease
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import javax.inject.Inject

/** [VaultDataStore] over a single SQLCipher/Room opening. */
@Suppress("TooManyFunctions") // One function per vault table operation of the contract.
class RoomVaultDataStore private constructor(
    private val databaseProvider: DatabaseProvider,
    private val lease: DatabaseLease?,
) : VaultDataStore {
    @Inject constructor(databaseProvider: DatabaseProvider) : this(databaseProvider, null)

    override fun bindToCurrentSession(): VaultDataStore =
        RoomVaultDataStore(databaseProvider, databaseProvider.captureLease() ?: error("Vault locked"))

    override fun assertCurrentSession() {
        database()
    }

    private fun database() = databaseProvider.getDatabase(lease ?: error("Vault data store is not bound"))

    // One read transaction: every table is read from the same state of the database.
    override suspend fun snapshot(): VaultSnapshot = database().let { db ->
        db.useReaderConnection {
            it.deferredTransaction {
                VaultSnapshot(
                    notes = db.backupDao().allNotes(),
                    tags = db.backupDao().allTags(),
                    tasks = db.backupDao().allTasks(),
                    subtasks = db.backupDao().allSubtasks(),
                )
            }
        }
    }

    override suspend fun snapshotOfNote(noteId: Long): VaultSnapshot = database().let { db ->
        db.useReaderConnection {
            it.deferredTransaction {
                val note = db.notesDao().getNoteById(noteId)
                val tag = note?.tagId?.let { db.backupDao().tagById(it) }
                VaultSnapshot(listOfNotNull(note), listOfNotNull(tag), emptyList())
            }
        }
    }

    override suspend fun <R> inTransaction(block: suspend () -> R): R =
        database().useWriterConnection { it.immediateTransaction { block() } }

    override suspend fun deleteAll() {
        val dao = database().backupDao()
        dao.deleteAllNotes()
        // The foreign key would cascade them with their tasks; deleted explicitly all the same.
        dao.deleteAllSubtasks()
        dao.deleteAllTasks()
        dao.deleteAllTags()
    }

    override suspend fun insertTag(tag: Tag): Long = database().backupDao().insertTag(tag)

    override suspend fun insertNote(note: Note): Long = database().backupDao().insertNote(note)

    override suspend fun insertTask(task: Task): Long = database().backupDao().insertTask(task)

    override suspend fun insertSubtask(subtask: Subtask): Long = database().backupDao().insertSubtask(subtask)
}
