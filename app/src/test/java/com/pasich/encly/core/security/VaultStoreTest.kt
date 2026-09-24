package com.pasich.encly.core.security

import com.pasich.encly.testutil.tempVaultFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The slot file: atomic replacement, damage detection, and the way out of damage. */
class VaultStoreTest {
    private val file = tempVaultFile()

    @Test
    fun valuesSurviveAReload() {
        val store = VaultStore(file)
        assertTrue(
            store.edit {
                putBytes("a", byteArrayOf(1, 2, 3))
                putInt("i", 42)
                putLong("l", Long.MAX_VALUE)
                putBoolean("b", true)
            },
        )

        val reloaded = VaultStore(file)

        assertArrayEquals(byteArrayOf(1, 2, 3), reloaded.getBytes("a"))
        assertEquals(42, reloaded.getInt("i", 0))
        assertEquals(Long.MAX_VALUE, reloaded.getLong("l", 0))
        assertTrue(reloaded.getBoolean("b", false))
        assertFalse(reloaded.isCorrupt())
    }

    @Test
    fun aMissingFileIsAnEmptyStoreNotADamagedOne() {
        val store = VaultStore(file)

        assertFalse(store.isCorrupt())
        assertNull(store.getBytes("a"))
        assertEquals(7, store.getInt("i", 7))
    }

    @Test
    fun valuesAreCopiedInAndOut() {
        val store = VaultStore(file)
        val value = byteArrayOf(1, 2, 3)
        store.edit { putBytes("a", value) }
        value.fill(0)
        store.getBytes("a")!!.fill(0)

        assertArrayEquals(byteArrayOf(1, 2, 3), store.getBytes("a"))
    }

    @Test
    fun oneEditIsOneWrite() {
        val store = VaultStore(file)
        store.edit {
            putInt("x", 1)
            putInt("y", 1)
        }
        store.edit {
            putInt("x", 2)
            remove("y")
            putInt("z", 3)
        }

        val reloaded = VaultStore(file)
        assertEquals(2, reloaded.getInt("x", 0))
        assertFalse(reloaded.contains("y"))
        assertEquals(3, reloaded.getInt("z", 0))
    }

    @Test
    fun removePrefixDropsOnlyThatGroup() {
        val store = VaultStore(file)
        store.edit {
            putInt("pin.a", 1)
            putInt("pin.b", 1)
            putInt("recovery.a", 1)
        }
        store.edit { removePrefix("pin.") }

        assertFalse(store.contains("pin.a"))
        assertFalse(store.contains("pin.b"))
        assertTrue(store.contains("recovery.a"))
    }

    @Test
    fun aCrashBeforeTheRenameKeepsTheOldStateIntact() {
        val store = VaultStore(file)
        store.edit { putInt("x", 1) }
        store.beforeRename = { throw CrashBeforeRename() }

        runCatching { store.edit { putInt("x", 2) } }

        // What the next process start sees: the old file, whole; the leftover temp is ignored.
        val restarted = VaultStore(file)
        assertFalse(restarted.isCorrupt())
        assertEquals(1, restarted.getInt("x", 0))
        assertFalse("the temp file is cleaned up", File(file.path + ".tmp").exists())
    }

    @Test
    fun aHalfWrittenTempFileIsNeverRead() {
        VaultStore(file).edit { putInt("x", 1) }
        File(file.path + ".tmp").writeBytes(byteArrayOf(9, 9, 9))

        val store = VaultStore(file)

        assertEquals(1, store.getInt("x", 0))
        assertFalse(store.isCorrupt())
    }

    @Test
    fun aFailedWriteChangesNothing() {
        val store = VaultStore(file)
        store.edit { putInt("x", 1) }
        val dir = file.parentFile!!
        dir.setWritable(false)
        try {
            assertFalse(store.edit { putInt("x", 2) })
        } finally {
            dir.setWritable(true)
        }

        assertEquals(1, store.getInt("x", 0))
        assertEquals(1, VaultStore(file).getInt("x", 0))
    }

    @Test
    fun aDamagedFileReadsAsEmptyAndRefusesWrites() {
        VaultStore(file).edit { putBytes("pin.slot", ByteArray(64) { 5 }) }
        val bytes = file.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x40).toByte()
        file.writeBytes(bytes)

        val store = VaultStore(file)

        assertTrue(store.isCorrupt())
        assertNull(store.getBytes("pin.slot"))
        assertFalse(store.edit { putInt("x", 1) })
        assertTrue("the damaged file is kept until the user wipes", file.exists())
    }

    @Test
    fun aTruncatedOrForeignFileIsDamaged() {
        VaultStore(file).edit { putInt("x", 1) }
        file.writeBytes(file.readBytes().copyOf(20))
        assertTrue(VaultStore(file).isCorrupt())

        file.writeText("<?xml version='1.0'?><map/>")
        assertTrue(VaultStore(file).isCorrupt())
    }

    @Test
    fun clearIsTheWayOutOfDamage() {
        file.writeBytes(ByteArray(100) { 1 })
        val store = VaultStore(file)
        assertTrue(store.isCorrupt())

        assertTrue(store.clear())

        assertFalse(store.isCorrupt())
        assertFalse(file.exists())
        assertTrue(store.edit { putInt("x", 1) })
        assertEquals(1, VaultStore(file).getInt("x", 0))
    }

    private class CrashBeforeRename : RuntimeException()
}
