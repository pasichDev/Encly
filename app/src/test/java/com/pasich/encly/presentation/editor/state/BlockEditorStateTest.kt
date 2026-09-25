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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockEditorStateTest {
    private var now = 0L
    private val editor = BlockEditorState { now }

    // --- content and undo/redo -----------------------------------------------------------------

    @Test
    fun aNewEditorHasOneEmptyTextBlockAndNoHistory() {
        assertEquals(1, editor.blocks.size)
        assertTrue(editor.blocks.single() is Block.TextBlock)
        assertFalse(editor.canUndo.value)
        assertFalse(editor.canRedo.value)
    }

    @Test
    fun typeUndoTypeUndoNeverBringsBackUndoneText() {
        val block = editor.blocks[0] as Block.TextBlock
        type(block, "hello")
        editor.undo()
        assertEquals("", block.text.value)

        now += BlockOperations.MERGE_WINDOW_MS + 1
        type(block, "x")
        editor.undo()

        assertEquals("", block.text.value)
        editor.redo()
        assertEquals("x", block.text.value)
    }

    @Test
    fun typingInQuickSuccessionIsOneUndoStep() {
        val block = editor.blocks[0] as Block.TextBlock
        listOf("h", "he", "hel").forEach {
            type(block, it)
            now += 100
        }

        editor.undo()

        assertEquals("", block.text.value)
        assertFalse(editor.canUndo.value)
        assertTrue(editor.canRedo.value)
    }

    @Test
    fun checkingAChecklistItemIsUndoable() {
        val list = Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("milk"))), BlockType.LIST_CHECK)
        editor.load(listOf(list))

        editor.changeValue(list.id, list.items, listOf(list.items.value[0].copy(isCheck = true)), mergeable = false)
        assertTrue(list.items.value.single().isCheck)
        editor.undo()

        assertFalse(list.items.value.single().isCheck)
        editor.redo()
        assertTrue(list.items.value.single().isCheck)
    }

    @Test
    fun historyIsBounded() {
        val block = editor.blocks[0] as Block.TextBlock
        repeat(BlockOperations.MAX_HISTORY + 10) { editor.changeValue(block.id, block.text, "v$it", mergeable = false) }

        var undone = 0
        while (editor.canUndo.value) {
            editor.undo()
            undone++
        }

        assertEquals(BlockOperations.MAX_HISTORY, undone)
    }

    @Test
    fun undoNeverLeavesTheNoteWithoutBlocks() {
        editor.addBlock(Block.HBlock(blockType = BlockType.H1))
        repeat(3) { editor.undo() }

        assertEquals(1, editor.blocks.size)
        assertTrue(editor.blocks.single() is Block.TextBlock)
    }

    // --- structure ------------------------------------------------------------------------------

    @Test
    fun aBlockAddedToAnEmptyNoteReplacesTheEmptyFirstBlockAndIsFocused() = runTest {
        val heading = Block.HBlock(blockType = BlockType.H2)

        editor.addBlock(heading)

        assertEquals(listOf<Block>(heading), editor.blocks)
        assertEquals(FocusRequest(heading.id), editor.selection.requests.first())
    }

    @Test
    fun addedBlocksGoAfterTheFocusedBlock() {
        editor.load(listOf(text("a"), text("b"), text("c")))
        editor.selection.onFocused(editor.blocks[0].id)

        editor.addBlock(Block.QuoteBlock())

        assertTrue(editor.blocks[1] is Block.QuoteBlock)
        assertEquals(1, editor.selection.interactedIndex)
    }

    @Test
    fun anAddedListIsFocusedLikeAnyBlock() = runTest {
        editor.load(listOf(text("a")))
        val list = Block.ListBlock(blockType = BlockType.LIST_NUMBER)

        editor.addBlock(list)

        assertEquals(list.id, editor.selection.interactedBlockId.value)
        assertEquals(FocusRequest(list.id), editor.selection.requests.first())
    }

    // --- toolbar tools -------------------------------------------------------------------------

    @Test
    fun aToolTurnsTheParagraphIntoItsTypeKeepingTheText() {
        editor.load(listOf(text("Before the visit")))
        editor.selection.onFocused(editor.blocks[0].id)

        editor.applyTool(HEADING_TOOL)

        val heading = editor.blocks.single() as Block.HBlock
        assertEquals(BlockType.H2, heading.blockType)
        assertEquals("Before the visit", heading.text.value)
    }

    @Test
    fun theActiveToolTurnsTheBlockBackIntoAParagraphAndUndoRestoresIt() {
        val quote = Block.QuoteBlock(MutableStateFlow("sleep on it"))
        editor.load(listOf(quote))
        editor.selection.onFocused(quote.id)
        assertEquals(BlockType.QUOTE, quote.toolType())

        editor.applyTool(BlockType.QUOTE)
        assertEquals("sleep on it", (editor.blocks.single() as Block.TextBlock).text.value)

        editor.undo()
        assertSame(quote, editor.blocks.single())
    }

    @Test
    fun aListToolMakesOneItemPerLineAndAParagraphJoinsThemAgain() {
        editor.load(listOf(text("milk\nbread")))
        editor.selection.onFocused(editor.blocks[0].id)

        editor.applyTool(BlockType.LIST_CHECK)
        val list = editor.blocks.single() as Block.ListBlock
        assertEquals(listOf("milk", "bread"), list.items.value.map { it.value })

        editor.applyTool(BlockType.LIST_CHECK)
        assertEquals("milk\nbread", (editor.blocks.single() as Block.TextBlock).text.value)
    }

    @Test
    fun anExactHeadingLevelChangesTheLevelInsteadOfTurningItBack() {
        val heading = Block.HBlock(MutableStateFlow("h"), blockType = BlockType.H1)
        editor.load(listOf(heading))
        editor.selection.onFocused(heading.id)

        editor.applyTool(BlockType.H3, exact = true)

        assertEquals(BlockType.H3, (editor.blocks.single() as Block.HBlock).blockType)
    }

    @Test
    fun aLinkNeverReplacesText() {
        editor.load(listOf(text("keep me")))
        editor.selection.onFocused(editor.blocks[0].id)

        editor.applyTool(BlockType.LINK)

        assertEquals("keep me", (editor.blocks[0] as Block.TextBlock).text.value)
        assertTrue(editor.blocks[1] is Block.LinkBlock)
    }

    @Test
    fun aSeparatorIsFollowedByAParagraphToGoOnWriting() = runTest {
        editor.load(listOf(text("a")))
        editor.selection.onFocused(editor.blocks[0].id)

        editor.applyTool(BlockType.SEPARATOR)

        assertTrue(editor.blocks[1] is Block.SeparatorBlock)
        assertTrue(editor.blocks[2] is Block.TextBlock)
        assertEquals(FocusRequest(editor.blocks[2].id), editor.selection.requests.first())
    }

    @Test
    fun theTitlesNextFocusesTheFirstBlockWithAField() = runTest {
        editor.load(listOf(Block.SeparatorBlock(), text("b")))

        editor.focusFirstBlock()

        assertEquals(FocusRequest(editor.blocks[1].id), editor.selection.requests.first())
    }

    @Test
    fun removingAnEmptyBlockByBackspaceFocusesTheBlockAboveWithTheCursorAtTheEnd() = runTest {
        val above = text("above")
        val empty = text("")
        editor.load(listOf(above, Block.SeparatorBlock(), empty))

        editor.removeBlock(empty, BlockRemoveAction.REMOVE_BACKSPACE)

        assertEquals(2, editor.blocks.size)
        assertEquals(FocusRequest(above.id, cursorToEnd = true), editor.selection.requests.first())
    }

    @Test
    fun backspaceDoesNotRemoveABlockWithTextOrTheLastBlock() {
        val full = text("full")
        editor.load(listOf(full, text("")))

        editor.removeBlock(full, BlockRemoveAction.REMOVE_BACKSPACE)
        assertEquals(2, editor.blocks.size)

        editor.removeBlock(editor.blocks[1], BlockRemoveAction.REMOVE)
        editor.removeBlock(full, BlockRemoveAction.REMOVE)
        assertEquals(listOf<Block>(full), editor.blocks)
    }

    @Test
    fun removingTheSecondOfTwoEqualSeparatorsRemovesThatOne() {
        val first = Block.SeparatorBlock()
        val second = Block.SeparatorBlock()
        editor.load(listOf(first, second))

        editor.removeBlock(second, BlockRemoveAction.REMOVE, refocus = false)

        assertSame(first, editor.blocks.single())
    }

    @Test
    fun replacingABlockFocusesTheReplacementAtTheSameIndex() = runTest {
        val replacement = Block.TextBlock()

        assertTrue(editor.replaceBlock(0, replacement))
        assertFalse(editor.replaceBlock(5, Block.TextBlock()))

        assertSame(replacement, editor.blocks.single())
        assertEquals(FocusRequest(replacement.id), editor.selection.requests.first())
    }

    @Test
    fun contentUndoFollowsItsBlockAfterABlockIsInsertedBeforeIt() {
        val world = text("world")
        editor.load(listOf(text("hello"), world))

        editor.addBlockAfter(0, Block.TextBlock())
        val inserted = editor.blocks[1] as Block.TextBlock
        type(inserted, "a")
        editor.undo()

        assertEquals("", inserted.text.value)
        assertEquals("world", world.text.value)
    }

    @Test
    fun contentChangesEmitsForANoteOfOnlySeparators() = runTest {
        editor.load(listOf(Block.SeparatorBlock()))

        // Would suspend forever if a note without text fields never reported a change.
        editor.contentChanges.first()
    }

    // --- the block the user works on --------------------------------------------------------------

    @Test
    fun theInteractedBlockIsFollowedByIdentityWhenBlocksShiftAroundIt() {
        editor.load(listOf(text("a"), text("b")))
        editor.addBlockAfter(-1, Block.SeparatorBlock())
        val b = editor.blocks[2]
        editor.selection.onInteraction(b.id)

        editor.undo() // the separator goes: b moves up
        assertEquals(1, editor.selection.interactedIndex)
        editor.redo() // and comes back: b moves down again
        assertEquals(2, editor.selection.interactedIndex)
    }

    @Test
    fun whenTheInteractedBlockDisappearsItsPositionStaysInsideTheList() {
        editor.load(listOf(text("a")))
        editor.addBlockAfter(0, Block.QuoteBlock())
        assertEquals(1, editor.selection.interactedIndex)

        editor.undo()

        assertEquals(0, editor.selection.interactedIndex)
        assertEquals(-1, editor.selection.focusedIndex)
    }

    @Test
    fun aNewBlockGoesAfterTheFocusedListNotAfterTheTextBlockFocusedBeforeIt() {
        val list = Block.ListBlock(blockType = BlockType.LIST_CHECK)
        editor.load(listOf(text("a"), list, text("c")))
        editor.selection.onFocused(editor.blocks[0].id)
        // Focus moves into an item of the list: the text block loses it, the list takes it.
        editor.selection.onFocusLost(editor.blocks[0].id)
        editor.selection.onFocused(list.id)

        val quote = Block.QuoteBlock()
        editor.addBlock(quote)

        assertEquals(2, editor.blocks.indexOf(quote))
    }

    @Test
    fun aBlockThatLostFocusNoLongerDecidesWhereTheNextBlockGoes() {
        editor.load(listOf(text("a"), text("b"), text("c")))
        editor.selection.onFocused(editor.blocks[0].id)
        editor.selection.onFocusLost(editor.blocks[0].id)
        editor.selection.onInteraction(editor.blocks[2].id)

        val quote = Block.QuoteBlock()
        editor.addBlock(quote)

        assertEquals(3, editor.blocks.indexOf(quote))
    }

    @Test
    fun losingFocusOfAnotherBlockKeepsTheFocusedOne() {
        editor.load(listOf(text("a"), text("b")))
        editor.selection.onFocused(editor.blocks[1].id)
        editor.selection.onFocusLost(editor.blocks[0].id)

        assertEquals(1, editor.selection.focusedIndex)
    }

    @Test
    fun toolbarMovesAndRemovesTheInteractedBlock() {
        editor.load(listOf(text("a"), text("b"), text("c")))
        editor.selection.onInteraction(editor.blocks[1].id)

        assertTrue(editor.canMoveInteracted(up = true))
        editor.moveInteracted(up = true)
        assertEquals(listOf("b", "a", "c"), texts())
        assertFalse(editor.canMoveInteracted(up = true))
        assertEquals(BLOCK_AT_TOP, editor.interactedBlockPosition())

        editor.removeInteracted()
        assertEquals(listOf("a", "c"), texts())
    }

    @Test
    fun addBlockToEndReusesATrailingTextBlock() {
        editor.load(listOf(Block.SeparatorBlock()))

        editor.addBlockToEnd()
        editor.addBlockToEnd()

        assertEquals(2, editor.blocks.size)
        assertTrue(editor.blocks.last() is Block.TextBlock)
        assertEquals(BLOCK_AT_BOTTOM, editor.interactedBlockPosition())
    }

    @Test
    fun focusabilityIsDecidedByBlockType() {
        assertTrue(Block.QuoteBlock().canTakeFocus())
        assertFalse(Block.SeparatorBlock().canTakeFocus())
        assertTrue(Block.LinkBlock().canTakeFocus())
        assertFalse(Block.LinkBlock(MutableStateFlow(LinkDataBlock(url = "https://a.b"))).canTakeFocus())

        val blocks = listOf(Block.SeparatorBlock(), text("x"), Block.SeparatorBlock())
        assertEquals(1, blocks.previousFocusableIndex(2))
        // Nothing focusable above: the first focusable block of the note.
        assertEquals(1, blocks.previousFocusableIndex(0))
    }

    private fun type(block: Block.TextBlock, value: String) =
        editor.changeValue(block.id, block.text, value, mergeable = true)

    private fun texts(): List<String> = editor.blocks.map { (it as Block.TextBlock).text.value }

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))
}
