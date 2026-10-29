package com.pasich.encly.data.database

import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.github.pasichdev.sealite.SealiteDriver
import io.github.pasichdev.sealite.SealiteKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Upgrades real older vault files, encrypted, through [SecureDatabaseManager] on the desktop: the
 * exported schema of an earlier version is written into a SQLCipher file through Sealite, the way
 * that version created it, and the manager must migrate it to the current version without losing a note.
 */
class DesktopMigrationTest {
    private lateinit var directory: File
    private lateinit var manager: SecureDatabaseManager

    @BeforeTest
    fun setUp() {
        directory = Files.createTempDirectory("encly-migration-test").toFile()
        manager = SecureDatabaseManager(DesktopDatabasePlatform(Path(directory.path)))
    }

    @AfterTest
    fun tearDown() {
        manager.reset()
        directory.deleteRecursively()
    }

    private fun writeEncryptedVersion(version: Int, vararg rowsSql: String) {
        val schema = Json.parseToJsonElement(File("schemas/$SCHEMA_DIR/$version.json").readText())
            .jsonObject.getValue("database").jsonObject
        val statements = buildList {
            schema.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                add(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                // Version 1 predates the indices.
                entity.jsonObject["indices"]?.jsonArray.orEmpty().forEach { index ->
                    add(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { add(it.jsonPrimitive.content) }
        }
        val key = SealiteKey.raw(KEY)
        val driver = SealiteDriver(key)
        key.close()
        driver.use {
            val connection: SQLiteConnection = it.open(File(directory, VaultFile.PRIMARY.fileName).path)
            connection.use { db ->
                statements.forEach(db::execSQL)
                // The uid columns, and with them the triggers, came in version 2; the subtasks in 4.
                if (version >= 2) {
                    VaultSchema.triggerStatements()
                        .filter { version >= 4 || "subtasks" !in it }
                        .forEach(db::execSQL)
                }
                rowsSql.forEach(db::execSQL)
                db.execSQL("PRAGMA user_version = $version")
            }
        }
    }

    private suspend fun userVersion(db: AppDatabase): Int = db.useReaderConnection { connection ->
        connection.usePrepared("PRAGMA user_version") {
            it.step()
            it.getLong(0).toInt()
        }
    }

    @Test
    fun anEncryptedVersion4VaultMigratesToTheCurrentVersionAndKeepsItsNotes() = runBlocking {
        writeEncryptedVersion(
            4,
            "INSERT INTO notes (id, title, value, description, date, dateCreate, tagId, isTrash, uid) " +
                "VALUES (5, 'Old note', '[]', 'd', 20, 10, NULL, 1, 'old-uid')",
        )
        assertTrue(manager.hasEncryptedDatabase())

        assertFalse(manager.unlockDatabase(ByteArray(32) { 9 }), "another key must not open it")
        assertTrue(manager.unlockDatabase(KEY))
        val db = manager.getDatabase(checkNotNull(manager.captureLease()))

        val old = checkNotNull(db.notesDao().getNoteById(5))
        assertEquals("Old note", old.title)
        assertEquals("old-uid", old.uid)
        assertEquals(true, old.isTrash)
        assertEquals("none", old.color)
        assertNull(old.textSize)
        assertEquals(DB_VERSION, userVersion(db))
        assertEquals(listOf("Old note"), db.notesDao().getTrashNotes().first().map { it.title })
    }

    @Test
    fun anEncryptedVersion1VaultGetsUidsForEveryRowAndLosesTheTaskReminders() = runBlocking {
        writeEncryptedVersion(
            1,
            "INSERT INTO tags (id, name, visibility, position) VALUES (1, 'Work', 1, 0)",
            "INSERT INTO notes (id, title, value, description, date, dateCreate, tagId, isTrash) " +
                "VALUES (1, 'n1', '[]', 'd', 1, 1, 1, 0)",
            "INSERT INTO tasks (id, title, description, isCompleted, createdDate, completedDate, " +
                "reminderDate, priority, categoryId, position) VALUES (1, 't1', NULL, 0, 5, NULL, 99, 1, NULL, 0)",
        )

        assertTrue(manager.unlockDatabase(KEY))
        val db = manager.getDatabase(checkNotNull(manager.captureLease()))

        assertEquals("n1", checkNotNull(db.notesDao().getNoteById(1)).title)
        assertEquals(UID_LENGTH, checkNotNull(db.notesDao().getNoteById(1)).uid.length)
        assertEquals(UID_LENGTH, db.tagsDao().getTags().first().single().uid.length)
        val task = checkNotNull(db.tasksDao().getTaskById(1))
        assertEquals("t1", task.title)
        assertEquals(UID_LENGTH, task.uid.length)
        assertEquals(DB_VERSION, userVersion(db))

        // The triggers of the migrated vault fill a blank uid, and the sub-tasks table is there.
        val fresh = db.notesDao().insertNote(com.pasich.encly.data.model.Note(title = "new"))
        assertEquals(UID_LENGTH, checkNotNull(db.notesDao().getNoteById(fresh)).uid.length)
        db.tasksDao().appendSubtask(1, "step")
        assertEquals(1, db.tasksDao().getSubtasks(1).size)
    }

    @Test
    fun anEncryptedVersion2VaultMigratesAndKeepsItsUids() = runBlocking {
        writeEncryptedVersion(
            2,
            "INSERT INTO notes (id, title, value, description, date, dateCreate, tagId, isTrash, uid) " +
                "VALUES (1, 'n1', '[]', 'd', 1, 1, NULL, 0, 'note-uid')",
            "INSERT INTO tasks (id, title, description, isCompleted, createdDate, completedDate, " +
                "reminderDate, priority, categoryId, position, uid) " +
                "VALUES (1, 't1', NULL, 0, 5, NULL, 99, 1, NULL, 0, 'task-uid')",
        )

        assertTrue(manager.unlockDatabase(KEY))
        val db = manager.getDatabase(checkNotNull(manager.captureLease()))

        assertEquals("note-uid", checkNotNull(db.notesDao().getNoteById(1)).uid)
        assertEquals("task-uid", checkNotNull(db.tasksDao().getTaskById(1)).uid)
        assertEquals(DB_VERSION, userVersion(db))
    }

    @Test
    fun anEncryptedVersion3VaultGainsTheSubtasksTable() = runBlocking {
        writeEncryptedVersion(
            3,
            "INSERT INTO tasks (id, title, description, isCompleted, createdDate, completedDate, " +
                "priority, categoryId, position, uid) VALUES (1, 't1', NULL, 0, 5, NULL, 1, NULL, 0, 'task-uid')",
        )

        assertTrue(manager.unlockDatabase(KEY))
        val db = manager.getDatabase(checkNotNull(manager.captureLease()))

        assertEquals("task-uid", checkNotNull(db.tasksDao().getTaskById(1)).uid)
        db.tasksDao().appendSubtask(1, "step")
        assertEquals(listOf("step"), db.tasksDao().getSubtasks(1).map { it.title })
        assertEquals(DB_VERSION, userVersion(db))
    }

    @Test
    fun anEncryptedVersion5VaultGetsTheNoteTextSettings() = runBlocking {
        writeEncryptedVersion(
            5,
            "INSERT INTO notes (id, title, value, description, date, dateCreate, tagId, isTrash, uid, " +
                "isPinned, isArchived, color) " +
                "VALUES (5, 'Old note', '[]', 'd', 20, 10, NULL, 0, 'old-uid', 1, 0, 'red')",
        )

        assertTrue(manager.unlockDatabase(KEY))
        val db = manager.getDatabase(checkNotNull(manager.captureLease()))

        val old = checkNotNull(db.notesDao().getNoteById(5))
        assertEquals(true, old.isPinned)
        assertEquals("red", old.color)
        assertNull(old.textSize)
        assertNull(old.lineSpacing)
        assertEquals(DB_VERSION, userVersion(db))
    }

    private companion object {
        const val UID_LENGTH = 32
        const val SCHEMA_DIR = "com.pasich.encly.data.database.AppDatabase"
        val KEY = ByteArray(32) { (it + 1).toByte() }
    }
}
