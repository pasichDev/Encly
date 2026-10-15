package com.pasich.encly.presentation.editor.state

import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The slash picker: which commands a query leaves, when it opens and closes, and what picking does. */
class SlashCommandsTest {
    private val editor = BlockEditorState { 0L }
    private val labels = mapOf(
        SlashCommand.TEXT to "Text",
        SlashCommand.H1 to "Heading 1",
        SlashCommand.H2 to "Heading 2",
        SlashCommand.H3 to "Heading 3",
        SlashCommand.H4 to "Heading 4",
        SlashCommand.CHECKLIST to "Checklist",
        SlashCommand.BULLETS to "Bulleted list",
        SlashCommand.NUMBERED to "Numbered list",
        SlashCommand.QUOTE to "Quote",
        SlashCommand.LINK to "Link",
        SlashCommand.DIVIDER to "Separator",
    )

    // --- filtering --------------------------------------------------------------------------------

    @Test
    fun anEmptyQueryOffersEveryBlockKindInOrder() {
        assertEquals(
            listOf(
                BlockType.TEXT, BlockType.H1, BlockType.H2, BlockType.H3, BlockType.H4, BlockType.LIST_CHECK,
                BlockType.LIST_BULLET, BlockType.LIST_NUMBER, BlockType.QUOTE, BlockType.LINK, BlockType.SEPARATOR,
            ),
            slashMatches("", labels).map { it.type },
        )
    }

    @Test
    fun aQueryMatchesTheStartOfAWordOfTheLabelOrAKeywordIgnoringCase() {
        assertEquals(
            listOf(SlashCommand.H1, SlashCommand.H2, SlashCommand.H3, SlashCommand.H4),
            slashMatches("HEAD", labels),
        )
        assertEquals(listOf(SlashCommand.H2), slashMatches("h2", labels))
        assertEquals(listOf(SlashCommand.BULLETS, SlashCommand.NUMBERED), slashMatches("list", labels))
        assertEquals(listOf(SlashCommand.DIVIDER), slashMatches("div", labels))
        assertEquals(listOf(SlashCommand.CHECKLIST), slashMatches("todo", labels))
        assertEquals(
            listOf(SlashCommand.BULLETS, SlashCommand.NUMBERED, SlashCommand.LINK, SlashCommand.DIVIDER),
            slashMatches("li", labels),
        )
        assertTrue(slashMatches("zzz", labels).isEmpty())
    }

    @Test
    fun matchingFollowsTheLocalizedLabelsToo() {
        val ukrainian = labels + (SlashCommand.QUOTE to "Цитата")
        assertEquals(listOf(SlashCommand.QUOTE), slashMatches("цит", ukrainian))
    }

    // --- opening and closing ----------------------------------------------------------------------

    @Test
    fun theMenuStaysHiddenUntilTheSlashIsCommitted() {
        val picker = SlashPicker().apply { setLabels(labels) }

        picker.open("b")
        assertFalse(picker.menu.value!!.isShown)
        picker.commit()

        assertTrue(picker.menu.value!!.isShown)
    }

    @Test
    fun theQueryFollowsTheTextAndASpaceOrANonMatchingQueryClosesIt() {
        val picker = SlashPicker().apply { setLabels(labels) }
        picker.open("b")
        picker.commit()

        picker.onText("b", "/he")
        assertEquals("he", picker.menu.value?.query)
        assertEquals(4, picker.menu.value?.matches?.size)

        picker.onText("b", "/zzz")
        assertNull(picker.menu.value)

        picker.open("b")
        picker.onText("b", "/head ")
        assertNull(picker.menu.value)
    }

    @Test
    fun deletingTheSlashClosesTheMenuAndAnotherParagraphIsIgnored() {
        val picker = SlashPicker().apply { setLabels(labels) }
        picker.open("b")

        picker.onText("other", "plain")
        assertNotNull(picker.menu.value)
        picker.onText("b", "")
        assertNull(picker.menu.value)
    }

    @Test
    fun theHighlightWrapsAroundAndResetsWithTheQuery() {
        val picker = SlashPicker().apply { setLabels(labels) }
        picker.open("b")
        picker.commit()

        picker.moveHighlight(-1)
        assertEquals(SlashCommand.DIVIDER, picker.menu.value?.highlightedCommand)
        picker.moveHighlight(1)
        assertEquals(SlashCommand.TEXT, picker.menu.value?.highlightedCommand)
        picker.moveHighlight(1)
        picker.onText("b", "/h")
        assertEquals(0, picker.menu.value?.highlighted)
    }

    @Test
    fun anyStructuralChangeOrUndoClosesThePicker() {
        val block = Block.TextBlock(MutableStateFlow("/"))
        editor.load(listOf(block))
        editor.slash.open(block.id)

        editor.addBlock(Block.QuoteBlock())

        assertNull(editor.slash.menu.value)
    }

    // --- picking a command ------------------------------------------------------------------------

    @Test
    fun pickingAHeadingReplacesTheParagraphWithAnEmptyHeadingInOneUndoStep() {
        val block = Block.TextBlock(MutableStateFlow("/h2"))
        editor.load(listOf(block))
        editor.slash.open(block.id)

        assertTrue(editor.applySlash(block.id, SlashCommand.H2))

        val heading = editor.blocks.single() as Block.HBlock
        assertEquals(BlockType.H2, heading.blockType)
        assertEquals("", heading.text.value)
        assertNull(editor.slash.menu.value)
        editor.undo()
        assertEquals("/h2", (editor.blocks.single() as Block.TextBlock).text.value)
        editor.redo()
        assertEquals(BlockType.H2, (editor.blocks.single() as Block.HBlock).blockType)
    }

    @Test
    fun everyListKindStartsWithOneEmptyItemInFocus() {
        listOf(
            SlashCommand.CHECKLIST to BlockType.LIST_CHECK,
            SlashCommand.BULLETS to BlockType.LIST_BULLET,
            SlashCommand.NUMBERED to BlockType.LIST_NUMBER,
        ).forEach { (command, type) ->
            val block = Block.TextBlock(MutableStateFlow("/x"))
            editor.load(listOf(block))

            editor.applySlash(block.id, command)

            val list = editor.blocks.single() as Block.ListBlock
            assertEquals(type, list.blockType)
            assertEquals(listOf(""), list.items.value.map { it.value })
        }
    }

    @Test
    fun quoteAndLinkReplaceTheParagraph() {
        val quote = Block.TextBlock(MutableStateFlow("/quo"))
        editor.load(listOf(quote))
        editor.applySlash(quote.id, SlashCommand.QUOTE)
        assertTrue(editor.blocks.single() is Block.QuoteBlock)

        val link = Block.TextBlock(MutableStateFlow("/link"))
        editor.load(listOf(link))
        editor.applySlash(link.id, SlashCommand.LINK)
        assertTrue(editor.blocks.single() is Block.LinkBlock)
    }

    @Test
    fun pickingTextOnlyClearsTheQuery() {
        val block = Block.TextBlock(MutableStateFlow("/te"))
        editor.load(listOf(block))

        editor.applySlash(block.id, SlashCommand.TEXT)

        assertSame(block, editor.blocks.single())
        assertEquals("", block.text.value)
        editor.undo()
        assertEquals("/te", block.text.value)
    }

    @Test
    fun aDividerIsFollowedByAParagraphToGoOnIn() {
        val block = Block.TextBlock(MutableStateFlow("/div"))
        editor.load(listOf(block))

        editor.applySlash(block.id, SlashCommand.DIVIDER)

        assertEquals(listOf("SeparatorBlock", "TextBlock"), editor.blocks.map { it::class.simpleName })
        assertEquals("", (editor.blocks[1] as Block.TextBlock).text.value)
        editor.undo()
        assertEquals("/div", (editor.blocks.single() as Block.TextBlock).text.value)
    }

    @Test
    fun onlyAParagraphCanBeConverted() {
        val heading = Block.HBlock(MutableStateFlow("Title"), BlockType.H1)
        editor.load(listOf(heading))

        assertFalse(editor.applySlash(heading.id, SlashCommand.QUOTE))
        assertFalse(editor.applySlash("gone", SlashCommand.QUOTE))
        assertEquals("Title", heading.text.value)
    }
}
