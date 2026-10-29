package com.pasich.encly.ios

import com.pasich.encly.data.export.keepFileNameCharacters
import com.pasich.encly.data.export.normalizeNfc
import com.pasich.encly.data.export.utf8Prefix
import com.pasich.encly.data.zip.decodeCp437
import com.pasich.encly.data.zip.newCrc32
import com.pasich.encly.data.zip.openZipFile
import com.pasich.encly.data.zip.zipWriter
import com.pasich.encly.ios.storage.IosKeyValueStore
import com.pasich.encly.utils.LocalizedDate
import com.pasich.encly.utils.formatLocalized
import kotlinx.datetime.TimeZone
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The iOS actuals that only run on the Simulator: the ZIP seam over zlib, dates, Unicode and the flags file. */
class IosActualsTest {
    private val scratch = mutableListOf<Path>()

    private fun tempPath(suffix: String): Path =
        Path(NSTemporaryDirectory().trimEnd('/') + "/encly-" + NSUUID().UUIDString + suffix).also { scratch += it }

    @AfterTest
    fun cleanUp() = scratch.forEach { SystemFileSystem.delete(it, mustExist = false) }

    @Test
    fun crc32MatchesTheCheckValue() {
        val crc = newCrc32()
        val bytes = "123456789".encodeToByteArray()
        crc.update(bytes, 0, bytes.size)
        assertEquals(0xCBF43926L, crc.value)
    }

    @Test
    fun cp437DecodesTheHighHalf() {
        assertEquals("aÇü ", decodeCp437(byteArrayOf(0x61, 0x80.toByte(), 0x81.toByte(), 0xFF.toByte())))
    }

    @Test
    fun zipWrittenHereReadsBack() {
        val text = "hello hello hello hello".encodeToByteArray()
        val big = ByteArray(100_000) { (it % 7).toByte() }
        val out = Buffer()
        val writer = zipWriter(out) { 1_700_000_000_000L }
        writer.put("a.txt", null, text)
        writer.put("dir/ü.md", 1_600_000_000_000L, big)
        writer.put("empty", null, ByteArray(0))
        writer.finish()
        val file = tempPath(".zip")
        SystemFileSystem.sink(file).buffered().use { it.write(out, out.size) }

        openZipFile(file).use { zip ->
            assertEquals(3, zip.entryCount)
            assertContentEquals(text, read(zip, "a.txt"))
            assertContentEquals(big, read(zip, "dir/ü.md"))
            assertContentEquals(ByteArray(0), read(zip, "empty"))
            assertEquals(big.size.toLong(), zip.find("dir/ü.md")?.declaredSize)
            assertNull(zip.find("missing"))
        }
    }

    @Test
    fun aFileThatIsNotAZipIsAnIoError() {
        val file = tempPath(".zip")
        SystemFileSystem.sink(file).buffered().use { it.write("not a zip at all, just some text".encodeToByteArray()) }
        val failed = runCatching { openZipFile(file) }.exceptionOrNull()
        assertNotNull(failed)
        assertTrue(failed is kotlinx.io.IOException)
    }

    @Test
    fun timeIsFormattedInTheGivenZoneAndLanguage() {
        val noon = Instant.fromEpochMilliseconds(12 * 3_600_000L)
        val text = formatLocalized(noon, LocalizedDate.TIME, "en-US", TimeZone.UTC)
        assertTrue(text.startsWith("12:00"), text)
        val day = formatLocalized(noon, LocalizedDate.DAY_MONTH, "en-US", TimeZone.UTC)
        assertTrue(day.contains("Jan") && day.contains("1"), day)
    }

    @Test
    fun fileNameRulesFollowTheJvmOnes() {
        assertEquals("é", normalizeNfc("é"))
        assertEquals("abc d", keepFileNameCharacters("a/b:c\u0000 d"))
        assertEquals("aé", utf8Prefix("aé€", 3))
        assertEquals("😀", utf8Prefix("😀x", 4))
    }

    @Test
    fun flagsSurviveARestart() {
        val file = tempPath(".json").toString()
        val store = IosKeyValueStore(file)
        assertTrue(
            store.commit {
                putString("a", "1")
                putBoolean("b", true)
                putLong("c", 7L)
            },
        )
        val reopened = IosKeyValueStore(file)
        assertEquals("1", reopened.getString("a", null))
        assertTrue(reopened.getBoolean("b", false))
        assertEquals(7L, reopened.getLong("c", 0L))
        reopened.commit { remove("a") }
        assertFalse(IosKeyValueStore(file).contains("a"))
    }

    private fun read(zip: com.pasich.encly.data.zip.ZipEntrySource, name: String): ByteArray {
        val entry = assertNotNull(zip.find(name))
        return entry.open().buffered().use { it.readByteArray() }
    }
}
