package com.pasich.encly.data.importer

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.writeIntLe
import kotlinx.io.writeLongLe
import kotlinx.io.writeShortLe
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipStreamReaderTest {
    private fun source(bytes: ByteArray) = Buffer().apply { write(bytes) }

    private fun ZipStreamReader.bytes(): ByteArray = data().buffered().readByteArray()

    private fun readAll(zip: ByteArray): List<Pair<String, ByteArray>> {
        val reader = ZipStreamReader(source(zip))
        val out = ArrayList<Pair<String, ByteArray>>()
        while (true) {
            val entry = reader.next() ?: return out
            out += entry.name to reader.bytes()
        }
    }

    private fun storedZip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val entry = ZipEntry("s.txt").apply {
                method = ZipEntry.STORED
                size = data.size.toLong()
                compressedSize = data.size.toLong()
                crc = CRC32().apply { update(data) }.value
            }
            zip.putNextEntry(entry)
            zip.write(data)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /** The message of the IOException reading [zip] to its end raises. */
    private fun failure(zip: ByteArray): String? = assertThrows(IOException::class.java) { readAll(zip) }.message

    /** Where the data descriptor (signature 0x08074b50) of the first deflated entry starts. */
    private fun descriptorAt(bytes: ByteArray): Int = bytes.indices.first { at ->
        at + 3 < bytes.size && bytes[at] == 0x50.toByte() && bytes[at + 1] == 0x4b.toByte() &&
            bytes[at + 2] == 7.toByte() && bytes[at + 3] == 8.toByte()
    }

    @Test
    fun readsDeflatedEntriesWithTheJdksDescriptorAndSkipsDirectories() {
        val zip = ImportFixtures.zip("a.txt" to "hello".toByteArray(), "dir/b.txt" to ByteArray(100_000) { 7 })
        val entries = readAll(zip)
        assertEquals(listOf("a.txt", "dir/b.txt"), entries.map { it.first })
        assertEquals("hello", String(entries[0].second))
        assertEquals(100_000, entries[1].second.size)
    }

    @Test
    fun readsStoredEntriesWithKnownSizes() {
        val data = "stored text".toByteArray()
        assertArrayEquals(data, readAll(storedZip(data)).single().second)
    }

    @Test
    fun anEntryLeftUnreadIsSkippedToTheNext() {
        val zip = ImportFixtures.zip("a.txt" to ByteArray(50_000) { 1 }, "b.txt" to "b".toByteArray())
        val reader = ZipStreamReader(source(zip))
        assertEquals("a.txt", reader.next()?.name)
        assertEquals("b.txt", reader.next()?.name)
        assertEquals("b", String(reader.bytes()))
        assertNull(reader.next())
    }

    @Test
    fun aDamagedStoredEntryFailsItsChecksum() {
        val bytes = storedZip("stored text".toByteArray())
        // The first copy of the text is the entry's data (the name is shorter).
        bytes[String(bytes, Charsets.ISO_8859_1).indexOf("stored text")] = 'X'.code.toByte()
        assertEquals("zip checksum mismatch", failure(bytes))
    }

    @Test
    fun aTruncatedZipIsAnIoError() {
        val zip = ImportFixtures.zip("a.txt" to ByteArray(10_000) { (it % 251).toByte() })
        assertThrows(IOException::class.java) { readAll(zip.copyOf(zip.size / 2)) }
    }

    @Test
    fun readsZip64StreamsWithA64BitDescriptorAsStreamingWritersProduceThem() {
        // Notesnook's export: ZIP64 local headers, sizes in a data descriptor without a signature.
        val zip = ImportFixtures.resource("notesnook/notes-md.zip")
        assertNotNull("real export fixture missing", zip)
        val entries = readAll(zip!!)
        assertEquals(5, entries.size)
        assertEquals("# Groceries", String(entries.first { it.first == "Groceries.md" }.second).lines().first())
    }

    @Test
    fun aDirectoryEntryWithDataIsRefusedInsteadOfInflatedForever() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("d/"))
            zip.write(ByteArray(8 * 1024 * 1024))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("after.txt"))
            zip.write(1)
            zip.closeEntry()
        }
        val reader = ZipStreamReader(source(out.toByteArray()))
        assertEquals("d/", reader.next()?.name)
        assertThrows(IOException::class.java) { reader.next() }
    }

    @Test
    fun anUnknownSignatureBetweenEntriesIsAnIoErrorNotTheEnd() {
        val bytes = ImportFixtures.zipText("a.txt" to "a", "b.txt" to "b")
        val second = bytes.indices.drop(1).first { at ->
            at + 3 < bytes.size && bytes[at] == 0x50.toByte() && bytes[at + 1] == 0x4b.toByte() &&
                bytes[at + 2] == 3.toByte() && bytes[at + 3] == 4.toByte()
        }
        bytes[second + 2] = 9
        val reader = ZipStreamReader(source(bytes))
        assertEquals("a.txt", reader.next()?.name)
        assertThrows(IOException::class.java) { reader.next() }
    }

    @Test
    fun onlyTheCentralDirectoryAnEndRecordOrACleanEndFinishesAnArchive() {
        fun ends(third: Int, fourth: Int): Boolean {
            val bytes = byteArrayOf(0x50, 0x4b, third.toByte(), fourth.toByte()) + ByteArray(18)
            return ZipStreamReader(source(bytes)).next() == null
        }
        assertEquals(true, ends(0x01, 0x02)) // central directory header
        assertEquals(true, ends(0x05, 0x06)) // end of central directory
        assertEquals(true, ends(0x06, 0x06)) // ZIP64 end of central directory
        assertEquals(true, ends(0x06, 0x08)) // archive extra data
        assertNull(ZipStreamReader(source(ByteArray(0))).next())
    }

    @Test
    fun aNameThatIsNotUtf8IsReadAsCp437SoDifferentNamesStayDifferent() {
        val out = ByteArrayOutputStream()
        // Latin-1 name bytes and no UTF-8 flag: 0x82 and 0x83 are not valid UTF-8 on their own.
        ZipOutputStream(out, Charsets.ISO_8859_1).use { zip ->
            for (name in listOf("\u0082.md", "\u0083.md")) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(1)
                zip.closeEntry()
            }
        }
        assertEquals(listOf("\u00e9.md", "\u00e2.md"), readAll(out.toByteArray()).map { it.first })
    }

    @Test
    fun aUtf8NameIsReadAsUtf8() {
        val names = readAll(ImportFixtures.zipText("Привіт/нотатка.md" to "x")).map { it.first }
        assertEquals(listOf("Привіт/нотатка.md"), names)
    }

    @Test
    fun anEncryptedEntryIsRefused() {
        val bytes = ImportFixtures.zipText("a.txt" to "a")
        bytes[FLAGS_AT] = (bytes[FLAGS_AT].toInt() or 1).toByte()
        assertEquals("unsupported zip entry", failure(bytes))
    }

    @Test
    fun aMethodOtherThanStoredOrDeflatedIsRefused() {
        val bytes = ImportFixtures.zipText("a.txt" to "a")
        bytes[METHOD_AT] = 12 // bzip2
        assertEquals("unsupported zip entry", failure(bytes))
    }

    @Test
    fun aStoredEntryOfUnknownSizeIsRefused() {
        val bytes = storedZip("stored text".toByteArray())
        bytes[FLAGS_AT] = (bytes[FLAGS_AT].toInt() or 8).toByte() // sizes follow the data
        assertEquals("stored zip entry of unknown size", failure(bytes))
    }

    @Test
    fun aDeflatedEntryWithAWrongChecksumInItsDescriptorIsRefused() {
        val bytes = ImportFixtures.zipText("a.txt" to "hello")
        bytes[descriptorAt(bytes) + 4] = (bytes[descriptorAt(bytes) + 4].toInt() xor 0x5a).toByte()
        assertEquals("zip checksum mismatch", failure(bytes))
    }

    @Test
    fun aDescriptorWhoseSizesDoNotMatchWhatWasInflatedIsRefused() {
        val bytes = ImportFixtures.zipText("a.txt" to "hello")
        val size = descriptorAt(bytes) + 12 // signature, CRC, compressed size, then the size
        bytes[size] = (bytes[size].toInt() + 1).toByte()
        assertEquals("bad zip data descriptor", failure(bytes))
    }

    @Test
    fun corruptDeflateDataIsRefused() {
        val bytes = ImportFixtures.zipText("a.txt" to "hello")
        bytes[LOCAL_HEADER + "a.txt".length] = 0x07 // a final block of the reserved type 3
        assertEquals("bad zip data", failure(bytes))
    }

    @Test
    fun aStoredEntryThatEndsEarlyIsRefusedAsTruncated() {
        val bytes = storedZip("stored text".toByteArray())
        assertEquals("truncated zip", failure(bytes.copyOf(LOCAL_HEADER + "s.txt".length + 3)))
    }

    @Test
    fun aHeaderCutShortIsRefusedAsTruncated() {
        assertEquals("truncated zip", failure(ImportFixtures.zipText("a.txt" to "a").copyOf(10)))
    }

    @Test
    fun theZip64SizesOfAnExtraFieldReplaceTheMarkerSizes() {
        // A stored entry whose 32-bit sizes are the 0xFFFFFFFF marker and whose real sizes sit in a ZIP64 extra field.
        val data = "zip64 text".toByteArray()
        val name = "z.txt".toByteArray()
        val extra = Buffer().apply {
            writeShortLe(1)
            writeShortLe(16)
            writeLongLe(data.size.toLong())
            writeLongLe(data.size.toLong())
        }.readByteArray()
        val header = Buffer().apply {
            writeIntLe(0x04034b50)
            writeShortLe(45)
            writeShortLe(0)
            writeShortLe(0) // stored
            writeIntLe(0)
            writeIntLe(CRC32().apply { update(data) }.value.toInt())
            writeIntLe(-1)
            writeIntLe(-1)
            writeShortLe(name.size.toShort())
            writeShortLe(extra.size.toShort())
            write(name)
            write(extra)
            write(data)
        }.readByteArray()
        assertArrayEquals(data, readAll(header).single().second)
    }

    private companion object {
        const val LOCAL_HEADER = 30
        const val FLAGS_AT = 6
        const val METHOD_AT = 8
    }
}
