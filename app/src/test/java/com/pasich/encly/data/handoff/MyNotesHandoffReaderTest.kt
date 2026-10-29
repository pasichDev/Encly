package com.pasich.encly.data.handoff

import com.pasich.encly.data.handoff.HandoffFixtures.handoff
import com.pasich.encly.data.handoff.HandoffFixtures.handoffZip
import com.pasich.encly.data.handoff.HandoffFixtures.note
import com.pasich.encly.data.handoff.HandoffFixtures.read
import com.pasich.encly.data.handoff.HandoffFixtures.tag
import com.pasich.encly.data.handoff.HandoffFixtures.task
import com.pasich.encly.data.handoff.HandoffFixtures.zip
import com.pasich.encly.data.zip.ZipEntryHandle
import com.pasich.encly.data.zip.ZipEntrySource
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MyNotesHandoffReaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dir: File get() = temp.root

    @Test
    fun readsAValidHandoff() {
        val parsed = read(
            dir,
            handoff(
                tags = listOf(tag("t1", "Work")),
                notes = listOf(note("n1", tag = "Work", isPinned = true, attachments = 2)),
                tasks = listOf(task("k1", "Call")),
            ),
        )
        assertEquals(1, parsed.schema)
        assertEquals("Work", parsed.tags.single().name)
        assertEquals("Work", parsed.notes.single().tag)
        assertEquals(2, parsed.notes.single().attachments)
        assertEquals("Call", parsed.tasks.single().description)
    }

    @Test
    fun unknownKeysAreIgnoredAndOmittedNullsAreNull() {
        val noteWithoutNulls = JsonObject(note("n1") - "valueJson" - "tag" + ("color" to JsonPrimitive("red")))
        val json = JsonObject(handoff(notes = listOf(noteWithoutNulls)) + ("future" to JsonPrimitive(true)))
        val parsed = read(dir, json)
        assertNull(parsed.notes.single().valueJson)
        assertNull(parsed.notes.single().tag)
    }

    @Test
    fun aNewerSchemaAsksForAnUpdate() {
        assertError(HandoffError.UNSUPPORTED_SCHEMA) { read(dir, handoff(schema = 2)) }
    }

    @Test
    fun aNewerSchemaIsReportedEvenIfItsShapeChanged() {
        val v2 = buildJsonObject {
            put("format", MyNotesHandoff.FORMAT)
            put("schema", 2)
            put("notes", "a different shape")
        }
        assertError(HandoffError.UNSUPPORTED_SCHEMA) { read(dir, v2) }
    }

    @Test
    fun wrongFormatOrSchemaIsInvalid() {
        assertError(HandoffError.INVALID_PAYLOAD) { read(dir, handoff(format = "encly-backup")) }
        assertError(HandoffError.INVALID_PAYLOAD) { read(dir, handoff(schema = 0)) }
        assertError(HandoffError.INVALID_PAYLOAD) { read(dir, JsonObject(handoff() - "schema")) }
    }

    @Test
    fun malformedJsonIsInvalid() {
        val broken = zip(dir, MyNotesHandoffReader.ENTRY_NAME to "{\"format\": \"mynotes-handoff\", ".toByteArray())
        assertError(HandoffError.INVALID_PAYLOAD) { MyNotesHandoffReader.read(broken) }
    }

    @Test
    fun aMissingRequiredFieldOrWrongTypeIsInvalid() {
        val untitled = JsonObject(note("n1") - "title")
        assertError(HandoffError.INVALID_PAYLOAD) { read(dir, handoff(notes = listOf(untitled))) }
        val stringFlag = JsonObject(note("n1") + ("isTrash" to JsonPrimitive("yes")))
        assertError(HandoffError.INVALID_PAYLOAD) { read(dir, handoff(notes = listOf(stringFlag))) }
    }

    @Test
    fun duplicateOrBlankIdsAndUnknownCategoriesAreInvalid() {
        assertError(HandoffError.INVALID_PAYLOAD) { read(dir, handoff(notes = listOf(note("n1"), note("n1")))) }
        assertError(HandoffError.INVALID_PAYLOAD) { read(dir, handoff(notes = listOf(note(" ")))) }
        assertError(HandoffError.INVALID_PAYLOAD) {
            read(dir, handoff(tasks = listOf(task("k1", "Call", categoryId = "missing"))))
        }
        assertError(HandoffError.INVALID_PAYLOAD) { read(dir, handoff(notes = listOf(note("n1", attachments = -1)))) }
    }

    @Test
    fun notAZipIsInvalid() {
        val file = File(dir, "plain.zip").apply { writeText("not a zip at all") }
        assertError(HandoffError.INVALID_PAYLOAD) { MyNotesHandoffReader.read(file) }
    }

    @Test
    fun theZipMustHoldExactlyHandoffJson() {
        val json = handoff().toString().toByteArray()
        assertError(HandoffError.INVALID_PAYLOAD) { MyNotesHandoffReader.read(zip(dir, "other.json" to json)) }
        assertError(HandoffError.INVALID_PAYLOAD) {
            MyNotesHandoffReader.read(zip(dir, MyNotesHandoffReader.ENTRY_NAME to json, "extra.bin" to byteArrayOf(1)))
        }
        assertError(HandoffError.INVALID_PAYLOAD) { MyNotesHandoffReader.read(zip(dir)) }
        assertError(HandoffError.INVALID_PAYLOAD) {
            MyNotesHandoffReader.read(zip(dir, "../${MyNotesHandoffReader.ENTRY_NAME}" to json))
        }
    }

    @Test
    fun deeplyNestedUnknownKeysDoNotOverflow() {
        val deep = "[".repeat(200_000) + "]".repeat(200_000)
        val json = handoff().toString().replaceFirst("{", "{\"future\":$deep,")
        val file = zip(dir, MyNotesHandoffReader.ENTRY_NAME to json.toByteArray())
        assertEquals(1, MyNotesHandoffReader.read(file).schema)
    }

    @Test
    fun aZipBombStopsAtTheUncompressedLimit() {
        // A few KB compressed, 8 MB of spaces uncompressed: valid JSON padding, so only the limit stops it.
        val padded = handoff().toString().replaceFirst("{", "{" + " ".repeat(8 * 1024 * 1024))
        val bomb = zip(dir, MyNotesHandoffReader.ENTRY_NAME to padded.toByteArray())
        val limits = HandoffLimits(maxJsonBytes = 1024L * 1024)
        assertEquals(true, bomb.length() < limits.maxJsonBytes)
        assertError(HandoffError.TOO_LARGE) { MyNotesHandoffReader.read(bomb, limits) }
    }

    @Test
    fun anOversizedZipIsRefusedBeforeReading() {
        val file = handoffZip(dir, handoff(notes = listOf(note("n1"))))
        assertError(HandoffError.TOO_LARGE) { MyNotesHandoffReader.read(file, HandoffLimits(maxZipBytes = 10)) }
    }

    @Test
    fun tooManyRecordsOrTooLongTextIsTooLarge() {
        val three = handoff(notes = listOf(note("a"), note("b"), note("c")))
        assertError(HandoffError.TOO_LARGE) {
            MyNotesHandoffReader.read(handoffZip(dir, three), HandoffLimits(maxRecords = 2))
        }
        val long = handoff(notes = listOf(note("a", value = "x".repeat(101))))
        assertError(HandoffError.TOO_LARGE) {
            MyNotesHandoffReader.read(handoffZip(dir, long), HandoffLimits(maxTextChars = 100))
        }
        val longName = handoff(tags = listOf(tag("t", "n".repeat(11))))
        assertError(HandoffError.TOO_LARGE) {
            MyNotesHandoffReader.read(handoffZip(dir, longName), HandoffLimits(maxNameChars = 10))
        }
    }

    @Test
    fun copyStopsAtTheZipLimit() {
        val endless = object : RawSource {
            override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
                sink.write(ByteArray(byteCount.toInt()))
                return byteCount
            }

            override fun close() = Unit
        }
        assertError(HandoffError.TOO_LARGE) {
            MyNotesHandoffReader.copyLimited(endless, Buffer(), HandoffLimits(maxZipBytes = 4096))
        }
        val out = Buffer()
        MyNotesHandoffReader.copyLimited(
            Buffer().apply {
                write(ByteArray(4096))
            },
            out,
            HandoffLimits(maxZipBytes = 4096),
        )
        assertEquals(4096, out.size)
    }

    @Test
    fun malformedUtf8InsideAStringIsReplacedNotRejected() {
        val json = handoff(notes = listOf(note("n1", title = "QB"))).toString().toByteArray()
        // The title's "Q" becomes a lone continuation byte, which is not UTF-8.
        json[json.indexOf('Q'.code.toByte())] = 0x80.toByte()
        val title = MyNotesHandoffReader.read(zip(dir, MyNotesHandoffReader.ENTRY_NAME to json)).notes.single().title
        assertEquals(true, title.endsWith("B") && '\uFFFD' in title)
    }

    // The checks that need a hostile archive are driven through the platform-free seam.

    private class FakeArchive(
        private val entries: List<Triple<String, Boolean, Long>>,
        private val data: ByteArray = ByteArray(0),
    ) : ZipEntrySource {
        override val entryCount: Int get() = entries.size

        override fun find(name: String): ZipEntryHandle? = entries.firstOrNull { it.first == name }?.let { entry ->
            object : ZipEntryHandle {
                override val name = entry.first
                override val isDirectory = entry.second
                override val declaredSize = entry.third

                override fun open(): RawSource = Buffer().apply { write(data) }
            }
        }

        override fun close() = Unit
    }

    private fun readFake(archive: FakeArchive, zipBytes: Long = 100, limits: HandoffLimits = HandoffLimits()) =
        MyNotesHandoffReader.read(zipBytes, limits) { archive }

    @Test
    fun aDirectoryNamedHandoffJsonIsInvalid() {
        val archive = FakeArchive(listOf(Triple(MyNotesHandoffReader.ENTRY_NAME, true, 0L)))
        assertError(HandoffError.INVALID_PAYLOAD) { readFake(archive) }
    }

    @Test
    fun aDeclaredSizeOverTheLimitIsTooLargeWithoutReading() {
        val archive = FakeArchive(listOf(Triple(MyNotesHandoffReader.ENTRY_NAME, false, 2048L)))
        assertError(HandoffError.TOO_LARGE) { readFake(archive, limits = HandoffLimits(maxJsonBytes = 1024)) }
    }

    @Test
    fun aDeclaredSizeThatLiesStillStopsAtTheStreamLimit() {
        val json = handoff().toString().toByteArray()
        val archive = FakeArchive(listOf(Triple(MyNotesHandoffReader.ENTRY_NAME, false, 1L)), json)
        assertError(HandoffError.TOO_LARGE) { readFake(archive, limits = HandoffLimits(maxJsonBytes = json.size - 1L)) }
        assertEquals(1, readFake(archive, limits = HandoffLimits(maxJsonBytes = json.size.toLong())).schema)
    }

    @Test
    fun twoEntriesOrNoneAreInvalid() {
        val two = FakeArchive(listOf(Triple(MyNotesHandoffReader.ENTRY_NAME, false, 1L), Triple("x", false, 1L)))
        assertError(HandoffError.INVALID_PAYLOAD) { readFake(two) }
        assertError(HandoffError.INVALID_PAYLOAD) { readFake(FakeArchive(emptyList())) }
    }

    @Test
    fun theZipLengthIsCheckedBeforeTheArchiveIsOpened() {
        assertError(HandoffError.TOO_LARGE) {
            MyNotesHandoffReader.read(11, HandoffLimits(maxZipBytes = 10)) { error("opened") }
        }
    }
}

internal fun assertError(expected: HandoffError, block: () -> Unit) {
    try {
        block()
        fail("expected $expected")
    } catch (e: HandoffException) {
        assertEquals(expected, e.error)
    }
}
