package com.pasich.encly.presentation.editor.state

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Enter, Backspace, paste and typed marks: how text moves between blocks, and that undo takes it back. */
class BlockEditingTest {
    private var now = 0L
    private val editor = BlockEditorState { now }

    private val enter = LineBreak("", listOf("", ""), "")

    private fun enterAt(text: String, cursor: Int) = LineBreak(text.take(cursor), listOf("", ""), text.drop(cursor))

    private fun paste(text: String, cursor: Int, pasted: String) =
        LineBreak(text.take(cursor), pasted.split('\n'), text.drop(cursor))

    // --- Enter ----------------------------------------------------------------------------------

    @Test
    fun enterSplitsAParagraphAtTheCursorKeepingTheTextAfterIt() {
        val block = text("Hello world")
        editor.load(listOf(block))

        val kept = editor.breakLine(block.id, enterAt("Hello world", 5))

        assertEquals("Hello", kept)
        assertEquals(listOf("Hello", " world"), contents())
        assertTrue(editor.blocks[1] is Block.TextBlock)
        assertFocus(1, caret = 0)
    }

    @Test
    fun oneUndoTakesBackASplit() {
        val block = text("Hello world")
        editor.load(listOf(block))
        editor.breakLine(block.id, enterAt("Hello world", 5))

        editor.undo()

        assertEquals(listOf("Hello world"), contents())
        editor.redo()
        assertEquals(listOf("Hello", " world"), contents())
    }

    @Test
    fun enterAtTheEndOfAHeadingOrQuoteGoesOnInAParagraph() {
        val heading = Block.HBlock(MutableStateFlow("Plan"), blockType = BlockType.H2)
        val quote = Block.QuoteBlock(MutableStateFlow("Sleep on it."))
        editor.load(listOf(heading, quote))

        editor.breakLine(heading.id, enterAt("Plan", 4))
        editor.breakLine(quote.id, enterAt("Sleep on it.", 12))

        assertEquals(listOf("Plan", "", "Sleep on it.", ""), contents())
        assertTrue(editor.blocks[0] is Block.HBlock)
        assertTrue(editor.blocks[1] is Block.TextBlock)
        assertTrue(editor.blocks[2] is Block.QuoteBlock)
        assertTrue(editor.blocks[3] is Block.TextBlock)
    }

    @Test
    fun enterInAHeadingMovesTheTextAfterTheCursorToAParagraph() {
        val heading = Block.HBlock(MutableStateFlow("Before the visit"), blockType = BlockType.H1)
        editor.load(listOf(heading))

        editor.breakLine(heading.id, enterAt("Before the visit", 6))

        assertEquals(listOf("Before", " the visit"), contents())
        assertTrue(editor.blocks[1] is Block.TextBlock)
    }

    @Test
    fun enterAtTheStartOfAHeadingOpensAParagraphAboveIt() {
        val heading = Block.HBlock(MutableStateFlow("Plan"), blockType = BlockType.H1)
        editor.load(listOf(heading))

        val kept = editor.breakLine(heading.id, enterAt("Plan", 0))

        assertEquals("Plan", kept)
        assertEquals(listOf("", "Plan"), contents())
        assertSame(heading, editor.blocks[1])
        assertFocus(1, caret = 0)
    }

    @Test
    fun enterInAnEmptyHeadingTurnsItIntoAParagraph() {
        val heading = Block.HBlock(blockType = BlockType.H3)
        editor.load(listOf(heading))

        editor.breakLine(heading.id, enter)

        assertEquals(1, editor.blocks.size)
        assertTrue(editor.blocks.single() is Block.TextBlock)
    }

    // --- paste ----------------------------------------------------------------------------------

    @Test
    fun pastedLinesBecomeParagraphsAndTheTextAfterTheCursorFollowsTheLastOne() {
        val block = text("Start end")
        editor.load(listOf(block))

        editor.breakLine(block.id, paste("Start end", 6, "one\n\ntwo\nthree "))

        assertEquals(listOf("Start one", "two", "three end"), contents())
        assertFocus(2, caret = "three ".length)
        editor.undo()
        assertEquals(listOf("Start end"), contents())
    }

    @Test
    fun marksInPastedTextBecomeBlocksAndListLinesOneList() {
        val block = Block.TextBlock()
        editor.load(listOf(block))

        editor.breakLine(block.id, paste("", 0, "# Trip\n- milk\n- eggs\n---\nhttps://example.com\n> Pack light"))

        val types = editor.blocks.map { it::class.simpleName }
        assertEquals(
            listOf("HBlock", "ListBlock", "SeparatorBlock", "LinkBlock", "QuoteBlock"),
            types,
        )
        val list = editor.blocks[1] as Block.ListBlock
        assertEquals(listOf("milk", "eggs"), list.items.value.map { it.value })
        assertEquals("https://example.com", (editor.blocks[3] as Block.LinkBlock).block.value.url)
        editor.undo()
        assertEquals(listOf(""), contents())
    }

    @Test
    fun aPasteEndingInALineBreakGoesOnInANewParagraph() {
        val block = text("Note")
        editor.load(listOf(block))

        editor.breakLine(block.id, paste("Note", 4, " one\n"))

        assertEquals(listOf("Note one", ""), contents())
        assertFocus(1, caret = null)
    }

    @Test
    fun linesPastedIntoAListItemBecomeItemsWithoutTheirOwnMarks() {
        val list = list(BlockType.LIST_BULLET, "milk")
        editor.load(listOf(list))
        val item = list.items.value.single()

        editor.listLineBreak(list.id, item.id, paste("milk", 4, "\n- eggs\n[x] flour"))

        assertEquals(listOf("milk", "eggs", "flour"), list.items.value.map { it.value })
        assertTrue(list.items.value.last().isCheck)
    }

    @Test
    fun aLoneAddressPastedIntoAnEmptyParagraphBecomesALinkAndUndoGivesTheTextBack() {
        val block = Block.TextBlock()
        editor.load(listOf(block))

        editor.pasteLink(
            block.id,
            "https://example.com/a",
            LinkDataBlock(title = "example.com", url = "https://example.com/a"),
        )

        assertTrue(editor.blocks[0] is Block.LinkBlock)
        assertTrue(editor.blocks[1] is Block.TextBlock)
        editor.undo()
        assertEquals(listOf("https://example.com/a"), contents())
    }

    // --- Backspace ------------------------------------------------------------------------------

    @Test
    fun backspaceAtTheStartJoinsAParagraphToTheOneAboveKeepingItsText() {
        val first = text("Hello")
        val second = text(" world")
        editor.load(listOf(first, second))

        assertTrue(editor.backspaceAtStart(second.id))

        assertEquals(listOf("Hello world"), contents())
        assertFocus(0, caret = 5)
        editor.undo()
        assertEquals(listOf("Hello", " world"), contents())
    }

    @Test
    fun backspaceAtTheStartJoinsAParagraphToTheLastItemOfAList() {
        val list = list(BlockType.LIST_CHECK, "milk", "eggs")
        val paragraph = text(" and flour")
        editor.load(listOf(list, paragraph))

        editor.backspaceAtStart(paragraph.id)

        assertEquals(1, editor.blocks.size)
        assertEquals(listOf("milk", "eggs and flour"), list.items.value.map { it.value })
    }

    @Test
    fun backspaceAtTheStartOfAHeadingFirstMakesItAParagraph() {
        val paragraph = text("Intro")
        val heading = Block.HBlock(MutableStateFlow("Plan"), blockType = BlockType.H2)
        editor.load(listOf(paragraph, heading))

        editor.backspaceAtStart(heading.id)

        assertEquals(listOf("Intro", "Plan"), contents())
        assertTrue(editor.blocks[1] is Block.TextBlock)
    }

    @Test
    fun backspaceAfterASeparatorRemovesTheSeparator() {
        val first = text("One")
        val second = text("Two")
        editor.load(listOf(first, Block.SeparatorBlock(), second))

        editor.backspaceAtStart(second.id)

        assertEquals(listOf("One", "Two"), contents())
    }

    @Test
    fun backspaceInTheFirstBlockOrAnEmptyParagraphIsLeftToTheCaller() {
        val first = text("One")
        val empty = Block.TextBlock()
        editor.load(listOf(first, empty))

        assertFalse(editor.backspaceAtStart(first.id))
        assertFalse(editor.backspaceAtStart(empty.id))
        assertEquals(2, editor.blocks.size)
    }

    // --- lists ----------------------------------------------------------------------------------

    @Test
    fun enterSplitsAListItemAtTheCursor() {
        val list = list(BlockType.LIST_BULLET, "milk eggs")
        editor.load(listOf(list))
        val item = list.items.value.single()

        val kept = editor.listLineBreak(list.id, item.id, enterAt("milk eggs", 4))

        assertEquals("milk", kept)
        assertEquals(listOf("milk", " eggs"), list.items.value.map { it.value })
        val focus = editor.lastFocus()
        assertEquals(list.items.value[1].id, focus.itemId)
        assertEquals(0, focus.caret)
    }

    @Test
    fun enterInAnEmptyLastItemLeavesTheListForAParagraph() {
        val list = list(BlockType.LIST_NUMBER, "one", "")
        editor.load(listOf(list))

        editor.listLineBreak(list.id, list.items.value[1].id, enter)

        assertEquals(listOf("one"), list.items.value.map { it.value })
        assertTrue(editor.blocks[1] is Block.TextBlock)
        assertFocus(1, caret = 0)
    }

    @Test
    fun enterInAnEmptyMiddleItemSplitsTheListAroundAParagraph() {
        val list = list(BlockType.LIST_NUMBER, "one", "", "three")
        editor.load(listOf(list))

        editor.listLineBreak(list.id, list.items.value[1].id, enter)

        assertEquals(3, editor.blocks.size)
        assertEquals(listOf("one"), (editor.blocks[0] as Block.ListBlock).items.value.map { it.value })
        assertTrue(editor.blocks[1] is Block.TextBlock)
        val after = editor.blocks[2] as Block.ListBlock
        assertEquals(BlockType.LIST_NUMBER, after.blockType)
        assertEquals(listOf("three"), after.items.value.map { it.value })
        editor.undo()
        assertEquals(1, editor.blocks.size)
        assertEquals(listOf("one", "", "three"), list.items.value.map { it.value })
    }

    @Test
    fun enterInTheOnlyEmptyItemTurnsTheListIntoAParagraph() {
        val list = list(BlockType.LIST_CHECK, "")
        editor.load(listOf(list))

        editor.listLineBreak(list.id, list.items.value.single().id, enter)

        assertTrue(editor.blocks.single() is Block.TextBlock)
    }

    @Test
    fun backspaceAtTheStartOfTheFirstItemMakesItAParagraphAboveTheList() {
        val list = list(BlockType.LIST_BULLET, "milk", "eggs")
        editor.load(listOf(list))

        editor.listBackspaceAtStart(list.id, list.items.value[0].id)

        assertEquals("milk", (editor.blocks[0] as Block.TextBlock).text.value)
        assertEquals(listOf("eggs"), list.items.value.map { it.value })
    }

    @Test
    fun backspaceOnTheOnlyItemTurnsTheListBackIntoAParagraph() {
        val list = list(BlockType.LIST_CHECK, "")
        editor.load(listOf(list))

        editor.listBackspaceAtStart(list.id, list.items.value.single().id)

        assertTrue(editor.blocks.single() is Block.TextBlock)
    }

    @Test
    fun backspaceAtTheStartOfALaterItemJoinsItToTheItemAbove() {
        val list = list(BlockType.LIST_BULLET, "milk", " and eggs", "flour")
        editor.load(listOf(list))
        val first = list.items.value[0]

        editor.listBackspaceAtStart(list.id, list.items.value[1].id)

        assertEquals(listOf("milk and eggs", "flour"), list.items.value.map { it.value })
        val focus = editor.lastFocus()
        assertEquals(first.id, focus.itemId)
        assertEquals(4, focus.caret)
    }

    @Test
    fun backspaceOnAnEmptyLastItemLeavesTheList() {
        val list = list(BlockType.LIST_BULLET, "milk", "")
        editor.load(listOf(list))

        editor.listBackspaceAtStart(list.id, list.items.value[1].id)

        assertEquals(listOf("milk"), list.items.value.map { it.value })
        assertTrue(editor.blocks[1] is Block.TextBlock)
    }

    @Test
    fun numbersFollowTheItemsAfterADelete() {
        val list = list(BlockType.LIST_NUMBER, "one", "two", "three")
        editor.load(listOf(list))

        editor.listBackspaceAtStart(list.id, list.items.value[1].id)

        // Numbers are drawn from the item's position, so they stay 1, 2 after a join.
        assertEquals(listOf("onetwo", "three"), list.items.value.map { it.value })
    }

    // --- typed marks ----------------------------------------------------------------------------

    @Test
    fun everyMarkTurnsTheParagraphIntoItsBlock() {
        val expected = mapOf(
            "# " to BlockType.H1,
            "## " to BlockType.H2,
            "### " to BlockType.H3,
            "#### " to BlockType.H4,
            "- " to BlockType.LIST_BULLET,
            "* " to BlockType.LIST_BULLET,
            "1. " to BlockType.LIST_NUMBER,
            "[] " to BlockType.LIST_CHECK,
            "[ ] " to BlockType.LIST_CHECK,
            "> " to BlockType.QUOTE,
        )
        expected.forEach { (mark, type) ->
            val block = Block.TextBlock()
            editor.load(listOf(block))
            val shortcut = typedShortcut(mark, mark.length)!!

            assertTrue(editor.applyShortcut(block.id, mark, shortcut))

            assertEquals(mark, type, editor.blocks.single().typeOf())
        }
    }

    @Test
    fun aMarkTypedBeforeTextKeepsTheText() {
        val block = text("Shopping")
        editor.load(listOf(block))

        editor.applyShortcut(block.id, "# Shopping", typedShortcut("# Shopping", 2)!!)

        val heading = editor.blocks.single() as Block.HBlock
        assertEquals("Shopping", heading.text.value)
        assertFocus(0, caret = 0)
    }

    @Test
    fun undoAfterAMarkBringsBackTheMarkAsTyped() {
        val block = Block.TextBlock()
        editor.load(listOf(block))
        editor.changeValue(block.id, block.text, "-", mergeable = true)

        editor.applyShortcut(block.id, "- ", typedShortcut("- ", 2)!!)
        editor.undo()

        assertEquals(listOf("- "), contents())
    }

    @Test
    fun threeDashesMakeASeparatorAndAParagraphToGoOnIn() {
        val block = text("--")
        editor.load(listOf(block))

        editor.applyShortcut(block.id, "---", typedShortcut("---", 3)!!)

        assertTrue(editor.blocks[0] is Block.SeparatorBlock)
        assertTrue(editor.blocks[1] is Block.TextBlock)
        assertFocus(1, caret = 0)
    }

    @Test
    fun aMarkIsOnlyAppliedToAParagraph() {
        val heading = Block.HBlock(MutableStateFlow("x"), blockType = BlockType.H1)
        editor.load(listOf(heading))

        assertFalse(editor.applyShortcut(heading.id, "- x", Shortcut(BlockType.LIST_BULLET, "- ")))
        assertNull(editor.breakLine("missing", enter))
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun list(type: BlockType, vararg items: String) =
        Block.ListBlock(MutableStateFlow(items.map { ItemListBlock(it) }), blockType = type)

    private fun contents(): List<String> = editor.blocks.map {
        when (it) {
            is Block.TextBlock -> it.text.value
            is Block.HBlock -> it.text.value
            is Block.QuoteBlock -> it.text.value
            else -> error("Not a text block: $it")
        }
    }

    private fun Block.typeOf(): BlockType = when (this) {
        is Block.HBlock -> blockType
        is Block.ListBlock -> blockType
        is Block.QuoteBlock -> BlockType.QUOTE
        is Block.TextBlock -> BlockType.TEXT
        is Block.LinkBlock -> BlockType.LINK
        is Block.SeparatorBlock -> BlockType.SEPARATOR
    }

    private fun BlockEditorState.lastFocus(): FocusRequest = runTestResult { selection.requests.first() }

    private fun assertFocus(index: Int, caret: Int?) {
        val request = editor.lastFocus()
        assertEquals(editor.blocks[index].id, request.blockId)
        assertEquals(caret, request.caret)
    }

    private fun <T> runTestResult(block: suspend () -> T): T {
        var result: T? = null
        runTest { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
