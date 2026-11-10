package com.pasich.encly.core.security

import com.pasich.encly.testutil.tempVaultFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * The slot file's format as seen from outside: files that pass the checksum but are not a valid
 * store (wrong magic, absurd sizes, trailing bytes) are damage, and a value read back with the
 * wrong type falls to the default instead of being misread.
 */
class VaultStoreFormatTest {
    private val file = tempVaultFile()

    /** A file with a valid checksum around [body]. */
    private fun write(body: ByteArray) {
        file.writeBytes(body + MessageDigest.getInstance("SHA-256").digest(body))
    }

    private fun body(
        magic: ByteArray = "ENCLYVS3".toByteArray(Charsets.US_ASCII),
        count: Int,
        entries: DataOutputStream.() -> Unit = {},
        trailing: ByteArray = ByteArray(0),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            write(magic)
            writeInt(count)
            entries()
        }
        return out.toByteArray() + trailing
    }

    @Test
    fun aWellFormedFileFromOutsideIsReadBack() {
        write(
            body(
                count = 1,
                entries = {
                    writeUTF("k")
                    writeInt(2)
                    write(byteArrayOf(7, 8))
                },
            ),
        )
        val store = VaultStore(file)

        assertFalse(store.isCorrupt())
        assertArrayEquals(byteArrayOf(7, 8), store.getBytes("k"))
    }

    @Test
    fun aFileWithAnotherMagicIsDamageEvenWithAValidChecksum() {
        write(body(magic = "ENCLYVS2".toByteArray(Charsets.US_ASCII), count = 0))

        assertTrue(VaultStore(file).isCorrupt())
    }

    @Test
    fun anEntryCountBeyondTheLimitOrBelowZeroIsDamage() {
        write(body(count = 257))
        assertTrue(VaultStore(file).isCorrupt())

        write(body(count = -1))
        assertTrue(VaultStore(file).isCorrupt())
    }

    @Test
    fun aValueLengthBeyondTheLimitIsDamage() {
        write(
            body(
                count = 1,
                entries = {
                    writeUTF("k")
                    writeInt(64 * 1024 + 1)
                },
            ),
        )

        assertTrue(VaultStore(file).isCorrupt())
    }

    @Test
    fun aNegativeValueLengthIsDamage() {
        write(
            body(
                count = 1,
                entries = {
                    writeUTF("k")
                    writeInt(-5)
                },
            ),
        )

        assertTrue(VaultStore(file).isCorrupt())
    }

    @Test
    fun anEntryCutShortByTheEndOfTheBodyIsDamage() {
        write(
            body(
                count = 2,
                entries = {
                    writeUTF("k")
                    writeInt(1)
                    write(byteArrayOf(1))
                },
            ),
        )

        assertTrue(VaultStore(file).isCorrupt())
    }

    @Test
    fun bytesAfterTheLastEntryAreDamage() {
        write(body(count = 0, trailing = byteArrayOf(1, 2, 3)))

        assertTrue(VaultStore(file).isCorrupt())
    }

    @Test
    fun aChecksumThatDoesNotMatchIsDamage() {
        write(body(count = 0))
        file.writeBytes(file.readBytes().also { it[it.size - 1] = (it.last().toInt() xor 1).toByte() })

        assertTrue(VaultStore(file).isCorrupt())
    }

    @Test
    fun aDamagedStoreRefusesEveryWriteUntilItIsCleared() {
        file.writeBytes(ByteArray(80) { 9 })
        val store = VaultStore(file)

        assertFalse(store.edit { putInt("a", 1) })
        assertFalse(store.contains("a"))

        assertTrue(store.clear())
        assertFalse(store.isCorrupt())
        assertTrue(store.edit { putInt("a", 1) })
        assertEquals(1, VaultStore(file).getInt("a", 0))
    }

    @Test
    fun aValueReadWithTheWrongTypeFallsToTheDefault() {
        val store = VaultStore(file)
        store.edit {
            putBytes("three", byteArrayOf(1, 2, 3))
            putInt("int", 5)
            putLong("long", 6L)
            putBoolean("flag", true)
        }

        assertEquals(-1, store.getInt("three", -1))
        assertEquals(-2L, store.getLong("three", -2L))
        assertFalse(store.getBoolean("three", false))
        assertEquals(-1L, store.getLong("int", -1L))
        assertEquals(-1, store.getInt("long", -1))
        assertEquals(-1, store.getInt("flag", -1))
        assertEquals(9, store.getInt("missing", 9))
    }

    @Test
    fun aBooleanThatIsNotOneReadsAsFalse() {
        val store = VaultStore(file)
        store.edit { putBytes("b", byteArrayOf(2)) }

        assertFalse(store.getBoolean("b", true))
    }

    @Test
    fun aFileThatIsGoneAfterLoadingIsStillCleared() {
        val store = VaultStore(file)
        store.edit { putInt("a", 1) }
        file.delete()

        assertTrue(store.clear())
        assertFalse(store.contains("a"))
    }

    @Test
    fun aStoreWhoseDirectoryIsAFileCanNotBeWrittenAndSaysSo() {
        val blocker = tempVaultFile().also { it.writeBytes(byteArrayOf(1)) }
        val store = VaultStore(java.io.File(blocker, "vault_state.bin"))

        assertFalse(store.edit { putInt("a", 1) })
        assertFalse(store.contains("a"))
    }
}
