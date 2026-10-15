package com.pasich.encly.data.importer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipStreamReaderTest {
    private fun readAll(zip: ByteArray): List<Pair<String, ByteArray>> {
        val reader = ZipStreamReader(zip.inputStream())
        val out = ArrayList<Pair<String, ByteArray>>()
        while (true) {
            val entry = reader.next() ?: return out
            out += entry.name to reader.data().readBytes()
        }
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
        assertArrayEquals(data, readAll(out.toByteArray()).single().second)
    }

    @Test
    fun anEntryLeftUnreadIsSkippedToTheNext() {
        val zip = ImportFixtures.zip("a.txt" to ByteArray(50_000) { 1 }, "b.txt" to "b".toByteArray())
        val reader = ZipStreamReader(zip.inputStream())
        assertEquals("a.txt", reader.next()?.name)
        assertEquals("b.txt", reader.next()?.name)
        assertEquals("b", String(reader.data().readBytes()))
        assertNull(reader.next())
    }

    @Test
    fun aDamagedStoredEntryFailsItsChecksum() {
        val data = "stored text".toByteArray()
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
        val bytes = out.toByteArray()
        // The first copy of the text is the entry's data (the name is shorter).
        bytes[String(bytes, Charsets.ISO_8859_1).indexOf("stored text")] = 'X'.code.toByte()
        assertThrows(IOException::class.java) { readAll(bytes) }
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
        val reader = ZipStreamReader(out.toByteArray().inputStream())
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
        val reader = ZipStreamReader(bytes.inputStream())
        assertEquals("a.txt", reader.next()?.name)
        assertThrows(IOException::class.java) { reader.next() }
    }

    @Test
    fun onlyTheCentralDirectoryAnEndRecordOrACleanEndFinishesAnArchive() {
        fun ends(third: Int, fourth: Int): Boolean {
            val bytes = byteArrayOf(0x50, 0x4b, third.toByte(), fourth.toByte()) + ByteArray(18)
            return ZipStreamReader(bytes.inputStream()).next() == null
        }
        assertEquals(true, ends(0x01, 0x02)) // central directory header
        assertEquals(true, ends(0x05, 0x06)) // end of central directory
        assertEquals(true, ends(0x06, 0x06)) // ZIP64 end of central directory
        assertEquals(true, ends(0x06, 0x08)) // archive extra data
        assertNull(ZipStreamReader(ByteArray(0).inputStream()).next())
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
}
