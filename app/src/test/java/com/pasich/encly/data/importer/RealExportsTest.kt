package com.pasich.encly.data.importer

import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.importer.ImportFixtures.describe
import com.pasich.encly.data.importer.ImportFixtures.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

/**
 * The parsers against exports produced by the real apps (src/test/resources/import-fixtures,
 * synthetic notes). Each test is skipped, not failed, while its fixture is absent.
 */
class RealExportsTest {
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    // Google Keep: Takeout ZIP -------------------------------------------------------------

    private val keepExpected: List<ExpectedNote> by lazy {
        val prefix = "Encly test · "
        listOf(
            ExpectedNote(
                prefix + "Ідеї 💡",
                listOf("text:Перша думка.", "text:Друга думка."),
                "encly-test",
                1792006880674,
                1792006922502,
            ),
            ExpectedNote(
                prefix + "Groceries",
                listOf("check:[ ] Eggs|[ ] Хліб 🍞|[x] Milk"),
                "encly-home",
                1792006977756,
                1792006990486,
                losses = ImportLosses(extraTags = 1),
            ),
            ExpectedNote(
                prefix + "Pinned reminder",
                listOf("text:Water the plants."),
                "encly-test",
                1792007023776,
                1792007030520,
                losses = ImportLosses(pinned = 1),
            ),
            ExpectedNote(
                prefix + "Archived recipe",
                listOf("text:Flour, water, salt."),
                "encly-test",
                1792007046736,
                1792007049509,
                losses = ImportLosses(archived = 1),
            ),
            ExpectedNote(
                prefix + "Old draft",
                listOf("text:Delete me."),
                "encly-test",
                1792007063708,
                1792007063708,
                trashed = true,
            ),
        )
    }

    @Test
    fun googleKeepTakeoutZip() {
        val zip = ImportFixtures.resource("keep/takeout-keep.zip")
        assertNotNull("real export fixture missing", zip)
        val plan = ImportFixtures.ready(source("takeout-keep.zip" to zip!!))
        assertEquals(ImportSource.GOOGLE_KEEP, plan.preview.source)
        assertEquals(5, plan.preview.notes)
        assertEquals(1, plan.preview.checklists)
        assertEquals(1, plan.preview.trashed)
        // Labels.txt lists only the labels of the notes; a label no note uses is never created.
        assertEquals(
            listOf("encly-home", "encly-test"),
            plan.payload.tags.map { it.name }.sorted().filter {
                it ==
                    "encly-home" ||
                    it == "encly-test"
            },
        )
        checkPlan(plan, keepExpected)
    }

    @Test
    fun googleKeepLooseJsonFilesAreTheSameAsTheZip() {
        val zip = ImportFixtures.resource("keep/takeout-keep.zip")
        assertNotNull("real export fixture missing", zip)
        val fromZip = ImportFixtures.ready(source("takeout-keep.zip" to zip!!))
        val entries = ImportFixtures.read(source("takeout-keep.zip" to zip)).entries.filter { it.extension == "json" }
        val loose = ImportFixtures.ready(source(*entries.map { it.name to it.bytes }.toTypedArray()))
        assertEquals(fromZip.payload.notes.map { it.title }.sorted(), loose.payload.notes.map { it.title }.sorted())
        // The same notes are the same notes whichever way they were picked: a second import adds nothing.
        assertEquals(fromZip.payload.notes.map { it.uid }.toSet(), loose.payload.notes.map { it.uid }.toSet())
    }

    // Standard Notes: decrypted backup ZIP -------------------------------------------------

    @Test
    fun standardNotesDecryptedBackupZip() {
        val zip = ImportFixtures.resource("standard-notes/Standard Notes Backup - decrypted.zip")
        assertNotNull("real export fixture missing", zip)
        val plan = ImportFixtures.ready(source("backup.zip" to zip!!))
        assertEquals(ImportSource.STANDARD_NOTES, plan.preview.source)
        assertEquals(6, plan.preview.notes)
        // Only tags a note uses are created (the third has to be unused by the single-tag rule).
        assertEquals(2, plan.payload.tags.size)
        assertEquals(1, plan.preview.trashed)
        assertEquals(1, plan.preview.losses.pinned)
        assertEquals(1, plan.preview.losses.archived)
        // Offline exports have updated_at = 1970: the edit time is the client's.
        val groceries = plan.payload.notes.first { it.title == "Groceries" }
        assertEquals(ms("2026-10-14T19:37:36.178Z"), groceries.date)
        assertEquals(ms("2026-10-14T19:36:52.378Z"), groceries.dateCreate)
        assertEquals(
            listOf("check:[x] Milk|[ ] Eggs|[ ] Хліб 🍞"),
            describe(BlockConverter.jsonToBlocks(groceries.value)),
        )
        val meeting = plan.payload.notes.first { it.title == "Meeting notes" }
        assertEquals(
            listOf(
                "h1:Weekly sync",
                "h2:Agenda",
                "bullet:[ ] Release 2.2|[ ] Translations",
                "number:[ ] Review PRs|[ ] Ship",
                "quote:Decisions are final.",
                "separator",
                "text:Plain closing paragraph.",
            ),
            describe(BlockConverter.jsonToBlocks(meeting.value)),
        )
        val tagNames = plan.payload.tags.associate { it.uid to it.name }
        assertEquals("home", tagNames[groceries.tagUid])
        assertEquals("important", tagNames[meeting.tagUid])
        val ideas = plan.payload.notes.first { it.title == "Ідеї 💡" }
        assertEquals(
            listOf("text:Перша думка.", "text:Друга думка."),
            describe(BlockConverter.jsonToBlocks(ideas.value)),
        )
        assertEquals(true, plan.payload.notes.first { it.title == "Old draft" }.isTrash)
        // The Items/ copies are not imported a second time.
        assertEquals(6, plan.payload.notes.size)
    }

    // Joplin ---------------------------------------------------------------------------------

    private val joplinExpected: List<ExpectedNote> by lazy {
        fun t(s: String) = ms(s)
        listOf(
            ExpectedNote(
                "Groceries",
                listOf("check:[x] Milk|[ ] Eggs|[ ] Хліб 🍞"),
                "home",
                t("2026-10-14T19:32:48.892Z"),
                t("2026-10-14T19:32:48.892Z"),
            ),
            ExpectedNote(
                "Ідеї 💡",
                listOf("text:Перша думка.", "text:Друга думка з **жирним** текстом."),
                "Personal",
                t("2026-10-14T19:32:50.617Z"),
                t("2026-10-14T19:32:50.617Z"),
            ),
            ExpectedNote(
                "Meeting notes",
                listOf(
                    "h1:Weekly sync",
                    "h2:Agenda",
                    "bullet:[ ] Release 2.2|[ ] Translations",
                    "number:[ ] Review PRs|[ ] Ship",
                    "quote:Decisions are final.",
                    "separator",
                    "text:Plain closing paragraph.",
                ),
                "important",
                t("2026-10-14T19:32:53.807Z"),
                t("2026-10-14T19:32:53.807Z"),
                losses = ImportLosses(extraTags = 1),
            ),
            ExpectedNote(
                "Call the bank",
                emptyList(),
                "Work",
                t("2026-10-14T19:32:55.989Z"),
                t("2026-10-14T19:32:56.506Z"),
                losses = ImportLosses(todos = 1),
            ),
            ExpectedNote(
                "Renew passport",
                emptyList(),
                "Work",
                t("2026-10-14T19:32:57.013Z"),
                t("2026-10-14T19:32:57.013Z"),
                losses = ImportLosses(todos = 1),
            ),
        )
    }

    private fun joplinPlan(source: ImportFileSource): ImportPlan {
        val plan = ImportFixtures.ready(source)
        assertEquals(5, plan.preview.notes)
        return plan
    }

    @Test
    fun joplinJexArchive() {
        val jex = ImportFixtures.resource("joplin/notes.jex")
        assertNotNull("real export fixture missing", jex)
        val plan = joplinPlan(source("notes.jex" to jex!!))
        assertEquals(ImportSource.JOPLIN, plan.preview.source)
        checkPlan(plan, joplinExpected)
        assertEquals(0, plan.preview.unsupported)
    }

    @Test
    fun joplinRawFolder() {
        val tree = ImportFixtures.resourceTree("joplin/raw")
        assertNotNull("real export fixture missing", tree)
        val plan = joplinPlan(tree!!)
        assertEquals(ImportSource.JOPLIN, plan.preview.source)
        checkPlan(plan, joplinExpected)
        // Same items in the JEX and in the folder: the same uids, so one import covers both.
        val jex = ImportFixtures.resource("joplin/notes.jex")
        assertNotNull("real export fixture missing", jex)
        val fromJex = joplinPlan(source("notes.jex" to jex!!))
        assertEquals(fromJex.payload.notes.map { it.uid }.toSet(), plan.payload.notes.map { it.uid }.toSet())
    }

    @Test
    fun joplinMarkdownExportIsGenericMarkdown() {
        val tree = ImportFixtures.resourceTree("joplin/md")
        assertNotNull("real export fixture missing", tree)
        val plan = joplinPlan(tree!!)
        assertEquals(ImportSource.MARKDOWN, plan.preview.source)
        // The Markdown export has no front matter: the file name is the title, the folder the tag.
        val titles = plan.payload.notes.map { it.title }.sorted()
        assertEquals(
            listOf("Call the bank", "Groceries", "Meeting notes", "Renew passport", "Ідеї 💡").sorted(),
            titles,
        )
        val groceries = plan.payload.notes.first { it.title == "Groceries" }
        assertEquals(
            listOf("check:[x] Milk|[ ] Eggs|[ ] Хліб 🍞"),
            describe(BlockConverter.jsonToBlocks(groceries.value)),
        )
        assertEquals(setOf("Personal", "Work"), plan.payload.tags.map { it.name }.toSet())
    }

    @Test
    fun joplinMarkdownWithFrontMatterKeepsTagsAndDates() {
        val tree = ImportFixtures.resourceTree("joplin/md_frontmatter")
        assertNotNull("real export fixture missing", tree)
        val plan = joplinPlan(tree!!)
        assertEquals(ImportSource.MARKDOWN, plan.preview.source)
        val tagNames = plan.payload.tags.associate { it.uid to it.name }
        val groceries = plan.payload.notes.first { it.title == "Groceries" }
        assertEquals("home", tagNames[groceries.tagUid])
        assertEquals(ms("2026-10-14T19:32:48Z"), groceries.dateCreate)
        val meeting = plan.payload.notes.first { it.title == "Meeting notes" }
        assertEquals("important", tagNames[meeting.tagUid])
        assertEquals(ms("2026-10-14T19:32:53Z"), meeting.date)
        val ideas = plan.payload.notes.first { it.title == "Ідеї 💡" }
        assertEquals(
            listOf("text:Перша думка.", "text:Друга думка з **жирним** текстом."),
            describe(BlockConverter.jsonToBlocks(ideas.value)),
        )
        // Notes without tags get their folder as the tag.
        assertEquals("Work", tagNames[plan.payload.notes.first { it.title == "Call the bank" }.tagUid])
    }
}
