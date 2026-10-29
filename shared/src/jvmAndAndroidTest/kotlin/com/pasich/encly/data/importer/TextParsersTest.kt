package com.pasich.encly.data.importer

import com.pasich.encly.data.importer.ImportFixtures.describe
import com.pasich.encly.data.importer.ImportFixtures.entry
import com.pasich.encly.data.importer.ImportFixtures.parse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardNotesParserTest {
    private fun backup(vararg items: String, extra: String = "") =
        """{"version":"004","items":[${items.joinToString(",")}]$extra}"""

    private fun note(
        uuid: String,
        content: String,
        created: String = "2024-05-01T10:00:00.000Z",
        updated: String = "1970-01-01T00:00:00.000Z",
    ) = """{"uuid":"$uuid","content_type":"Note","created_at":"$created","updated_at":"$updated",""" +
        """"content":{$content}}"""

    private fun tag(title: String, vararg notes: String) =
        """{"uuid":"t-$title","content_type":"Tag","content":{"title":"$title","references":[${notes.joinToString(",") {
            """{"uuid":"$it","content_type":"Note"}"""
        }}]}}"""

    private fun run(json: String) =
        parse(ImportSource.STANDARD_NOTES, entry("Standard Notes Backup and Import File.txt", json))

    @Test
    fun mapsNotesTagsTrashAndTheClientEditTime() {
        val parsed = run(
            backup(
                note(
                    "n1",
                    """"title":"Plan","text":"- [x] a\n- [ ] b","noteType":"plain-text","trashed":true,""" +
                        """"appData":{"org.standardnotes.sn":{"client_updated_at":"2024-05-02T11:00:00.000Z",""" +
                        """"pinned":true,"archived":true}}""",
                ),
                tag("work", "n1"),
                tag("home", "n1"),
                """{"uuid":"p","content_type":"SN|UserPreferences","content":{}}""",
            ),
        )
        val n = parsed.notes.single()
        assertEquals("n1", n.sourceKey)
        assertEquals("Plan", n.title)
        assertEquals(listOf("check:[x] a|[ ] b"), describe(n.blocks))
        assertEquals(listOf("home", "work"), n.tags)
        assertTrue(n.trashed)
        assertEquals(1_714_647_600_000L, n.edited)
        assertEquals(1_714_557_600_000L, n.created)
        assertEquals(ImportLosses(pinned = 1, archived = 1, extraTags = 1), n.losses)
    }

    @Test
    fun anEpochZeroUpdateIsNeverImported() {
        val n = run(backup(note("n", """"title":"x","text":"y""""))).notes.single()
        assertNull(n.edited)
    }

    @Test
    fun readsPinnedAndArchivedFromTheTopLevelAsAFallback() {
        val n = run(backup(note("n", """"title":"x","text":"y","pinned":true"""))).notes.single()
        assertEquals(1, n.losses.pinned)
    }

    @Test
    fun superNotesAreReadFromTheirLexicalText() {
        val lexical = """{"root":{"type":"root","children":[
            {"type":"heading","tag":"h2","children":[{"type":"text","text":"Head"}]},
            {"type":"paragraph","children":[{"type":"text","text":"one "},{"type":"linebreak"},
              {"type":"text","text":"two"}]},
            {"type":"list","listType":"check","children":[
              {"type":"listitem","checked":true,"children":[{"type":"text","text":"done"}]},
              {"type":"listitem","checked":false,"children":[{"type":"text","text":"todo"}]}]},
            {"type":"list","listType":"bullet","children":[
              {"type":"listitem","children":[{"type":"text","text":"a"}]},
              {"type":"listitem","children":[{"type":"list","listType":"bullet","children":[
                {"type":"listitem","children":[{"type":"text","text":"nested"}]}]}]}]},
            {"type":"quote","children":[{"type":"text","text":"q"}]},
            {"type":"horizontalrule"}]}}"""
        val text = lexical.replace("\n", "").replace("\"", "\\\"")
        val n = run(backup(note("s", """"title":"Super","text":"$text","noteType":"super""""))).notes.single()
        assertEquals(
            listOf(
                "h2:Head",
                "text:one \ntwo",
                "check:[x] done|[ ] todo",
                "bullet:[ ] a|[ ] nested",
                "quote:q",
                "separator",
            ),
            describe(n.blocks),
        )
    }

    @Test
    fun superNoteCodeTablesAndCollapsiblesKeepTheirLayoutInsteadOfOneBlockPerToken() {
        val lexical = """{"root":{"type":"root","children":[
            {"type":"code","language":"js","children":[
              {"type":"code-highlight","text":"const","highlightType":"keyword"},
              {"type":"code-highlight","text":" x "},{"type":"code-highlight","text":"="},
              {"type":"code-highlight","text":" 1"},{"type":"linebreak"},{"type":"tab"},
              {"type":"code-highlight","text":"f()"}]},
            {"type":"collapsible-container","children":[
              {"type":"collapsible-title","children":[{"type":"text","text":"Title"}]},
              {"type":"collapsible-content","children":[
                {"type":"paragraph","children":[{"type":"text","text":"body"}]}]}]},
            {"type":"table","children":[{"type":"tablerow","children":[
              {"type":"tablecell","children":[{"type":"paragraph","children":[{"type":"text","text":"a"}]}]},
              {"type":"tablecell","children":[{"type":"paragraph","children":[{"type":"text","text":"b"}]}]}]}]}
            ]}}"""
        val text = lexical.replace("\n", "").replace("\"", "\\\"")
        val n = run(backup(note("s", """"title":"Super","text":"$text","noteType":"super""""))).notes.single()
        assertEquals(listOf("text:const x = 1\n\tf()", "text:Title", "text:body", "text:a | b"), describe(n.blocks))
    }

    @Test
    fun aSuperNoteThatIsNotLexicalFallsBackToItsPreviewThenItsText() {
        val withPreview = run(
            backup(note("s", """"title":"a","text":"{broken","noteType":"super","preview_plain":"preview"""")),
        ).notes.single()
        assertEquals(listOf("text:preview"), describe(withPreview.blocks))
        val plain = run(backup(note("s", """"title":"a","text":"raw text","noteType":"super""""))).notes.single()
        assertEquals(listOf("text:raw text"), describe(plain.blocks))
    }

    @Test
    fun otherTypesAreParagraphsAndRichTextLosesItsHtml() {
        val code = run(backup(note("c", """"title":"c","text":"a\n\nb","noteType":"code""""))).notes.single()
        assertEquals(listOf("text:a", "text:b"), describe(code.blocks))
        val rich = run(
            backup(note("r", """"title":"r","text":"<p>Hi <b>there</b></p>","noteType":"rich-text"""")),
        ).notes.single()
        assertEquals(listOf("text:Hi there"), describe(rich.blocks))
    }

    @Test
    fun markdownNotesGetBlocks() {
        val n = run(backup(note("m", """"title":"m","text":"# H\n\n1. a","noteType":"markdown""""))).notes.single()
        assertEquals(listOf("h1:H", "number:[ ] a"), describe(n.blocks))
    }

    @Test
    fun anEncryptedBackupIsNeverRead() {
        val parsed =
            run(
                backup(
                    note("n", """"title":"x"""").replace(""""content":{""", """"content_x":{"""),
                    extra = ""","keyParams":{}""",
                ),
            )
        assertTrue(parsed.notes.isEmpty())
        assertEquals(1, parsed.skips.unsupported)
    }
}

class JoplinParserTest {
    private val folder = "0123456789abcdef0123456789abcdef"
    private val noteId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val tagId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

    private fun meta(id: String, type: Int, vararg more: String) =
        (listOf("id: $id") + more + "type_: $type").joinToString("\n")

    @Test
    fun parsesTheMetadataBlockFromTheBottom() {
        val item = JoplinParser.item(
            "Title\n\nbody line\n\nsecond paragraph\n\n" +
                meta(noteId, 1, "parent_id: $folder", "latitude: 0.0", "user_data: "),
        )!!
        assertEquals("Title", item.title)
        assertEquals("body line\n\nsecond paragraph", item.body)
        assertEquals(noteId, item.id)
        assertEquals("", item.props["user_data"])
        assertEquals(1, item.type)
    }

    @Test
    fun aNoteWithAnEmptyBodyAndALinkWithNoHead() {
        assertEquals("", JoplinParser.item("Title\n\n\n\n" + meta(noteId, 1))!!.body)
        val link = JoplinParser.item(meta("c".repeat(32), 6, "note_id: $noteId", "tag_id: $tagId"))!!
        assertEquals(6, link.type)
        assertEquals(tagId, link.props["tag_id"])
    }

    @Test
    fun textWithoutAValidBlockIsNotJoplin() {
        assertNull(JoplinParser.item("Title\n\nbody"))
        assertNull(JoplinParser.item("Title\n\nid: short\ntype_: 1"))
        assertNull(JoplinParser.item("Title\n\nid: $noteId\ntype_: x"))
        assertNull(JoplinParser.item("Title\n\nid: $noteId\nnot a property\ntype_: 1"))
    }

    @Test
    fun linksTagsAndNotebooksBecomeTheNotesTag() {
        val parsed = ImportFixtures.parse(
            ImportSource.JOPLIN,
            ImportFixtures.entry("f.md", "Personal\n\n" + meta(folder, 2)),
            ImportFixtures.entry("t1.md", "zeta\n\n" + meta(tagId, 5)),
            ImportFixtures.entry("t2.md", "alpha\n\n" + meta("d".repeat(32), 5)),
            ImportFixtures.entry("l1.md", meta("e".repeat(32), 6, "note_id: $noteId", "tag_id: $tagId")),
            ImportFixtures.entry("l2.md", meta("f".repeat(32), 6, "note_id: $noteId", "tag_id: ${"d".repeat(32)}")),
            ImportFixtures.entry(
                "n.md",
                "Shopping\n\n- [x] milk\n\n" + meta(
                    noteId,
                    1,
                    "parent_id: $folder",
                    "created_time: 2024-01-02T03:04:05.678Z",
                    "updated_time: 2024-01-03T00:00:00.000Z",
                    "user_updated_time: 2024-01-04T00:00:00.000Z",
                    "is_todo: 1",
                    "deleted_time: 1704067200000",
                ),
            ),
            ImportFixtures.entry(
                "n2.md",
                "Plain\n\nbody\n\n" + meta("1".repeat(32), 1, "parent_id: $folder", "deleted_time: 0"),
            ),
            ImportFixtures.entry("r.md", "pic.png\n\n" + meta("2".repeat(32), 4)),
            ImportFixtures.entry("readme.md", "just a readme"),
        )
        assertEquals(2, parsed.notes.size)
        val shopping = parsed.notes.first { it.title == "Shopping" }
        assertEquals(listOf("alpha", "zeta"), shopping.tags)
        assertEquals(1, shopping.losses.extraTags)
        assertEquals(1, shopping.losses.todos)
        assertTrue(shopping.trashed)
        assertEquals(1_704_326_400_000L, shopping.edited)
        assertEquals(1_704_164_645_678L, shopping.created)
        assertEquals(listOf("check:[x] milk"), describe(shopping.blocks))
        val plain = parsed.notes.first { it.title == "Plain" }
        assertEquals(listOf("Personal"), plain.tags)
        assertFalse(plain.trashed)
        assertEquals(1, parsed.extraLosses.attachments)
        assertEquals(1, parsed.skips.unsupported)
    }

    private fun describe(blocks: List<com.pasich.encly.editor.model.Block>) = ImportFixtures.describe(blocks)
}

class MarkdownNotesParserTest {
    private fun localMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        java.time.LocalDateTime.of(year, month, day, hour, minute).atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    private fun generic(vararg e: ImportEntry) = parse(ImportSource.MARKDOWN, *e)

    private fun notesnook(vararg e: ImportEntry) = parse(ImportSource.NOTESNOOK, *e)

    @Test
    fun theFileNameIsTheTitleAndOnlyARepeatedHeadingIsDropped() {
        val kept = generic(entry("Plan.md", "# Weekly sync\n\ntext")).notes.single()
        assertEquals("Plan", kept.title)
        assertEquals(listOf("h1:Weekly sync", "text:text"), describe(kept.blocks))
        val dropped = generic(entry("Plan.md", "# Plan\n\ntext")).notes.single()
        assertEquals(listOf("text:text"), describe(dropped.blocks))
    }

    @Test
    fun frontMatterGivesTitleTagsAndDates() {
        val note = generic(
            entry(
                "x.md",
                "---\ntitle: Real title\ntags:\n  - b\n  - '#a'\ncreated: 2024-03-01 10:00:00Z\n" +
                    "updated: 2024-03-02\n---\nbody",
            ),
        ).notes.single()
        assertEquals("Real title", note.title)
        assertEquals(listOf("b", "a"), note.tags)
        assertEquals(1_709_287_200_000L, note.created)
        assertEquals(localMillis(2024, 3, 2, 0, 0), note.edited)
        assertEquals(listOf("text:body"), describe(note.blocks))
    }

    @Test
    fun theFirstFolderIsTheTagWhenThereAreNoTags() {
        val notes = generic(entry("Vault/Ideas/x.md", "a"), entry("y.txt", "b")).notes
        assertEquals(listOf("Vault"), notes[0].tags)
        assertEquals(emptyList<String>(), notes[1].tags)
    }

    @Test
    fun anEmptyFileKeepsItsTitle() {
        val note = generic(entry("Empty.md", "")).notes.single()
        assertEquals("Empty", note.title)
        assertTrue(note.blocks.isEmpty())
    }

    @Test
    fun invalidUtf8IsSkippedAndReported() {
        val parsed = generic(ImportEntry("bad.md", byteArrayOf(0xc3.toByte(), 0x28)), entry("ok.md", "fine"))
        assertEquals(1, parsed.notes.size)
        assertEquals(1, parsed.skips.unreadable)
    }

    @Test
    fun notesnookFrontMatterFlagsFoldersAndAttachments() {
        val parsed = notesnook(
            entry(
                "Work/Plan.md",
                "---\ntitle: \"Plan \\\"A\\\"\"\ncreated_at: 2024-01-01T00:00:00Z\n" +
                    "updated_at: 2024-01-02T00:00:00Z\npinned: true\ncolor: red\ntags: x, y\n---\n\n" +
                    "# Plan \"A\"\n\n- [x] done\n",
            ),
            entry("_archive/Work/Old.md", "old text"),
            entry("Work/attachments/pic.png", "x"),
            entry("Trip.txt", "Trip\n\nintro\n\n✅ Passport\n☐ Tickets\n☐ Charger\nbye"),
        )
        assertEquals(3, parsed.notes.size)
        val plan = parsed.notes.first { it.title == "Plan \"A\"" }
        assertEquals(listOf("x", "y"), plan.tags)
        assertEquals(listOf("check:[x] done"), describe(plan.blocks))
        assertEquals(ImportLosses(pinned = 1, colors = 1, extraTags = 1), plan.losses)
        assertEquals(1_704_067_200_000L, plan.created)
        val old = parsed.notes.first { it.title == "Old" }
        assertEquals(listOf("Work"), old.tags)
        assertEquals(1, old.losses.archived)
        val trip = parsed.notes.first { it.title == "Trip" }
        assertEquals(
            listOf("text:intro", "check:[x] Passport|[ ] Tickets|[ ] Charger", "text:bye"),
            describe(trip.blocks),
        )
        assertEquals(1, parsed.extraLosses.attachments)
    }
}
