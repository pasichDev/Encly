package com.pasich.encly.data.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Upgrades a real version-4 vault file (the exported schema 4.json, with its triggers) through
 * [VaultSchema], the way an existing install does, and lets Room validate the result against the
 * current entities. Plain SQLite under Robolectric, not SQLCipher: it checks the schema, not
 * encryption. The instrumented `AppDatabaseSchemaTest` covers the same on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VaultMigrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    /**
     * The database of [version] as Room created it from its exported [schemaPath], with one note
     * written the way that version wrote it ([noteSql] inserts it).
     */
    private fun createVersion(version: Int, schemaPath: String, noteSql: String) {
        val schema = Json.parseToJsonElement(File(schemaPath).readText()).jsonObject.getValue("database").jsonObject
        val statements = buildList {
            schema.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                add(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity.jsonObject.getValue("indices").jsonArray.forEach { index ->
                    add(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { add(it.jsonPrimitive.content) }
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            statements.forEach(db::execSQL)
                            VaultSchema.triggerStatements().forEach(db::execSQL)
                            db.execSQL(noteSql)
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        helper.writableDatabase.close()
        helper.close()
    }

    @Test
    fun aVersion5VaultAsItWasPushedMigratesToTheCurrentVersion() = runBlocking {
        createVersion(
            5,
            SCHEMA_5,
            "INSERT INTO notes (id, title, value, description, date, dateCreate, tagId, isTrash, uid, " +
                "isPinned, isArchived, color) " +
                "VALUES (5, 'Old note', '[]', 'd', 20, 10, NULL, 1, 'old-uid', 1, 0, 'red')",
        )

        val db = VaultSchema.install(Room.databaseBuilder(context, AppDatabase::class.java, name))
            .setDriver(AndroidSQLiteDriver())
            .build()
        try {
            val old = checkNotNull(db.notesDao().getNoteById(5))
            assertEquals("Old note", old.title)
            assertEquals(true, old.isPinned)
            assertEquals("red", old.color)
            assertNull(old.textSize)
            assertNull(old.lineSpacing)
            assertEquals(DB_VERSION, db.userVersion())
        } finally {
            db.close()
        }
    }

    @Test
    fun aVersion4VaultOpensAtTheCurrentVersionAndKeepsItsNotes() = runBlocking {
        createVersion(
            4,
            SCHEMA_4,
            "INSERT INTO notes (id, title, value, description, date, dateCreate, tagId, isTrash, uid) " +
                "VALUES (5, 'Old note', '[]', 'd', 20, 10, NULL, 1, 'old-uid')",
        )

        val db = VaultSchema.install(Room.databaseBuilder(context, AppDatabase::class.java, name))
            .setDriver(AndroidSQLiteDriver())
            .build()
        try {
            // Room validates the migrated tables against the entities when the first query opens it.
            val old = checkNotNull(db.notesDao().getNoteById(5))
            assertEquals("Old note", old.title)
            assertEquals(true, old.isTrash)
            assertEquals("old-uid", old.uid)
            assertEquals(false, old.isPinned)
            assertEquals(false, old.isArchived)
            assertEquals("none", old.color)
            assertNull(old.textSize)
            assertNull(old.lineSpacing)
            assertEquals(DB_VERSION, db.userVersion())

            // The uid trigger survived the upgrade, and the new columns default for a new note.
            val id = db.notesDao().insertNote(com.pasich.encly.data.model.Note(title = "New"))
            val fresh = checkNotNull(db.notesDao().getNoteById(id))
            assertEquals(32, fresh.uid.length)
            assertEquals("none", fresh.color)
            // The migrated old note is in the trash, so only the new one is in the list.
            assertEquals(listOf("New"), db.notesDao().getAllNotes().first().map { it.title })
            assertEquals(listOf("Old note"), db.notesDao().getTrashNotes().first().map { it.title })
        } finally {
            db.close()
        }
    }

    private suspend fun AppDatabase.userVersion(): Int = useReaderConnection { connection ->
        connection.usePrepared("PRAGMA user_version") {
            it.step()
            it.getLong(0).toInt()
        }
    }

    private companion object {
        const val SCHEMA_5 = "schemas/com.pasich.encly.data.database.AppDatabase/5.json"
        const val SCHEMA_4 = "schemas/com.pasich.encly.data.database.AppDatabase/4.json"
    }
}
