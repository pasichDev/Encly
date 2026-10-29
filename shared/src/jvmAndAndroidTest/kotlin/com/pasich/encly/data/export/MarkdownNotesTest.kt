package com.pasich.encly.data.export

import com.pasich.encly.data.importer.Frontmatter
import com.pasich.encly.data.model.Note
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownNotesTest {

    private val labels = MarkdownLabels("Untitled", "Cannot read", "Tasks", emptyMap())

    private fun render(note: Note, tag: String? = null) = MarkdownNotes.render(note, tag, labels)

    @Test
    fun timesKeepTheirMillisecondsAndDropNothingElse() {
        val markdown = render(
            Note(title = "Plan", value = "[]", date = 1_700_000_000_120, dateCreate = 1_700_000_000_500),
        )

        assertTrue(markdown, markdown.contains("created: 2023-11-14T22:13:20.500Z\n"))
        assertTrue(markdown, markdown.contains("updated: 2023-11-14T22:13:20.120Z\n"))
    }

    @Test
    fun theFrontMatterCarriesTheNoteDetails() {
        val markdown = render(
            Note(
                title = "Plan",
                value = "[]",
                description = "short",
                date = 1_700_000_000_000,
                dateCreate = 1_690_000_000_000,
                isPinned = true,
                isArchived = true,
                color = "teal",
            ),
            tag = "Work",
        )

        assertEquals(
            "---\n" +
                "title: \"Plan\"\n" +
                "tags: [\"Work\"]\n" +
                "created: 2023-07-22T04:26:40Z\n" +
                "updated: 2023-11-14T22:13:20Z\n" +
                "description: \"short\"\n" +
                "pinned: true\n" +
                "archived: true\n" +
                "color: \"teal\"\n" +
                "---\n\n" +
                "# Plan\n",
            markdown,
        )
    }

    @Test
    fun aPlainNoteHasOnlyWhatItHas() {
        val markdown = render(Note(title = "Plain", value = "[]"))

        assertEquals("---\ntitle: \"Plain\"\n---\n\n# Plain\n", markdown)
    }

    @Test
    fun aTitleCannotBreakOutOfTheFrontMatter() {
        val markdown = render(Note(title = "a\"b\nkey: x\n---\n# injected", value = "[]"), tag = "t\"]\npinned: true")

        val doc = Frontmatter.split(markdown)
        assertEquals("a\"b\nkey: x\n---\n# injected", doc.value("title"))
        assertFalse(doc.has("key"))
        assertFalse(doc.flag("pinned"))
        assertEquals(2, markdown.lines().count { it == "---" || it == "..." })
    }

    @Test
    fun theFileReadsBackThroughEnclysOwnFrontMatterReader() {
        val doc = Frontmatter.split(
            render(Note(title = "Back: and forth", value = "[]", date = 1_700_000_000_000), tag = "Home"),
        )

        assertEquals("Back: and forth", doc.value("title"))
        assertEquals(listOf("Home"), doc.list("tags"))
        assertEquals("2023-11-14T22:13:20Z", doc.value("updated"))
    }

    @Test
    fun aNoteWithoutATitleIsCalledUntitledButGetsNoHeading() {
        val markdown = render(Note(value = "[]"))

        assertTrue(markdown.startsWith("---\ntitle: \"Untitled\"\n"))
        assertFalse(markdown.contains("# "))
    }

    @Test
    fun aNoteThatCannotBeReadSaysSoInsteadOfBeingEmpty() {
        val markdown = render(Note(title = "Broken", value = "[{\"blockType\":\"FUTURE\"}]"))

        assertTrue(markdown.endsWith("# Broken\n\nCannot read\n"))
    }

    // --- the body alone, for "Copy as Markdown" ---------------------------------------------------

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    @Test
    fun theBodyIsTheTitleAsAHeadingThenTheBlocks() {
        val blocks = listOf(
            Block.HBlock(MutableStateFlow("Part"), BlockType.H2),
            text("Hello"),
            Block.ListBlock(
                MutableStateFlow(listOf(ItemListBlock("done", isCheck = true), ItemListBlock("todo"))),
                BlockType.LIST_CHECK,
            ),
        )

        assertEquals(
            "# Plan\n\n## Part\n\nHello\n\n- [x] done\n- [ ] todo\n",
            MarkdownNotes.renderBody("  Plan ", blocks),
        )
    }

    @Test
    fun anEmptyTitleLeavesOnlyTheBlocks() {
        assertEquals("Hello\n", MarkdownNotes.renderBody("  ", listOf(text("Hello"))))
    }

    @Test
    fun aTitleWithoutBlocksIsJustTheHeadingAndNothingAtAllIsEmpty() {
        assertEquals("# Plan\n", MarkdownNotes.renderBody("Plan", emptyList()))
        assertEquals("", MarkdownNotes.renderBody("", emptyList()))
    }

    @Test
    fun theBodyIsWhatTheFileHoldsAfterItsFrontMatter() {
        val note = Note(title = "Plan", value = "[]")
        assertEquals(
            "# Plan\n",
            MarkdownNotes.render(note, null, labels).substringAfter("---\n\n"),
        )
        assertEquals("# Plan\n", MarkdownNotes.renderBody(note.title, emptyList()))
    }
}
