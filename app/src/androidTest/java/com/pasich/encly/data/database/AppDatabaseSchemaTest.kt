package com.pasich.encly.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the committed Room schema export (app/schemas) against the compiled entities.
 *
 * Every future DB_VERSION bump must add its migration and a `runMigrationsAndValidate` case
 * from the previous exported version here. The vault builder (VaultSchema.databaseBuilder) has
 * no destructive fallback, so a missing migration crashes instead of silently dropping the
 * user's encrypted notes.
 * The helper uses a plain (non-SQLCipher) test database: it checks the schema, not encryption.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseSchemaTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun currentSchemaMatchesExport() {
        helper.createDatabase(TEST_DB, DB_VERSION).close()
        helper.runMigrationsAndValidate(TEST_DB, DB_VERSION, true).close()
    }

    @Test
    fun migrate2To3DropsReminderAndKeepsTasks() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO tasks (id, title, description, isCompleted, createdDate, completedDate, " +
                    "reminderDate, priority, categoryId, position, uid) " +
                    "VALUES (7, 'Buy milk', '2 l', 0, 100, NULL, 200, 2, NULL, 3, 'task-uid')",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, VaultSchema.MIGRATION_2_3).use { db ->
            db.query("SELECT id, title, description, priority, position, uid FROM tasks").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(7L, c.getLong(0))
                assertEquals("Buy milk", c.getString(1))
                assertEquals("2 l", c.getString(2))
                assertEquals(2, c.getInt(3))
                assertEquals(3, c.getInt(4))
                assertEquals("task-uid", c.getString(5))
            }
            db.query("PRAGMA table_info(tasks)").use { c ->
                val nameIndex = c.getColumnIndexOrThrow("name")
                while (c.moveToNext()) assertNotEquals("reminderDate", c.getString(nameIndex))
            }

            // The uid triggers are rebuilt with the table: a blank uid is still filled in.
            db.execSQL(
                "INSERT INTO tasks (title, isCompleted, createdDate, priority, position) VALUES ('x', 0, 1, 0, 0)",
            )
            db.query("SELECT uid FROM tasks WHERE title = 'x'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(32, c.getString(0).length)
            }
        }
    }

    private companion object {
        const val TEST_DB = "schema-test.db"
    }
}
