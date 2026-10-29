package com.pasich.encly.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pasich.encly.data.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

/**
 * A vault written the way releases up to 2.3 wrote it (sqlcipher-android 4.19, the DEK as a raw
 * key `x'<64 hex>'`) opens with the current driver: same notes, still writable, and still refused
 * under another key. Existing installs depend on it.
 *
 * Runs inside the app under test and deletes its database.db: run it on an emulator.
 */
@RunWith(AndroidJUnit4::class)
class EarlierReleaseVaultTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var manager: SecureDatabaseManager
    private val dek = ByteArray(32).also(SecureRandom()::nextBytes)

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
    fun aVaultOfAnEarlierReleaseOpensWithItsNotes() = runBlocking {
        writeVaultTheEarlierWay(listOf("first", "second"))

        assertTrue(manager.unlockDatabase(dek))
        val db = manager.getDatabase(manager.captureLease()!!)
        assertEquals(setOf("first", "second"), db.notesDao().getAllNotes().first().map { it.title }.toSet())

        db.notesDao().insertNote(Note(title = "third"))
        manager.reset()

        assertFalse(manager.unlockDatabase(ByteArray(32) { 7 }))
        assertTrue(manager.unlockDatabase(dek))
        val reopened = manager.getDatabase(manager.captureLease()!!)
        assertEquals(3, reopened.notesDao().getAllNotes().first().size)
    }

    private suspend fun writeVaultTheEarlierWay(titles: List<String>) {
        System.loadLibrary("sqlcipher")
        val passphrase = rawKey(dek)
        val db = VaultSchema.install(
            Room.databaseBuilder(context, AppDatabase::class.java, VaultFile.PRIMARY.fileName),
        )
            .openHelperFactory(SupportOpenHelperFactory(passphrase, null, false))
            .build()
        try {
            titles.forEach { db.notesDao().insertNote(Note(title = it)) }
        } finally {
            db.close()
        }
        assertTrue(manager.hasEncryptedDatabase())
    }

    /** The key form releases up to 2.3 handed to sqlcipher-android: `x'<hex>'` as ASCII. */
    private fun rawKey(key: ByteArray): ByteArray =
        ("x'" + key.joinToString("") { "%02x".format(it) } + "'").toByteArray(Charsets.US_ASCII)
}
