package com.pasich.encly.data.importer

import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.importer.ImportFixtures.describe
import com.pasich.encly.data.importer.ImportFixtures.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Real Notesnook exports (Markdown, Markdown with front matter, text, an encrypted backup) and an
 * Obsidian vault on disk, read the way the app reads them. Skipped while a fixture is absent.
 */
class NotesnookAndMarkdownFixturesTest {
    private fun local(day: Int, hour: Int, minute: Int) = LocalDateTime.of(2026, 10, day, hour, minute)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val meeting = listOf(
        "h1:Weekly sync",
        "h2:Agenda",
        "bullet:[ ] Release 2.2|[ ] Translations",
        "number:[ ] Review PRs|[ ] Ship",
        "quote:Decisions are final.",
        "separator",
        "text:Plain closing paragraph.",
    )
    private val groceries = listOf("check:[x] Milk|[ ] Eggs|[ ] Хліб 🍞")
    private val ideas = listOf("text:Перша думка.", "text:Друга думка.")

    private fun plan(name: String, forced: ImportSource? = null): ImportPlan {
        val zip = ImportFixtures.resource("notesnook/$name")
        assertNotNull("real export fixture missing", zip)
        return ImportFixtures.ready(source(name to zip!!), forced)
    }

    @Test
    fun notesnookMarkdownWithFrontMatter() {
        val plan = plan("notes-md-frontmatter.zip")
        assertEquals(ImportSource.NOTESNOOK, plan.preview.source)
        checkPlan(
            plan,
            listOf(
                ExpectedNote("Groceries", groceries, "home", local(14, 22, 47), local(14, 22, 48)),
                ExpectedNote(
                    "Meeting notes",
                    meeting,
                    "work",
                    local(14, 22, 48),
                    local(14, 22, 48),
                    losses = ImportLosses(extraTags = 1),
                ),
                ExpectedNote("Ідеї 💡", ideas, null, local(14, 22, 49), local(14, 22, 49)),
                ExpectedNote(
                    "Pinned reminder",
                    listOf("text:Water the plants."),
                    null,
                    local(14, 22, 50),
                    local(14, 22, 50),
                    losses = ImportLosses(pinned = 1),
                ),
                ExpectedNote(
                    "Archived recipe",
                    listOf("text:Flour, water, salt."),
                    null,
                    local(14, 22, 49),
                    local(14, 22, 49),
                    losses = ImportLosses(archived = 1),
                ),
            ),
        )
    }

    private fun withoutFrontMatter(name: String, meetingBlocks: List<String>, forced: ImportSource? = null) {
        val plan = plan(name, forced)
        checkPlan(
            plan,
            listOf(
                ExpectedNote("Groceries", groceries, null, ImportFixtures.NOW, ImportFixtures.NOW),
                ExpectedNote("Meeting notes", meetingBlocks, "Work", ImportFixtures.NOW, ImportFixtures.NOW),
                ExpectedNote("Ідеї 💡", ideas, null, ImportFixtures.NOW, ImportFixtures.NOW),
                ExpectedNote(
                    "Pinned reminder",
                    listOf("text:Water the plants."),
                    null,
                    ImportFixtures.NOW,
                    ImportFixtures.NOW,
                ),
                ExpectedNote(
                    "Archived recipe",
                    listOf("text:Flour, water, salt."),
                    null,
                    ImportFixtures.NOW,
                    ImportFixtures.NOW,
                    losses = ImportLosses(archived = 1),
                ),
            ),
        )
    }

    @Test
    fun notesnookPlainMarkdownIsReadAsMarkdownWithTheTitleFromItsFirstHeading() {
        withoutFrontMatter("notes-md.zip", meeting)
        assertEquals(ImportSource.MARKDOWN, plan("notes-md.zip").preview.source)
    }

    @Test
    fun notesnookPlainMarkdownChosenManuallyAsNotesnookGivesTheSameNotes() {
        withoutFrontMatter("notes-md.zip", meeting, forced = ImportSource.NOTESNOOK)
    }

    @Test
    fun notesnookTextExport() {
        val txtMeeting = listOf(
            "text:WEEKLY SYNC",
            "text:AGENDA",
            "bullet:[ ] Release 2.2|[ ] Translations",
            "number:[ ] Review PRs|[ ] Ship",
            "quote:Decisions are final.",
            "separator",
            "text:Plain closing paragraph.",
        )
        withoutFrontMatter("notes-txt.zip", txtMeeting)
    }

    @Test
    fun anEncryptedNotesnookBackupIsRefusedWithGuidance() {
        val zip = ImportFixtures.resource("notesnook/backup.nnbackupz")
        assertNotNull("real export fixture missing", zip)
        assertEquals(
            Analysis.Encrypted(ImportSource.NOTESNOOK),
            ImportFixtures.analyze(source("backup.nnbackupz" to zip!!)),
        )
        // Choosing a source by hand does not make it readable.
        assertEquals(
            Analysis.Encrypted(ImportSource.NOTESNOOK),
            ImportFixtures.analyze(source("backup.nnbackupz" to zip), ImportSource.NOTESNOOK),
        )
    }

    @Test
    fun anObsidianVaultFolder() {
        val tree = ImportFixtures.resourceTree("markdown/obsidian-vault")
        assertNotNull("real export fixture missing", tree)
        val plan = ImportFixtures.ready(tree!!)

        assertEquals(ImportSource.MARKDOWN, plan.preview.source)
        // .obsidian/ is skipped whole, the image is counted as an attachment, nothing is unsupported.
        assertEquals(0, plan.preview.unsupported + plan.preview.filesSkipped)
        checkPlan(
            plan,
            listOf(
                ExpectedNote("Groceries", groceries, "home", local(14, 22, 30), local(14, 22, 30)),
                ExpectedNote(
                    "Meeting notes",
                    listOf(
                        "h1:Weekly sync",
                        "h2:Agenda",
                        "bullet:[ ] Release 2.2|[ ] Translations|[ ] nested detail",
                        "number:[ ] Review PRs|[ ] Ship",
                        "quote:Decisions\nDecisions are final.",
                        "separator",
                        "text:Linked to Ідеї 💡 and the shopping list. #followup",
                    ),
                    "work",
                    ImportFixtures.NOW,
                    ImportFixtures.NOW,
                    losses = ImportLosses(extraTags = 1),
                ),
                ExpectedNote(
                    "Ідеї 💡",
                    listOf("text:Перша думка.", "text:Друга думка з **жирним** і `кодом`."),
                    null,
                    ImportFixtures.NOW,
                    ImportFixtures.NOW,
                ),
                ExpectedNote(
                    "Markor note",
                    listOf(
                        "text:Plain text note from Markor.\nSecond line of the same paragraph.",
                        "text:New paragraph.",
                    ),
                    null,
                    ImportFixtures.NOW,
                    ImportFixtures.NOW,
                ),
            ),
            attachments = 1,
        )
    }

    @Test
    fun theVaultFoldersTextIsStoredAsEnclyBlocksThatReadBack() {
        val tree = ImportFixtures.resourceTree("markdown/obsidian-vault")
        assertNotNull("real export fixture missing", tree)
        val plan = ImportFixtures.ready(tree!!)
        plan.payload.notes.forEach {
            assertEquals(it.title, describe(BlockConverter.jsonToBlocks(it.value)).isNotEmpty(), true)
        }
    }
}
