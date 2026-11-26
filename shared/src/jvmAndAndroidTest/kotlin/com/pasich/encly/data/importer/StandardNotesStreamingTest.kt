package com.pasich.encly.data.importer

import com.pasich.encly.data.importer.ImportFixtures.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Standard Notes backup is read an item at a time; what that must not change, and the limits it keeps. */
class StandardNotesStreamingTest {
    private val name = "Standard Notes Backup and Import File.txt"

    private fun note(n: Int, text: String = "text $n") =
        """{"uuid":"n$n","content_type":"Note","created_at":"2024-05-01T10:00:00.000Z",""" +
            """"updated_at":"1970-01-01T00:00:00.000Z",""" +
            """"content":{"title":"Note $n","text":"$text","noteType":"plain-text"}}"""

    private fun tag(title: String, vararg uuids: String) =
        """{"uuid":"t-$title","content_type":"Tag","content":{"title":"$title","references":[""" +
            uuids.joinToString(",") { """{"uuid":"$it","content_type":"Note"}""" } + "]}}"

    private fun backup(items: List<String>, tail: String = "") =
        """{"version":"004","items":[${items.joinToString(",")}]$tail}"""

    private fun detect(json: String) = FormatDetector.detect(listOf(entry("b.json", json)))

    private fun parseBackup(json: String, limits: ImportLimits = ImportLimits()) =
        StandardNotesParser.parse(listOf(entry(name, json)), limits)

    @Test
    fun aBackupOfThousandsOfNotesKeepsTheirOrderAndTags() {
        val notes = (1..3000).map { note(it) }
        val tags = listOf(tag("odd", *(1..3000 step 2).map { "n$it" }.toTypedArray()), tag("first", "n1"))
        val parsed = parseBackup(backup(tags + notes))

        assertEquals((1..3000).map { "Note $it" }, parsed.notes.map { it.title })
        assertEquals(listOf("first", "odd"), parsed.notes[0].tags)
        assertEquals(emptyList<String>(), parsed.notes[1].tags)
        assertEquals(listOf("odd"), parsed.notes[2].tags)
    }

    @Test
    fun aTagThatComesAfterItsNotesStillTagsThem() {
        val parsed = parseBackup(backup(listOf(note(1), note(2), tag("late", "n2"))))
        assertEquals(listOf(emptyList<String>(), listOf("late")), parsed.notes.map { it.tags })
    }

    @Test
    fun keyParamsAfterTheItemsStillMakeTheBackupEncrypted() {
        val json = backup(listOf(note(1)), tail = ""","keyParams":{"identifier":"a"}""")
        assertEquals(Detection.Encrypted(ImportSource.STANDARD_NOTES), detect(json))
        val parsed = parseBackup(json)
        assertTrue(parsed.notes.isEmpty())
        assertEquals(1, parsed.skips.unsupported)
    }

    @Test
    fun anEncryptedItemLateInTheFileMakesTheWholeBackupEncrypted() {
        val locked = """{"uuid":"x","content_type":"Note","content":"004:abc"}"""
        val json = backup((1..50).map { note(it) } + locked)
        assertEquals(Detection.Encrypted(ImportSource.STANDARD_NOTES), detect(json))
        assertTrue(parseBackup(json).notes.isEmpty())
    }

    @Test
    fun aMalformedTailDropsEveryNoteOfThatFileOnly() {
        val broken = backup((1..100).map { note(it) }).dropLast(40)
        val good = backup(listOf(note(500)))
        val parsed = StandardNotesParser.parse(listOf(entry(name, broken), entry("two.json", good)), ImportLimits())

        assertEquals(listOf("Note 500"), parsed.notes.map { it.title })
        assertEquals(1, parsed.skips.unsupported)
    }

    @Test
    fun aBadItemMidwayFailsTheFileNotJustTheItem() {
        val json = """{"version":"004","items":[${note(1)},{"content_type":"Note",,},${note(3)}]}"""
        val parsed = parseBackup(json)
        assertTrue(parsed.notes.isEmpty())
        assertEquals(1, parsed.skips.unsupported)
    }

    @Test
    fun invalidUtf8LateInALargeBackupIsUnreadableNotPartlyImported() {
        val head = backup((1..2000).map { note(it) }).toByteArray(Charsets.UTF_8)
        val bytes = head.copyOf(head.size + 1).also { it[head.size] = 0xff.toByte() }
        val parsed = StandardNotesParser.parse(listOf(ImportEntry(name, bytes)), ImportLimits())

        assertTrue(parsed.notes.isEmpty())
        assertEquals(1, parsed.skips.unreadable)
        val kinds = FormatDetector.classify(listOf(ImportEntry("b.json", bytes)), ImportLimits())
        assertEquals(EntryKind.UNREADABLE, kinds.single())
    }

    @Test
    fun nestingDeeperThanTheLimitIsRefusedWhileReading() {
        val deep = "[".repeat(10) + "]".repeat(10)
        val item = """{"uuid":"d","content_type":"Note","content":{"title":"T","text":"x","deep":$deep}}"""
        val limits = ImportLimits(maxJsonDepth = 8)
        val parsed = StandardNotesParser.parse(listOf(entry(name, backup(listOf(item)))), limits)
        assertTrue(parsed.notes.isEmpty())
        assertEquals(1, parsed.skips.unsupported)
        assertEquals(
            EntryKind.UNSUPPORTED,
            FormatDetector.classify(listOf(entry("b.json", backup(listOf(item)))), limits).single(),
        )
        // Within the limit the same note is read.
        assertEquals(1, parseBackup(backup(listOf(item))).notes.size)
    }

    @Test
    fun moreNotesThanTheLimitStopTheReadWithTooLarge() {
        val limits = ImportLimits(maxNotes = 5)
        val error = assertThrows(ImportException::class.java) {
            parseBackup(backup((1..6).map { note(it) }), limits)
        }
        assertEquals(ImportError.TOO_LARGE, error.error)
        assertEquals(5, parseBackup(backup((1..5).map { note(it) }), limits).notes.size)
    }

    @Test
    fun blankNotesDoNotCountAgainstTheNoteLimit() {
        val blank = """{"uuid":"b","content_type":"Note","content":{"title":"","text":""}}"""
        val limits = ImportLimits(maxNotes = 2)
        val parsed = parseBackup(backup(List(10) { blank } + note(1)), limits)
        assertEquals(1, parsed.notes.size)
        assertEquals(10, parsed.skips.empty)
    }
}
