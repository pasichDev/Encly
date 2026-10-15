package com.pasich.encly.data.database

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The parts of the database owner that do not need the native SQLCipher library: the raw-key
 * syntax, the refusals before any file is touched, and which files a wipe removes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecureDatabaseManagerTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var manager: SecureDatabaseManager

    @Before
    fun setUp() {
        manager = SecureDatabaseManager(context)
        listOf(VaultFile.PRIMARY, VaultFile.DECOY).forEach { vault ->
            suffixes.forEach { File(context.getDatabasePath(vault.fileName).path + it).delete() }
        }
    }

    private val suffixes = listOf("", "-wal", "-shm", "-journal")

    private fun files(vault: VaultFile) = suffixes.map { File(context.getDatabasePath(vault.fileName).path + it) }

    private fun createFiles(vault: VaultFile) = files(vault).forEach {
        it.parentFile?.mkdirs()
        it.writeBytes(ByteArray(64) { 7 })
    }

    @Test
    fun theRawKeyIsHexInsideSqlcipherRawKeySyntax() {
        val dek = ByteArray(32) { (it * 8 + 1).toByte() }

        val passphrase = SecureDatabaseManager.rawKeyPassphrase(dek)

        val expected = "x'" + dek.joinToString("") { "%02x".format(it) } + "'"
        assertEquals(67, passphrase.size)
        assertArrayEquals(expected.toByteArray(Charsets.US_ASCII), passphrase)
    }

    @Test
    fun theRawKeyUsesLowercaseHexForHighBytes() {
        val passphrase = SecureDatabaseManager.rawKeyPassphrase(ByteArray(32) { 0xAB.toByte() })

        assertEquals("x'" + "ab".repeat(32) + "'", String(passphrase, Charsets.US_ASCII))
    }

    @Test
    fun aKeyOfTheWrongSizeIsRefusedWithoutTouchingAnyFile() {
        assertFalse(manager.unlockDatabase(ByteArray(31), allowCreate = true))
        assertFalse(manager.unlockDatabase(ByteArray(33), allowCreate = true, file = VaultFile.DECOY))

        assertFalse(manager.isDatabaseUnlocked())
        assertFalse(context.getDatabasePath(VaultFile.PRIMARY.fileName).exists())
    }

    @Test
    fun aLockedManagerHasNoLeaseAndGrantsNoDatabase() {
        assertNull(manager.captureLease())
        assertFalse(manager.isDatabaseUnlocked())
        assertThrows(IllegalStateException::class.java) { manager.getDatabase(DatabaseLease()) }
    }

    @Test
    fun resettingALockedManagerIsHarmless() {
        manager.reset()
        manager.reset()

        assertFalse(manager.isDatabaseUnlocked())
        assertNull(manager.captureLease())
    }

    @Test
    fun theTwoVaultsHaveTheirOwnFiles() {
        assertEquals("database.db", VaultFile.PRIMARY.fileName)
        assertEquals("vault_b.db", VaultFile.DECOY.fileName)
    }

    @Test
    fun noFileMeansNoEncryptedDatabase() {
        assertFalse(manager.hasEncryptedDatabase())
        assertFalse(manager.hasEncryptedDatabase(VaultFile.DECOY))
    }

    @Test
    fun aFileWithoutTheSqliteHeaderCountsAsTheEncryptedDatabase() {
        context.getDatabasePath(VaultFile.DECOY.fileName).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(4096) { (it * 13).toByte() })
        }

        assertTrue(manager.hasEncryptedDatabase(VaultFile.DECOY))
        assertFalse("the other vault's file is not this one", manager.hasEncryptedDatabase(VaultFile.PRIMARY))
    }

    @Test
    fun wipeRemovesEveryFileOfBothVaultsAndNothingElse() {
        createFiles(VaultFile.PRIMARY)
        createFiles(VaultFile.DECOY)
        val unrelated = File(context.getDatabasePath("other.db").path).apply { writeBytes(byteArrayOf(1)) }

        manager.wipe()

        (files(VaultFile.PRIMARY) + files(VaultFile.DECOY)).forEach { assertFalse(it.path, it.exists()) }
        assertTrue(unrelated.exists())
    }

    @Test
    fun wipeIfClosedOfOneVaultLeavesTheOtherAlone() {
        createFiles(VaultFile.PRIMARY)
        createFiles(VaultFile.DECOY)

        assertTrue(manager.wipeIfClosed(only = VaultFile.DECOY))

        files(VaultFile.DECOY).forEach { assertFalse(it.path, it.exists()) }
        assertTrue(files(VaultFile.PRIMARY).first().exists())
    }

    @Test
    fun wipeIfClosedWithoutAVaultNamedRemovesBoth() {
        createFiles(VaultFile.PRIMARY)
        createFiles(VaultFile.DECOY)

        assertTrue(manager.wipeIfClosed())

        (files(VaultFile.PRIMARY) + files(VaultFile.DECOY)).forEach { assertFalse(it.path, it.exists()) }
    }
}
