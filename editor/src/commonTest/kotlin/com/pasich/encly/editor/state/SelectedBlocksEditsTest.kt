package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectedBlocksEditsTest {
    private val editor = BlockEditorState { 0L }

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun load(vararg blocks: Block) = editor.load(blocks.toList())

    private fun textsOf() = editor.blocks.map {
        (it as? Block.TextBlock)?.text?.value ?: it::class.simpleName.orEmpty()
    }

    // --- deleting ----------------------------------------------------------------------------------

    @Test
    fun deletingPickedBlocksIsOneUndoStepThatBringsThemBackInPlace() {
        load(text("a"), text("b"), text("c"), text("d"))
        editor.pickedBlocks.select(editor.blocks[1].id)
        editor.pickedBlocks.toggle(editor.blocks[3].id)

        assertTrue(editor.deleteSelectedBlocks())
        assertEquals(listOf("a", "c"), textsOf())
        assertFalse(editor.pickedBlocks.isActive)

        editor.undo()
        assertEquals(listOf("a", "b", "c", "d"), textsOf())
        assertFalse(editor.canUndo.value)

        editor.redo()
        assertEquals(listOf("a", "c"), textsOf())
    }

    @Test
    fun deletingEveryBlockLeavesOneEmptyParagraphAndUndoBringsAllBack() {
        load(text("a"), Block.SeparatorBlock(), text("c"))
        editor.pickedBlocks.selectAll()

        assertTrue(editor.deleteSelectedBlocks())
        assertEquals(listOf(""), textsOf())

        editor.undo()
        assertEquals(listOf("a", "SeparatorBlock", "c"), textsOf())
        // One step: nothing is left to undo, and no stray paragraph came back with it.
        assertFalse(editor.canUndo.value)

        editor.redo()
        assertEquals(listOf(""), textsOf())
    }

    @Test
    fun deletingTheOnlyPickedBlockOfAnEmptyNoteKeepsAParagraph() {
        editor.pickedBlocks.selectAll()

        assertTrue(editor.deleteSelectedBlocks())

        assertEquals(1, editor.blocks.size)
    }

    @Test
    fun deletingNothingPickedChangesNothing() {
        load(text("a"))

        assertFalse(editor.deleteSelectedBlocks())
        assertEquals(listOf("a"), textsOf())
        assertFalse(editor.canUndo.value)
    }

    @Test
    fun aDeleteMovesTheCursorToTheEndOfTheBlockAboveTheGap() = runTest {
        load(text("a"), text("b"), text("c"))
        editor.pickedBlocks.select(editor.blocks[1].id)
        editor.pickedBlocks.toggle(editor.blocks[2].id)

        editor.deleteSelectedBlocks()

        assertEquals(FocusRequest(editor.blocks[0].id, cursorToEnd = true), editor.selection.requests.first())
        assertEquals(editor.blocks[0].id, editor.selection.focusedBlockId.value)
    }

    @Test
    fun aDeleteAtTheTopFocusesTheFirstBlockThatIsLeft() = runTest {
        load(text("a"), text("b"), text("c"))
        editor.pickedBlocks.select(editor.blocks[0].id)

        editor.deleteSelectedBlocks()

        assertEquals(FocusRequest(editor.blocks[0].id, cursorToEnd = true), editor.selection.requests.first())
    }

    // --- the mode ends -----------------------------------------------------------------------------

    @Test
    fun loadingANoteEndsTheMode() {
        load(text("a"), text("b"))
        editor.pickedBlocks.selectAll()

        load(text("c"))

        assertFalse(editor.pickedBlocks.isActive)
    }

    @Test
    fun undoAndRedoEndTheMode() {
        load(text("a"))
        editor.addBlock(text("b"))

        editor.pickedBlocks.selectAll()
        editor.undo()
        assertFalse(editor.pickedBlocks.isActive)

        editor.pickedBlocks.selectAll()
        editor.redo()
        assertFalse(editor.pickedBlocks.isActive)
    }

    @Test
    fun aFieldTakingFocusEndsTheMode() {
        load(text("a"), text("b"))
        editor.pickedBlocks.select(editor.blocks[0].id)

        editor.selection.onFocused(editor.blocks[1].id)

        assertFalse(editor.pickedBlocks.isActive)
    }

    @Test
    fun aPickedBlockThatIsRemovedLeavesTheSelection() {
        load(text("a"), text("b"), text("c"))
        editor.pickedBlocks.select(editor.blocks[1].id)
        editor.pickedBlocks.toggle(editor.blocks[2].id)

        editor.removeBlock(editor.blocks[2], BlockRemoveAction.REMOVE, refocus = false)

        assertEquals(setOf(editor.blocks[1].id), editor.pickedBlocks.ids.value)
    }

    // --- plain text --------------------------------------------------------------------------------

    @Test
    fun pickedBlocksReadAsPlainTextAParagraphEachWithListsOneItemToALine() {
        val bullets = Block.ListBlock(
            MutableStateFlow(listOf(ItemListBlock("one"), ItemListBlock(" "), ItemListBlock("two"))),
            BlockType.LIST_BULLET,
        )
        val numbers = Block.ListBlock(
            MutableStateFlow(listOf(ItemListBlock("first"), ItemListBlock("second"))),
            BlockType.LIST_NUMBER,
        )
        val checks = Block.ListBlock(
            MutableStateFlow(listOf(ItemListBlock("done", isCheck = true), ItemListBlock("todo"))),
            BlockType.LIST_CHECK,
        )
        load(
            Block.HBlock(MutableStateFlow("Title"), BlockType.H1),
            text("para"),
            text(""),
            Block.QuoteBlock(MutableStateFlow("quoted")),
            Block.SeparatorBlock(),
            bullets,
            numbers,
            checks,
            Block.LinkBlock(MutableStateFlow(LinkDataBlock(title = "Site", url = "https://a.example"))),
            Block.LinkBlock(MutableStateFlow(LinkDataBlock(url = "https://b.example"))),
        )
        editor.pickedBlocks.selectAll()

        assertEquals(
            "Title\n\npara\n\nquoted\n\n---\n\n- one\n- two\n\n1. first\n2. second\n\n[x] done\n[ ] todo\n\n" +
                "Site (https://a.example)\n\nhttps://b.example",
            editor.pickedPlainText(),
        )
    }

    @Test
    fun onlyThePickedBlocksAreRead() {
        load(text("a"), text("b"), text("c"))
        editor.pickedBlocks.select(editor.blocks[2].id)
        editor.pickedBlocks.toggle(editor.blocks[0].id)

        assertEquals("a\n\nc", editor.pickedPlainText())
    }

    @Test
    fun nothingPickedReadsAsNothing() {
        assertEquals("", editor.pickedPlainText())
    }
}
