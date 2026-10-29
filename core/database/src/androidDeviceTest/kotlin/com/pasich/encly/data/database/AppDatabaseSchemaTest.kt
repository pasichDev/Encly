package com.pasich.encly.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Validates the committed Room schema export (app/schemas) against the compiled entities.
 *
 * Every future DB_VERSION bump must add its migration and a `runMigrationsAndValidate` case
 * from the previous exported version here. The vault builder (VaultSchema.databaseBuilder) has
 * no destructive fallback, so a missing migration crashes instead of silently dropping the
 * user's encrypted notes.
 * The helper runs the migrations through the androidx.sqlite driver API (SQLiteConnection), the
 * same API the vault uses, but on a plain (non-SQLCipher) test database: it checks the schema,
 * not encryption.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseSchemaTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath(TEST_DB),
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    // The driver-based helper creates the file itself and refuses one that already exists.
    @Before
    fun deleteTestDatabase() {
        val path = instrumentation.targetContext.getDatabasePath(TEST_DB).path
        listOf("", "-wal", "-shm", "-journal").forEach { File(path + it).delete() }
    }

    @Test
    fun currentSchemaMatchesExport() {
        helper.createDatabase(DB_VERSION).close()
        helper.runMigrationsAndValidate(DB_VERSION, emptyList()).close()
    }

    @Test
    fun migrate2To3DropsReminderAndKeepsTasks() {
        helper.createDatabase(2).use { db ->
            db.execSQL(
                "INSERT INTO tasks (id, title, description, isCompleted, createdDate, completedDate, " +
                    "reminderDate, priority, categoryId, position, uid) " +
                    "VALUES (7, 'Buy milk', '2 l', 0, 100, NULL, 200, 2, NULL, 3, 'task-uid')",
            )
        }

        helper.runMigrationsAndValidate(3, listOf(VaultSchema.MIGRATION_2_3)).use { db ->
            db.prepare("SELECT id, title, description, priority, position, uid FROM tasks").use { s ->
                assertTrue(s.step())
                assertEquals(7L, s.getLong(0))
                assertEquals("Buy milk", s.getText(1))
                assertEquals("2 l", s.getText(2))
                assertEquals(2, s.getLong(3).toInt())
                assertEquals(3, s.getLong(4).toInt())
                assertEquals("task-uid", s.getText(5))
            }
            db.prepare("PRAGMA table_info(tasks)").use { s ->
                // table_info columns: cid, name, type, notnull, dflt_value, pk
                while (s.step()) assertNotEquals("reminderDate", s.getText(1))
            }

            // The uid triggers are rebuilt with the table: a blank uid is still filled in.
            db.execSQL(
                "INSERT INTO tasks (title, isCompleted, createdDate, priority, position) VALUES ('x', 0, 1, 0, 0)",
            )
            db.prepare("SELECT uid FROM tasks WHERE title = 'x'").use { s ->
                assertTrue(s.step())
                assertEquals(32, s.getText(0).length)
            }
        }
    }

    @Test
    fun migrate3To4AddsSubtasksWithTriggersAndCascade() {
        helper.createDatabase(3).use { db ->
            db.execSQL(
                "INSERT INTO tasks (id, title, isCompleted, createdDate, priority, position, uid) " +
                    "VALUES (7, 'Buy milk', 0, 100, 2, 3, 'task-uid')",
            )
        }

        helper.runMigrationsAndValidate(4, listOf(VaultSchema.MIGRATION_3_4)).use { db ->
            db.prepare("SELECT id, title, uid FROM tasks").use { s ->
                assertTrue(s.step())
                assertEquals(7L, s.getLong(0))
                assertEquals("Buy milk", s.getText(1))
                assertEquals("task-uid", s.getText(2))
            }
            db.prepare("SELECT COUNT(*) FROM subtasks").use { s ->
                assertTrue(s.step())
                assertEquals(0, s.getLong(0).toInt())
            }

            // A blank uid is filled in, and a later blank update keeps it.
            db.execSQL("INSERT INTO subtasks (taskId, title, isCompleted, position) VALUES (7, 'Oat', 0, 0)")
            val uid = db.prepare("SELECT uid FROM subtasks").use { s ->
                assertTrue(s.step())
                s.getText(0)
            }
            assertEquals(32, uid.length)
            db.execSQL("UPDATE subtasks SET uid = '' WHERE taskId = 7")
            db.prepare("SELECT uid FROM subtasks").use { s ->
                assertTrue(s.step())
                assertEquals(uid, s.getText(0))
            }

            // Deleting the task deletes its sub-tasks (Room turns foreign keys on when it opens the vault).
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM tasks WHERE id = 7")
            db.prepare("SELECT COUNT(*) FROM subtasks").use { s ->
                assertTrue(s.step())
                assertEquals(0, s.getLong(0).toInt())
            }
        }
    }

    @Test
    fun migrate4To5KeepsNotesUnpinnedUnarchivedUncoloured() {
        helper.createDatabase(4).use { db ->
            // A real version-4 vault has the uid triggers (the creation callback or 3 -> 4 adds
            // them); the helper builds the tables from the export only.
            VaultSchema.createTriggers(db)
            db.execSQL(
                "INSERT INTO notes (id, title, value, description, date, dateCreate, tagId, isTrash, uid) " +
                    "VALUES (3, 'Groceries', '[]', 'milk', 200, 100, NULL, 1, 'note-uid')",
            )
        }

        helper.runMigrationsAndValidate(5, listOf(VaultSchema.MIGRATION_4_5)).use { db ->
            db.prepare(
                "SELECT title, isTrash, uid, isPinned, isArchived, color FROM notes WHERE id = 3",
            ).use { s ->
                assertTrue(s.step())
                assertEquals("Groceries", s.getText(0))
                assertEquals(1, s.getLong(1).toInt())
                assertEquals("note-uid", s.getText(2))
                assertEquals(0, s.getLong(3).toInt())
                assertEquals(0, s.getLong(4).toInt())
                assertEquals("none", s.getText(5))
            }

            // A note inserted by older-style code (no new columns) still gets the defaults.
            db.execSQL(
                "INSERT INTO notes (title, value, description, date, dateCreate, isTrash) " +
                    "VALUES ('n', '[]', '', 1, 1, 0)",
            )
            db.prepare("SELECT isPinned, isArchived, color, uid FROM notes WHERE title = 'n'").use { s ->
                assertTrue(s.step())
                assertEquals(0, s.getLong(0).toInt())
                assertEquals(0, s.getLong(1).toInt())
                assertEquals("none", s.getText(2))
                assertEquals(32, s.getText(3).length)
            }
        }
    }

    @Test
    fun migrate5To6AddsNullableTextSettings() {
        helper.createDatabase(5).use { db ->
            db.execSQL(
                "INSERT INTO notes (id, title, value, description, date, dateCreate, tagId, isTrash, uid, " +
                    "isPinned, isArchived, color) VALUES (3, 'Groceries', '[]', 'milk', 200, 100, NULL, 0, " +
                    "'note-uid', 1, 0, 'red')",
            )
        }

        helper.runMigrationsAndValidate(6, listOf(VaultSchema.MIGRATION_5_6)).use { db ->
            db.prepare("SELECT title, isPinned, color, textSize, lineSpacing FROM notes WHERE id = 3").use { s ->
                assertTrue(s.step())
                assertEquals("Groceries", s.getText(0))
                assertEquals(1, s.getLong(1).toInt())
                assertEquals("red", s.getText(2))
                assertTrue(s.isNull(3))
                assertTrue(s.isNull(4))
            }
        }
    }

    @Test
    fun migrate1ToCurrentRunsTheWholeChain() {
        helper.createDatabase(1).close()

        helper.runMigrationsAndValidate(
            DB_VERSION,
            listOf(
                VaultSchema.MIGRATION_1_2,
                VaultSchema.MIGRATION_2_3,
                VaultSchema.MIGRATION_3_4,
                VaultSchema.MIGRATION_4_5,
                VaultSchema.MIGRATION_5_6,
            ),
        ).close()
    }

    private companion object {
        const val TEST_DB = "schema-test.db"
    }
}
