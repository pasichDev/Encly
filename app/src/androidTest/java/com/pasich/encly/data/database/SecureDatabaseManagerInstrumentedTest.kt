package com.pasich.encly.data.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pasich.encly.data.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.spec.SecretKeySpec

/**
 * Exercises the native SQLCipher lifecycle that JVM tests cannot load.
 *
 * Runs inside the app under test and deletes its database.db: run it on an emulator or a
 * device without a debug install you care about.
 */
@RunWith(AndroidJUnit4::class)
class SecureDatabaseManagerInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var manager: SecureDatabaseManager

    @Before
    fun setUp() {
        manager = SecureDatabaseManager(context)
        manager.wipe()
    }

    @After
    fun tearDown() {
        manager.wipe()
    }

    @Test
    fun concurrentReadsAfterUnlockOpenExtraConnectionsWithTheRealKey() = runBlocking {
        assertTrue(manager.unlockDatabase(SecretKeySpec(KEY_A, "AES"), allowCreate = true))
        insertNotes(NOTE_COUNT)

        // Parallel readers make the WAL pool open secondary connections, each keyed from the
        // passphrase buffer the open-helper factory keeps. A zeroed buffer fails here with
        // "file is not a database".
        val counts = (1..PARALLEL_READERS).map {
            async(Dispatchers.IO) { manager.getDatabase().notesDao().getAllNotes().first().size }
        }.awaitAll()

        assertTrue(counts.all { it == NOTE_COUNT })
    }

    @Test
    fun lockThenUnlockAgainReopensTheSameData() = runBlocking {
        assertTrue(manager.unlockDatabase(SecretKeySpec(KEY_A, "AES"), allowCreate = true))
        insertNotes(NOTE_COUNT)

        manager.reset()
        assertFalse(manager.isDatabaseUnlocked())
        assertTrue(manager.unlockDatabase(SecretKeySpec(KEY_A, "AES")))

        assertEquals(NOTE_COUNT, manager.getDatabase().notesDao().getAllNotes().first().size)
    }

    @Test
    fun wrongKeyDoesNotOpenACommittedDatabase() = runBlocking {
        assertTrue(manager.unlockDatabase(SecretKeySpec(KEY_A, "AES"), allowCreate = true))
        manager.reset()

        assertFalse(manager.unlockDatabase(SecretKeySpec(KEY_B, "AES")))
        assertTrue(manager.hasEncryptedDatabase())
    }

    private suspend fun insertNotes(count: Int) {
        val dao = manager.getDatabase().notesDao()
        repeat(count) { dao.insertNote(Note(title = "note $it", value = "body $it")) }
    }

    private companion object {
        const val NOTE_COUNT = 25
        const val PARALLEL_READERS = 8
        val KEY_A = ByteArray(32) { it.toByte() }
        val KEY_B = ByteArray(32) { (0xA0 + it).toByte() }
    }
}
