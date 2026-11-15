@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.data.database

import com.pasich.encly.data.model.Note
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The encrypted vault on iOS: Room opened through Sealite's native SQLCipher, the real driver and not a
 * stand-in, so these prove the iOS target keeps the vault encrypted and its lifecycle intact.
 */
class IosVaultTest {
    private lateinit var root: String

    // The directory does not exist yet: the first run has to create it.
    private val directory get() = "$root/data/databases"
    private lateinit var manager: SecureDatabaseManager

    @BeforeTest
    fun setUp() {
        root = NSTemporaryDirectory().trimEnd('/') + "/encly-vault-" + NSUUID().UUIDString
        manager = newManager()
    }

    @AfterTest
    fun tearDown() {
        manager.reset()
        NSFileManager.defaultManager.removeItemAtPath(root, null)
    }

    private fun newManager() = SecureDatabaseManager(IosDatabasePlatform(Path(directory)))

    private fun database(m: SecureDatabaseManager = manager): AppDatabase =
        m.getDatabase(checkNotNull(m.captureLease()))

    private fun fileOf(vault: VaultFile) = "$directory/${vault.fileName}"

    private fun exists(path: String) = SystemFileSystem.exists(Path(path))

    private fun read(path: String): ByteArray =
        SystemFileSystem.source(Path(path)).buffered().use { it.readByteArray() }

    private fun ByteArray.contains(part: ByteArray): Boolean =
        (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

    private fun write(path: String, bytes: ByteArray) {
        SystemFileSystem.createDirectories(Path(directory))
        SystemFileSystem.sink(Path(path)).buffered().use { it.write(bytes) }
    }

    private fun allBytes(vault: VaultFile): ByteArray = listOf("", "-wal").map { fileOf(vault) + it }.filter(::exists)
        .fold(ByteArray(0)) { acc, file -> acc + read(file) }

    @Test
    fun theFirstRunCreatesTheDirectoryAndAnEncryptedFile() = runBlocking {
        assertFalse(manager.hasEncryptedDatabase())
        assertTrue(manager.unlockDatabase(KEY_A, allowCreate = true))
        database().notesDao().insertNote(Note(title = TITLE, value = "[]"))
        manager.reset()

        assertTrue(exists(fileOf(VaultFile.PRIMARY)))
        assertTrue(manager.hasEncryptedDatabase())
        val bytes = allBytes(VaultFile.PRIMARY)
        assertFalse(bytes.copyOf(PLAIN_HEADER.size).contentEquals(PLAIN_HEADER), "the header must be encrypted too")
        assertFalse(bytes.contains(TITLE.encodeToByteArray()), "the note must not be readable in the file")
    }

    @Test
    fun aClosedVaultReopensWithItsNotesAndRefusesAnotherKey() = runBlocking {
        assertTrue(manager.unlockDatabase(KEY_A, allowCreate = true))
        database().notesDao().insertNote(Note(title = TITLE))
        manager.reset()
        assertFalse(manager.isDatabaseUnlocked())
        assertNull(manager.captureLease())

        assertFalse(manager.unlockDatabase(KEY_B), "another key must not open it")
        assertFalse(manager.isDatabaseUnlocked())
        assertTrue(manager.unlockDatabase(KEY_A))
        assertEquals(listOf(TITLE), database().notesDao().getAllNotes().first().map { it.title })
    }

    @Test
    fun aMissingVaultIsNotCreatedUnlessAllowed() {
        assertFalse(manager.unlockDatabase(KEY_A))

        assertFalse(manager.isDatabaseUnlocked())
        assertFalse(exists(fileOf(VaultFile.PRIMARY)))
    }

    @Test
    fun aPlaintextDatabaseIsRefusedAndLeftAlone() {
        val plain = PLAIN_HEADER + ByteArray(PAGE) { 1 }
        write(fileOf(VaultFile.PRIMARY), plain)

        assertFalse(manager.unlockDatabase(KEY_A, allowCreate = true))

        assertFalse(manager.isDatabaseUnlocked())
        assertContentEquals(plain, read(fileOf(VaultFile.PRIMARY)))
    }

    @Test
    fun aDamagedFileIsRefusedAndNeverDeleted() {
        val junk = ByteArray(PAGE * 2) { (it * 31).toByte() }
        write(fileOf(VaultFile.PRIMARY), junk)

        assertFalse(manager.unlockDatabase(KEY_A))

        assertFalse(manager.isDatabaseUnlocked())
        assertContentEquals(junk, read(fileOf(VaultFile.PRIMARY)))
    }

    @Test
    fun anOpenVaultAcceptsOnlyTheSameKeyAndFile() {
        assertTrue(manager.unlockDatabase(KEY_A, allowCreate = true))

        assertTrue(manager.unlockDatabase(KEY_A))
        assertFalse(manager.unlockDatabase(KEY_B))
        assertFalse(manager.unlockDatabase(KEY_A, allowCreate = true, file = VaultFile.DECOY))
        assertTrue(manager.isDatabaseUnlocked())
    }

    @Test
    fun aKeyOfTheWrongSizeIsRefusedBeforeAnyFileIsTouched() {
        assertFalse(manager.unlockDatabase(ByteArray(31), allowCreate = true))
        assertFalse(manager.unlockDatabase(ByteArray(33), allowCreate = true, file = VaultFile.DECOY))

        assertFalse(exists(directory))
    }

    @Test
    fun aLeaseDiesWithTheOpening() {
        assertTrue(manager.unlockDatabase(KEY_A, allowCreate = true))
        val old = checkNotNull(manager.captureLease())
        assertNotNull(manager.getDatabase(old))

        manager.reset()
        assertFailsWith<IllegalStateException> { manager.getDatabase(old) }

        assertTrue(manager.unlockDatabase(KEY_A))
        assertFailsWith<IllegalStateException> { manager.getDatabase(old) }
        assertNotNull(manager.getDatabase(checkNotNull(manager.captureLease())))
    }

    @Test
    fun theDecoyVaultHasItsOwnFileAndKey() = runBlocking {
        assertTrue(manager.unlockDatabase(KEY_A, allowCreate = true))
        database().notesDao().insertNote(Note(title = "real"))
        manager.reset()

        assertFalse(manager.hasEncryptedDatabase(VaultFile.DECOY))
        assertTrue(manager.unlockDatabase(KEY_B, allowCreate = true, file = VaultFile.DECOY))
        database().notesDao().insertNote(Note(title = "decoy"))
        manager.reset()

        assertTrue(exists(fileOf(VaultFile.DECOY)))
        assertTrue(manager.hasEncryptedDatabase(VaultFile.DECOY))
        assertTrue(manager.unlockDatabase(KEY_A))
        assertEquals(listOf("real"), database().notesDao().getAllNotes().first().map { it.title })
        manager.reset()
        assertTrue(manager.unlockDatabase(KEY_B, allowCreate = false, file = VaultFile.DECOY))
        assertEquals(listOf("decoy"), database().notesDao().getAllNotes().first().map { it.title })
    }

    @Test
    fun wipeRemovesBothVaultsAndWipeIfClosedSpareAnOpenOne() {
        assertTrue(manager.unlockDatabase(KEY_A, allowCreate = true))
        manager.reset()
        assertTrue(manager.unlockDatabase(KEY_B, allowCreate = true, file = VaultFile.DECOY))
        manager.reset()
        val unrelated = "$directory/other.db".also { write(it, byteArrayOf(1)) }

        assertTrue(manager.unlockDatabase(KEY_A))
        assertFalse(manager.wipeIfClosed(), "an open vault is not wiped")
        assertFalse(manager.wipeIfClosed(only = VaultFile.PRIMARY))
        assertTrue(exists(fileOf(VaultFile.PRIMARY)))
        assertTrue(manager.wipeIfClosed(only = VaultFile.DECOY), "the other vault's file may go")
        assertFalse(exists(fileOf(VaultFile.DECOY)))
        assertTrue(exists(fileOf(VaultFile.PRIMARY)))

        manager.wipe()

        assertFalse(manager.isDatabaseUnlocked())
        VaultFile.entries.forEach { assertFalse(exists(fileOf(it))) }
        assertTrue(exists(unrelated))
    }

    @Test
    fun theDatabaseDeletesItsSideFilesToo() {
        assertTrue(manager.unlockDatabase(KEY_A, allowCreate = true))
        manager.reset()
        listOf("-wal", "-shm", "-journal").forEach { write(fileOf(VaultFile.PRIMARY) + it, byteArrayOf(7)) }

        assertTrue(manager.wipeIfClosed())

        SystemFileSystem.list(Path(directory)).forEach { assertFalse(it.name.startsWith("database.db"), it.name) }
    }

    @Test
    fun theUidTriggersAreInstalledOnANewVault() = runBlocking {
        assertTrue(manager.unlockDatabase(KEY_A, allowCreate = true))

        val id = database().notesDao().insertNote(Note(title = "no uid"))

        assertEquals(32, checkNotNull(database().notesDao().getNoteById(id)).uid.length)
    }

    @Test
    fun parallelReadersOpenExtraConnectionsWithTheRealKey() = runBlocking {
        assertTrue(manager.unlockDatabase(KEY_A, allowCreate = true))
        repeat(NOTES) { database().notesDao().insertNote(Note(title = "n$it")) }

        val counts = (1..READERS).map {
            async(Dispatchers.Default) { database().notesDao().getAllNotes().first().size }
        }.awaitAll()

        assertTrue(counts.all { it == NOTES })
    }

    @Test
    fun aSecondManagerOpensTheSameFiles() = runBlocking {
        assertTrue(manager.unlockDatabase(KEY_A, allowCreate = true))
        database().notesDao().insertNote(Note(title = TITLE))
        manager.reset()

        val other = newManager()
        try {
            assertTrue(other.unlockDatabase(KEY_A))
            assertEquals(listOf(TITLE), database(other).notesDao().getAllNotes().first().map { it.title })
        } finally {
            other.reset()
        }
    }

    private companion object {
        const val TITLE = "a distinctive secret title 9f3a"
        const val PAGE = 4096
        const val NOTES = 40
        const val READERS = 8
        val PLAIN_HEADER = "SQLite format 3\u0000".encodeToByteArray()
        val KEY_A = ByteArray(32) { (it + 1).toByte() }
        val KEY_B = ByteArray(32) { (it + 101).toByte() }
    }
}
