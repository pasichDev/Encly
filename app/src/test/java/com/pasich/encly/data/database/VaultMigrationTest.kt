package com.pasich.encly.data.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    /** The version-4 database as Room created it, with one note written the way 2.2.1 wrote it. */
    private fun createVersion4() {
        val schema = JsonParser.parseString(File(SCHEMA_4).readText()).asJsonObject.getAsJsonObject("database")
        val statements = buildList {
            schema.getAsJsonArray("entities").forEach { entity ->
                val table = entity.asJsonObject.get("tableName").asString
                add(entity.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table))
                entity.asJsonObject.getAsJsonArray("indices").forEach { index ->
                    add(index.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getAsJsonArray("setupQueries").forEach { add(it.asString) }
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(OLD_VERSION) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            statements.forEach(db::execSQL)
                            VaultSchema.createTriggers(db)
                            db.execSQL(
                                "INSERT INTO notes " +
                                    "(id, title, value, description, date, dateCreate, tagId, isTrash, uid) " +
                                    "VALUES (5, 'Old note', '[]', 'd', 20, 10, NULL, 1, 'old-uid')",
                            )
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
    fun aVersion4VaultOpensAtTheCurrentVersionAndKeepsItsNotes() = runBlocking {
        createVersion4()

        val db = VaultSchema.install(Room.databaseBuilder(context, AppDatabase::class.java, name)).build()
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
            assertEquals(DB_VERSION, db.openHelper.readableDatabase.version)

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

    private companion object {
        const val OLD_VERSION = 4
        const val SCHEMA_4 = "schemas/com.pasich.encly.data.database.AppDatabase/4.json"
    }
}
