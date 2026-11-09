package com.pasich.encly.data.database

import androidx.room.useReaderConnection
import com.pasich.encly.data.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The size of the vault's connection pool and what each connection of it costs in memory. */
class DesktopPoolTest {
    private lateinit var root: File
    private lateinit var manager: SecureDatabaseManager

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("encly-pool-test").toFile()
        manager = SecureDatabaseManager(DesktopDatabasePlatform(Path(File(root, "databases").path)))
        assertTrue(manager.unlockDatabase(KEY, allowCreate = true))
    }

    @AfterTest
    fun tearDown() {
        manager.reset()
        root.deleteRecursively()
    }

    private fun database() = manager.getDatabase(checkNotNull(manager.captureLease()))

    @Test
    fun manyReadsAtOnceOpenNoMoreThanTheCappedPool() = runBlocking {
        val notes = database().notesDao()
        repeat(NOTES) { notes.insertNote(Note(title = "note $it")) }

        // Far more readers than the cap, all started together on threads of their own.
        val sizes = (1..READERS).map { async(Dispatchers.Default) { notes.getAllNotes().first().size } }.awaitAll()

        assertTrue(sizes.all { it == NOTES })
        // The writer, and no more readers than the queries that may run at once.
        val cap = 1 + QUERY_PARALLELISM
        assertTrue(manager.openedConnections() <= cap, "opened ${manager.openedConnections()}, cap $cap")
    }

    @Test
    fun everyConnectionKeepsAModestPageCache() = runBlocking {
        val cacheSizes = (1..READERS).map {
            async(Dispatchers.Default) {
                database().useReaderConnection { connection ->
                    connection.usePrepared("PRAGMA cache_size") {
                        it.step()
                        it.getLong(0)
                    }
                }
            }
        }.awaitAll()

        assertEquals(setOf(-LeanDriver.CACHE_KIB.toLong()), cacheSizes.toSet())
    }

    @Test
    fun theCountStartsOverWithTheNextOpening() {
        assertTrue(manager.openedConnections() >= 1)
        manager.reset()
        assertEquals(0, manager.openedConnections())
    }

    private companion object {
        const val NOTES = 5
        const val READERS = 40
        val KEY = ByteArray(32) { (it + 7).toByte() }
    }
}
