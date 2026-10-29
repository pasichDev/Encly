package com.pasich.encly.data.importer

import com.pasich.encly.data.importer.ImportFixtures.describe
import com.pasich.encly.data.importer.ImportFixtures.entry
import com.pasich.encly.data.importer.ImportFixtures.parse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepParserTest {
    private fun keep(extra: String, created: Long = 1_700_000_000_123_456, edited: Long = 1_700_000_100_000_000) =
        """{"createdTimestampUsec":$created,"userEditedTimestampUsec":$edited,$extra}"""

    private fun one(json: String): ImportedNote =
        parse(ImportSource.GOOGLE_KEEP, entry("Takeout/Keep/n.json", json)).notes.single()

    @Test
    fun mapsTextTitleLabelAndMicrosecondTimestamps() {
        val note =
            one(keep(""""title":"Plan","textContent":"first\n\nsecond line\nthird","labels":[{"name":"Work"}]"""))
        assertEquals("Plan", note.title)
        assertEquals(listOf("text:first", "text:second line\nthird"), describe(note.blocks))
        assertEquals(listOf("Work"), note.tags)
        assertEquals(1_700_000_000_123L, note.created)
        assertEquals(1_700_000_100_000L, note.edited)
        assertFalse(note.trashed)
        assertEquals("n.json|1700000000123456", note.sourceKey)
    }

    @Test
    fun aChecklistKeepsItsItemsAndStateEvenWithoutText() {
        val note = one(
            keep(
                """"title":"Shop","listContent":[{"text":"Milk","isChecked":true},""" +
                    """{"text":"Eggs","isChecked":false},{"text":"  ","isChecked":false}]""",
            ),
        )
        assertEquals(listOf("check:[x] Milk|[ ] Eggs"), describe(note.blocks))
    }

    @Test
    fun textAndChecklistTogether() {
        val note = one(keep(""""textContent":"intro","listContent":[{"text":"a","isChecked":false}]"""))
        assertEquals(listOf("text:intro", "check:[ ] a"), describe(note.blocks))
    }

    @Test
    fun trashedPinnedArchivedColourAttachmentsAndExtraLabelsAreCounted() {
        val note = one(
            keep(
                """"title":"x","textContent":"y","isTrashed":true,"isPinned":true,"isArchived":true,"color":"RED",
                |"attachments":[{"filePath":"a.jpg"},{"filePath":"b.jpg"}],
                |"labels":[{"name":"A"},{"name":"B"},{"name":"a"}]
                """.trimMargin(),
            ),
        )
        assertTrue(note.trashed)
        assertEquals(ImportLosses(attachments = 2, pinned = 1, archived = 1, colors = 1, extraTags = 1), note.losses)
        assertEquals(listOf("A", "B"), note.tags)
    }

    @Test
    fun theDefaultColourIsNoLoss() {
        assertEquals(ImportLosses(), one(keep(""""title":"x","color":"DEFAULT"""")).losses)
    }

    @Test
    fun badTimestampsAreNotImported() {
        val negative = one(keep(""""title":"x"""", created = -5, edited = 0))
        assertNull(negative.created)
        assertNull(negative.edited)
        val overflow = one(keep(""""title":"x"""", created = Long.MAX_VALUE, edited = 9_000_000_000_000_000_000))
        assertNull(overflow.created)
        assertNull(overflow.edited)
    }

    @Test
    fun anEmptyNoteIsSkippedAndCounted() {
        val parsed = parse(ImportSource.GOOGLE_KEEP, entry("a.json", keep("\"title\":\"\",\"textContent\":\"\"")))
        assertTrue(parsed.notes.isEmpty())
        assertEquals(1, parsed.skips.empty)
    }

    @Test
    fun htmlDuplicatesMediaAndLabelsFileAreIgnoredAndOtherJsonIsUnsupported() {
        val parsed = parse(
            ImportSource.GOOGLE_KEEP,
            entry("Takeout/Keep/n.html", "<p>x</p>"),
            entry("Takeout/Keep/Labels.txt", "A"),
            entry("Takeout/Keep/n.json", keep(""""title":"T"""")),
            entry("Takeout/Keep/other.json", """{"a":1}"""),
        )
        assertEquals(1, parsed.notes.size)
        assertEquals(1, parsed.skips.unsupported)
    }

    @Test
    fun invalidUtf8IsSkippedAndReported() {
        val parsed = parse(
            ImportSource.GOOGLE_KEEP,
            ImportEntry("bad.json", byteArrayOf(0x7b, 0xff.toByte(), 0x7d)),
            entry("ok.json", keep(""""title":"T"""")),
        )
        assertEquals(1, parsed.notes.size)
        assertEquals(1, parsed.skips.unreadable)
    }

    @Test
    fun tooManyNotesIsTooLarge() {
        val limits = ImportLimits(maxNotes = 2)
        val entries = (1..3).map { entry("n$it.json", keep(""""title":"T$it"""")) }
        val error = org.junit.Assert.assertThrows(ImportException::class.java) { KeepParser.parse(entries, limits) }
        assertEquals(ImportError.TOO_LARGE, error.error)
    }

    @Test
    fun aVeryDeeplyNestedJsonIsNotParsed() {
        val deep = "[".repeat(5000) + "]".repeat(5000)
        val parsed = parse(ImportSource.GOOGLE_KEEP, entry("deep.json", deep))
        assertTrue(parsed.notes.isEmpty())
        assertEquals(1, parsed.skips.unsupported)
    }
}
