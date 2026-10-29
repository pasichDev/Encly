package com.pasich.encly.core.security

import com.pasich.encly.testutil.tempVaultFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * The slot file is written and read by hand-coded big-endian and modified-UTF-8 routines. The
 * format must stay the one `DataOutputStream` wrote, byte for byte: a file an earlier build wrote
 * that no longer parses would read as damage, and damage locks the vault.
 */
class VaultStoreLegacyFormatTest {
    private val file = tempVaultFile()

    private val keys = listOf(
        "plain.key",
        "café",
        "nul\u0000inside",
        "汉字",
        "emoji😀",
        "bio.slot",
        "",
    )

    private fun entries() = keys.mapIndexed { index, key -> key to ByteArray(index * 3) { (it + index).toByte() } }

    /** What the build before the hand-coded codec wrote for [entries]. */
    private fun legacyFile(entries: List<Pair<String, ByteArray>>): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.write("ENCLYVS3".toByteArray(Charsets.US_ASCII))
            out.writeInt(entries.size)
            entries.forEach { (key, value) ->
                out.writeUTF(key)
                out.writeInt(value.size)
                out.write(value)
            }
        }
        val body = buffer.toByteArray()
        return body + MessageDigest.getInstance("SHA-256").digest(body)
    }

    @Test
    fun theFileIsByteIdenticalToWhatDataOutputStreamWrote() {
        val entries = entries()
        val store = VaultStore(file)

        assertTrue(store.edit { entries.forEach { (key, value) -> putBytes(key, value) } })

        assertArrayEquals(legacyFile(entries), file.readBytes())
    }

    @Test
    fun aFileDataOutputStreamWroteIsReadBack() {
        val entries = entries()
        file.writeBytes(legacyFile(entries))

        val store = VaultStore(file)

        assertFalse(store.isCorrupt())
        assertEquals(entries.map { it.first }.toSet(), store.keys())
        entries.forEach { (key, value) -> assertArrayEquals(key, value, store.getBytes(key)) }
    }

    @Test
    fun typedValuesAreBigEndianLikeByteBuffer() {
        VaultStore(file).edit {
            putInt("i", -0x12345678)
            putLong("l", -0x123456789ABCDEFL)
            putBoolean("b", true)
        }

        val reread = VaultStore(file)

        assertEquals(-0x12345678, reread.getInt("i", 0))
        assertEquals(-0x123456789ABCDEFL, reread.getLong("l", 0L))
        assertTrue(reread.getBoolean("b", false))
        assertArrayEquals(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(-0x12345678).array(), reread.getBytes("i"))
        assertArrayEquals(
            ByteBuffer.allocate(Long.SIZE_BYTES).putLong(-0x123456789ABCDEFL).array(),
            reread.getBytes("l"),
        )
    }

    @Test
    fun aMalformedKeyEncodingIsDamage() {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            write("ENCLYVS3".toByteArray(Charsets.US_ASCII))
            writeInt(1)
            writeShort(2)
            write(byteArrayOf(0xC3.toByte(), 0x41)) // a lead byte followed by a non-continuation
            writeInt(0)
        }
        val body = out.toByteArray()
        file.writeBytes(body + MessageDigest.getInstance("SHA-256").digest(body))

        assertTrue(VaultStore(file).isCorrupt())
    }
}
