@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.core.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.IOException
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The vault file on the iOS file system: the atomic replace, the temp file and the failures. */
class IosVaultFileIoTest {
    private val directory = NSTemporaryDirectory() + "vault-" + NSUUID().UUIDString
    private val path = "$directory/nested/vault_state.bin"
    private val io = IosVaultFileIo(path)

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }

    @Test
    fun aMissingFileDoesNotExistAndCannotBeRead() {
        assertFalse(io.exists())
        assertFailsWith<IOException> { io.read() }
        assertTrue(io.delete())
    }

    @Test
    fun replaceCreatesTheFileAndItsDirectoriesAndReadReturnsTheBytes() {
        val bytes = ByteArray(20_000) { it.toByte() }

        io.replace(bytes, beforeRename = null)

        assertTrue(io.exists())
        assertContentEquals(bytes, io.read())
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath("$path.tmp"))
    }

    @Test
    fun replaceOverwritesAndAnEmptyFileReadsEmpty() {
        io.replace(byteArrayOf(1, 2, 3), beforeRename = null)
        io.replace(byteArrayOf(4), beforeRename = null)
        assertContentEquals(byteArrayOf(4), io.read())

        io.replace(ByteArray(0), beforeRename = null)
        assertContentEquals(ByteArray(0), io.read())
    }

    @Test
    fun aFailureBeforeTheRenameKeepsTheOldFileAndRemovesTheTempFile() {
        io.replace(byteArrayOf(1, 2, 3), beforeRename = null)

        assertFailsWith<IOException> { io.replace(byteArrayOf(9), beforeRename = { throw IOException("crash") }) }

        assertContentEquals(byteArrayOf(1, 2, 3), io.read())
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath("$path.tmp"))
    }

    @Test
    fun deleteTempRemovesALeftoverAndDeleteRemovesTheFile() {
        io.replace(byteArrayOf(1), beforeRename = null)
        NSFileManager.defaultManager.createFileAtPath("$path.tmp", NSData(), null)

        io.deleteTemp()
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath("$path.tmp"))
        io.deleteTemp()

        assertTrue(io.delete())
        assertFalse(io.exists())
    }

    @Test
    fun aDirectoryThatCannotBeCreatedIsAnIoException() {
        val blocked = IosVaultFileIo("$path/inside/vault_state.bin")
        io.replace(byteArrayOf(1), beforeRename = null)

        assertFailsWith<IOException> { blocked.replace(byteArrayOf(1), beforeRename = null) }
    }

    @Test
    fun aVaultStoreOnTheFileKeepsWhatItWrote() {
        val store = VaultStore(io)
        assertTrue(store.edit { putBytes("a", byteArrayOf(1, 2, 3)) })

        val reopened = VaultStore(IosVaultFileIo(path))

        assertFalse(reopened.isCorrupt())
        assertContentEquals(byteArrayOf(1, 2, 3), reopened.getBytes("a"))
        assertEquals(null, reopened.getBytes("b"))
    }
}
