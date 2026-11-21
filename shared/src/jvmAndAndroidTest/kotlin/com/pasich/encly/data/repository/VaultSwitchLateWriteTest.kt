package com.pasich.encly.data.repository

import com.pasich.encly.data.backup.RoomVaultDataStore
import com.pasich.encly.data.database.AppDatabase
import com.pasich.encly.data.database.DatabaseLease
import com.pasich.encly.data.database.DatabasePlatform
import com.pasich.encly.data.database.SecureDatabaseManager
import com.pasich.encly.data.database.VaultFile
import com.pasich.encly.data.database.dao.BackupDao
import com.pasich.encly.data.database.dao.NotesDao
import com.pasich.encly.data.database.dao.TagsDao
import com.pasich.encly.data.database.dao.TasksDao
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.usecase.note.PurgeExpiredTrashUseCase
import com.pasich.encly.testutil.InMemoryKeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

/**
 * Work that was started in one vault and completes after the app switched to the other one (or
 * re-locked and reopened the same one): an editor save, a backup, a trash purge (issue #131). The
 * guarantee is the lease of [SecureDatabaseManager]: a repository or a data store keeps the opening it
 * was created in, and every operation resolves its DAO through that lease, so a late write fails
 * before it reaches any DAO of the vault that is open now. The real manager is used, with mock
 * databases put in as its open one (as in its own host test, without the native cipher).
 */
class VaultSwitchLateWriteTest {
    private class Vault(val file: VaultFile) {
        val notes: NotesDao = mock(NotesDao::class.java)
        val tags: TagsDao = mock(TagsDao::class.java)
        val tasks: TasksDao = mock(TasksDao::class.java)
        val backup: BackupDao = mock(BackupDao::class.java)
        val database: AppDatabase = mock(AppDatabase::class.java).also {
            `when`(it.notesDao()).thenReturn(notes)
            `when`(it.tagsDao()).thenReturn(tags)
            `when`(it.tasksDao()).thenReturn(tasks)
            `when`(it.backupDao()).thenReturn(backup)
        }
        val key = ByteArray(32) { (if (file == VaultFile.PRIMARY) 1 else 2).toByte() }

        fun assertUntouched() {
            verifyNoInteractions(notes, tags, tasks, backup)
        }
    }

    private val real = Vault(VaultFile.PRIMARY)
    private val decoy = Vault(VaultFile.DECOY)
    private val manager = SecureDatabaseManager(mock(DatabasePlatform::class.java))
    private val note = Note(id = 5, title = "t", value = "v", date = 1L)
    private val base = note.copy(value = "before")

    private fun setField(name: String, value: Any?) =
        SecureDatabaseManager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(manager, value)

    /** The state of a manager that just opened [vault]'s database (a new lease, as every opening gets). */
    private fun open(vault: Vault) {
        setField("database", vault.database)
        setField("openKey", vault.key.copyOf())
        setField("openFile", vault.file)
        setField("lease", DatabaseLease())
        setField("isUnlocked", true)
    }

    /** What a lock, or the switch to the other PIN, does first: closes whatever is open. */
    private fun lock() = manager.reset()

    @Test
    fun aRepositoryWritesIntoTheVaultItWasCreatedIn() = runBlocking {
        open(decoy)
        `when`(decoy.notes.insertNoteWithTags(note)).thenReturn(7L)

        assertEquals(7L, NotesRepositoryImpl(manager).insertNote(note).getOrNull())
        real.assertUntouched()
    }

    @Test
    fun anEditorSaveStartedInTheRealVaultAndCompletingInTheDecoyOneIsDropped() = runBlocking {
        open(real)
        val repository = NotesRepositoryImpl(manager)
        val gate = CompletableDeferred<Unit>()
        val save = async {
            gate.await() // the editor was still about to write when the PIN of the other vault was typed
            repository.updateEdited(note, base)
        }

        lock()
        open(decoy)
        gate.complete(Unit)

        assertTrue(save.await().isFailure)
        decoy.assertUntouched()
        real.assertUntouched()
    }

    @Test
    fun anEditorSaveStartedInTheDecoyVaultAndCompletingInTheRealOneIsDropped() = runBlocking {
        open(decoy)
        val repository = NotesRepositoryImpl(manager)
        val gate = CompletableDeferred<Unit>()
        val save = async {
            gate.await()
            repository.insertNote(note)
        }

        lock()
        open(real)
        gate.complete(Unit)

        assertTrue(save.await().isFailure)
        real.assertUntouched()
    }

    @Test
    fun aWriteAfterALockThatReopenedTheSameVaultIsDroppedToo() = runBlocking {
        open(real)
        val repository = NotesRepositoryImpl(manager)
        val lease = manager.captureLease()

        lock()
        open(real)

        assertNotSame("a new opening, a new lease", lease, manager.captureLease())
        assertTrue(repository.setTrashed(note.id, true, 1_000L).isFailure)
        assertTrue(repository.updateEdited(note, base).isFailure)
        real.assertUntouched()
    }

    @Test
    fun aWriteAfterALockWithNothingReopenedIsDropped() = runBlocking {
        open(real)
        val repository = NotesRepositoryImpl(manager)

        lock()

        assertTrue(repository.insertNote(note).isFailure)
        real.assertUntouched()
    }

    @Test
    fun aTrashPurgeStartedInTheRealVaultAndCompletingInTheDecoyOneDeletesNothingAndRecordsNothing() = runBlocking {
        open(real)
        val flags = InMemoryKeyValueStore()
        val purge = PurgeExpiredTrashUseCase(NotesRepositoryImpl(manager), flags)
        val gate = CompletableDeferred<Unit>()
        val purging = async {
            gate.await()
            purge(now = NOW)
        }

        lock()
        open(decoy)
        gate.complete(Unit)

        assertTrue(purging.await().isFailure)
        decoy.assertUntouched()
        real.assertUntouched()
        assertEquals("the purge did not count as done", 0L, flags.getLong("trash_last_purge_at", 0L))
    }

    @Test
    fun taskAndTagWritesOfAnEarlierOpeningAreDroppedToo() = runBlocking {
        open(real)
        val tasks = TasksRepositoryImpl(manager)
        val tags = TagsRepositoryImpl(manager)

        lock()
        open(decoy)

        assertTrue(tasks.insertTask(Task(id = 1, title = "t")).isFailure)
        assertTrue(tasks.deleteTaskById(1).isFailure)
        assertTrue(tags.addTag(Tag(id = 1, nameTag = "x")).isFailure)
        assertTrue(tags.deleteTag(Tag(id = 1, nameTag = "x")).isFailure)
        decoy.assertUntouched()
        real.assertUntouched()
    }

    @Test
    fun aBackupBoundToTheRealVaultCanNotReadOrWriteTheDecoyOneAfterTheSwitch() = runBlocking {
        open(real)
        val bound = RoomVaultDataStore(manager).bindToCurrentSession()
        bound.assertCurrentSession()

        lock()
        open(decoy)

        assertTrue(runCatching { bound.assertCurrentSession() }.isFailure)
        assertTrue(runCatching { bound.snapshot() }.isFailure)
        assertTrue(runCatching { bound.deleteAll() }.isFailure)
        assertTrue(runCatching { bound.insertNote(note) }.isFailure)
        assertTrue(runCatching { bound.insertTag(Tag(id = 1, nameTag = "x")) }.isFailure)
        decoy.assertUntouched()
        real.assertUntouched()
    }

    @Test
    fun aBackupStartedInTheRealVaultFailsWhenItsLateStepComesAfterTheSwitch() = runBlocking {
        open(real)
        val bound = RoomVaultDataStore(manager).bindToCurrentSession()
        val gate = CompletableDeferred<Unit>()
        val late = async {
            gate.await()
            runCatching { bound.insertNote(note) }
        }

        lock()
        open(decoy)
        gate.complete(Unit)

        assertTrue(late.await().isFailure)
        decoy.assertUntouched()
    }

    @Test
    fun theOtherVaultCanNotBeOpenedWhileOneIsOpenSoNoWriteCanLandInTheWrongFile() {
        open(real)
        val lease = manager.captureLease()

        assertFalse(manager.unlockDatabase(decoy.key, allowCreate = true, file = VaultFile.DECOY))
        assertFalse(manager.unlockDatabase(real.key, allowCreate = false, file = VaultFile.DECOY))

        assertTrue("the open one is as it was", manager.isDatabaseUnlocked())
        assertEquals(lease, manager.captureLease())
        decoy.assertUntouched()
    }

    private companion object {
        const val NOW = 40L * 24 * 60 * 60 * 1000
    }
}
