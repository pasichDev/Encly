package com.pasich.encly.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pasich.encly.data.model.NoteWithTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An ENCRYPTED vault of 2.3.x (database schema 6) goes through the real unlock
 * ([SecureDatabaseManager.unlockDatabase]) and the 6 -> 7 -> 8 migrations, and its data comes out intact.
 * [AppDatabaseSchemaTest] migrates the same versions, but on a plain database; this is the same path under
 * SQLCipher, which every upgraded user takes on the first unlock.
 *
 * How the 2.3.x vault is made: with sqlcipher-android 4.19.1 (the version 2.3.1 shipped, see the catalog),
 * through [SupportOpenHelperFactory] and the DEK as the raw key `x'<64 hex>'` as ASCII, which is what 2.3.x
 * handed to it. The tables, indices and `room_master_table` row are Room's own export of schema 6
 * (core/database/schemas/.../6.json, committed with 2.3.x); the uid triggers are the ones 2.3.x installed
 * on creation. The file's `user_version` is 6. It is not a binary taken out of a 2.3.1 build: only the 6.json
 * export and the write path are reproduced, which is what the migration and the unlock depend on.
 * The key is a test key, never a real user's.
 *
 * Runs inside the app under test and deletes its database.db: run it on an emulator.
 */
@RunWith(AndroidJUnit4::class)
class Release23MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var manager: SecureDatabaseManager
    private val dek = ByteArray(32) { (it * 7 + 3).toByte() }

    @Before
    fun setUp() {
        manager = SecureDatabaseManager(AndroidDatabasePlatform(context))
        manager.wipe()
    }

    @After
    fun tearDown() {
        manager.wipe()
    }

    @Test
    fun anEncryptedSchema6VaultMigratesThroughUnlockAndKeepsItsData() = runBlocking {
        writeSchema6Vault()
        assertTrue(manager.hasEncryptedDatabase())

        val before = System.currentTimeMillis()
        assertTrue("unlock must migrate 6 -> 8 under SQLCipher", manager.unlockDatabase(dek))
        assertData(manager.getDatabase(manager.captureLease()!!), before)

        // Every unlock after the first opens an already migrated vault.
        manager.reset()
        assertTrue(manager.unlockDatabase(dek))
        val reopened = manager.getDatabase(manager.captureLease()!!)
        assertData(reopened, before)

        // And it is a working schema 8 vault: a note takes several tags now.
        val notes = reopened.notesDao()
        notes.insertNoteTags(NOTE_PLAIN, listOf(TAG_WORK, TAG_HOME))
        assertEquals(listOf(TAG_WORK, TAG_HOME), tagIds(notes.getNoteWithTags(NOTE_PLAIN)))
    }

    private suspend fun assertData(db: AppDatabase, notBefore: Long) {
        val notes = db.notesDao()

        // Notes: the four live ones (the fifth is in the trash), text intact. The tag of each moved to
        // note_tags; the dangling one was dropped.
        assertEquals(
            listOf("Work note", "Home note", "Plain note", "Dangling tag note"),
            notes.getAllNotes().first().sortedBy { it.id }.map { it.title },
        )
        assertEquals("body of work", notes.getNoteById(NOTE_WORK)!!.value)
        assertEquals(listOf(TAG_WORK), tagIds(notes.getNoteWithTags(NOTE_WORK)))
        assertEquals(listOf(TAG_HOME), tagIds(notes.getNoteWithTags(NOTE_HOME)))
        assertEquals(emptyList<Long>(), tagIds(notes.getNoteWithTags(NOTE_DANGLING)))

        // The trash: the note stays in it with its tag and got the migration's trash time; the others have none.
        assertEquals(listOf(NOTE_TRASHED), notes.getTrashNotes().first().map { it.id })
        assertEquals(listOf(TAG_WORK), tagIds(notes.getNoteWithTags(NOTE_TRASHED)))
        val trashedAt = notes.getNoteById(NOTE_TRASHED)!!.trashedAt
        assertNotNull(trashedAt)
        assertTrue(
            "trashedAt is the time of the migration",
            trashedAt!! in (notBefore - ONE_MINUTE)..(notBefore + ONE_MINUTE),
        )
        assertNull(notes.getNoteById(NOTE_WORK)!!.trashedAt)

        assertEquals(listOf("Work", "Home"), db.tagsDao().getTags().first().sortedBy { it.id }.map { it.nameTag })

        // Tasks and sub-tasks (a foreign key on tasks): the migrations must not have cascaded them away.
        val tasks = db.tasksDao().getAllTasks().first()
        assertEquals(listOf("Pay rent"), tasks.map { it.title })
        assertEquals(2, tasks.single().priority)
        assertEquals(
            listOf("Call landlord" to false, "Transfer money" to true),
            db.tasksDao().getSubtasks(TASK_ID).sortedBy { it.position }.map { it.title to it.isCompleted },
        )
    }

    private fun tagIds(note: NoteWithTags?): List<Long> = checkNotNull(note).tags.map { it.id }.sorted()

    /** The vault file 2.3.x wrote, see the class comment. */
    private fun writeSchema6Vault() {
        System.loadLibrary("sqlcipher")
        val schema = JSONObject(
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("com.pasich.encly.data.database.AppDatabase/6.json").bufferedReader().use { it.readText() },
        ).getJSONObject("database")
        val passphrase = ("x'" + dek.joinToString("") { "%02x".format(it) } + "'").toByteArray(Charsets.US_ASCII)
        val helper = SupportOpenHelperFactory(passphrase, null, false).create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(VaultFile.PRIMARY.fileName)
                .callback(object : SupportSQLiteOpenHelper.Callback(SCHEMA_VERSION) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createSchema6(db, schema)

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            check(helper.writableDatabase.version == SCHEMA_VERSION)
        } finally {
            helper.close()
        }
    }

    private fun createSchema6(db: SupportSQLiteDatabase, schema: JSONObject) {
        check(schema.getInt("version") == SCHEMA_VERSION)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices")
            for (j in 0 until (indices?.length() ?: 0)) {
                db.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            // The uid triggers 2.3.x installed when it created the database.
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `${table}_uid_on_insert` AFTER INSERT ON `$table` " +
                    "WHEN NEW.uid = '' BEGIN UPDATE `$table` SET uid = lower(hex(randomblob(16))) " +
                    "WHERE id = NEW.id; END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `${table}_uid_keep` AFTER UPDATE OF uid ON `$table` " +
                    "WHEN NEW.uid = '' AND OLD.uid <> '' BEGIN UPDATE `$table` SET uid = OLD.uid " +
                    "WHERE id = NEW.id; END",
            )
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))

        db.execSQL("INSERT INTO tags (id, name, visibility, position) VALUES ($TAG_WORK, 'Work', 1, 0)")
        db.execSQL("INSERT INTO tags (id, name, visibility, position) VALUES ($TAG_HOME, 'Home', 1, 1)")
        // One tag per note in 2.3.x (`tagId`); 99 names no tag, as an old version could leave it.
        for (note in NOTES) {
            db.execSQL(
                "INSERT INTO notes (id, title, value, description, date, dateCreate, tagId, isTrash, uid) " +
                    "VALUES (${note.id}, '${note.title}', 'body of ${note.title.substringBefore(' ').lowercase()}', " +
                    "'desc', 1700000000000, 1700000000000, ${note.tag ?: "NULL"}, ${note.trash}, 'note-uid-${note.id}')",
            )
        }
        db.execSQL(
            "INSERT INTO tasks (id, title, description, isCompleted, createdDate, priority, position, uid) " +
                "VALUES ($TASK_ID, 'Pay rent', 'monthly', 0, 1700000000000, 2, 0, 'task-uid')",
        )
        db.execSQL(
            "INSERT INTO subtasks (id, taskId, title, isCompleted, position, uid) " +
                "VALUES (1, $TASK_ID, 'Call landlord', 0, 0, 'sub-uid-1'), " +
                "(2, $TASK_ID, 'Transfer money', 1, 1, 'sub-uid-2')",
        )
    }

    /** A row of the schema 6 `notes` table: its tag (`tagId`, or none) and whether it is in the trash. */
    private data class NoteRow(val id: Long, val title: String, val tag: Long?, val trash: Int)

    private companion object {
        const val SCHEMA_VERSION = 6
        const val ONE_MINUTE = 60_000L
        const val TAG_WORK = 1L
        const val TAG_HOME = 2L
        const val TASK_ID = 1L
        const val NOTE_WORK = 1L
        const val NOTE_HOME = 2L
        const val NOTE_PLAIN = 3L
        const val NOTE_DANGLING = 4L
        const val NOTE_TRASHED = 5L
        const val NO_SUCH_TAG = 99L

        val NOTES = listOf(
            NoteRow(NOTE_WORK, "Work note", TAG_WORK, 0),
            NoteRow(NOTE_HOME, "Home note", TAG_HOME, 0),
            NoteRow(NOTE_PLAIN, "Plain note", null, 0),
            NoteRow(NOTE_DANGLING, "Dangling tag note", NO_SUCH_TAG, 0),
            NoteRow(NOTE_TRASHED, "Trashed note", TAG_WORK, 1),
        )
    }
}
