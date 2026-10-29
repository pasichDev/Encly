package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Duplicate, move, delete and convert on the block the user works on: each is one undo step. */
class BlockActionsTest {
    private val editor = BlockEditorState { 0L }

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun list(vararg items: String) =
        Block.ListBlock(MutableStateFlow(items.map { ItemListBlock(it) }), BlockType.LIST_CHECK)

    private fun texts() = editor.blocks.map { (it as Block.TextBlock).text.value }

    private fun lastFocus(): FocusRequest {
        var request: FocusRequest? = null
        runTest { request = editor.selection.requests.first() }
        return request!!
    }

    // --- duplicate --------------------------------------------------------------------------------

    @Test
    fun duplicateInsertsAnIndependentCopyAfterTheBlockAndFocusesIt() {
        val a = text("a")
        val b = text("b")
        editor.load(listOf(a, b))

        assertTrue(editor.duplicateBlockOf(a.id))

        assertEquals(listOf("a", "a", "b"), texts())
        val copy = editor.blocks[1] as Block.TextBlock
        assertNotEquals(a.id, copy.id)
        copy.text.value = "changed"
        assertEquals("a", a.text.value)
        val focus = lastFocus()
        assertEquals(copy.id, focus.blockId)
        assertTrue(focus.cursorToEnd)
    }

    @Test
    fun duplicateIsOneUndoStepAndRedoBringsTheCopyBack() {
        val a = text("a")
        editor.load(listOf(a))

        editor.duplicateBlockOf(a.id)
        editor.undo()
        assertEquals(listOf("a"), texts())
        assertFalse(editor.canUndo.value)
        editor.redo()
        assertEquals(listOf("a", "a"), texts())
    }

    @Test
    fun aDuplicatedListSharesNeitherItsFlowNorItsItemIds() {
        val original = list("milk", "eggs")
        editor.load(listOf(original))

        editor.duplicateBlockOf(original.id)

        val copy = editor.blocks[1] as Block.ListBlock
        assertEquals(listOf("milk", "eggs"), copy.items.value.map { it.value })
        assertTrue(copy.items.value.map { it.id }.intersect(original.items.value.map { it.id }.toSet()).isEmpty())
        assertEquals(copy.blockType, original.blockType)
        copy.items.value = copy.items.value.map { it.copy(value = "x", isCheck = true) }
        assertEquals(listOf("milk", "eggs"), original.items.value.map { it.value })
        assertFalse(original.items.value.any { it.isCheck })
    }

    @Test
    fun linksHeadingsQuotesAndSeparatorsDuplicateToo() {
        val link = Block.LinkBlock(MutableStateFlow(LinkDataBlock(title = "t", url = "https://example.com")))
        val heading = Block.HBlock(MutableStateFlow("H"), BlockType.H3)
        val quote = Block.QuoteBlock(MutableStateFlow("Q"))
        val separator = Block.SeparatorBlock()
        editor.load(listOf(link, heading, quote, separator))

        listOf(link, heading, quote, separator).forEach { editor.duplicateBlockOf(it.id) }

        assertEquals(8, editor.blocks.size)
        assertEquals(
            "https://example.com",
            (editor.blocks[1] as Block.LinkBlock).block.value.url,
        )
        assertEquals(BlockType.H3, (editor.blocks[3] as Block.HBlock).blockType)
        assertEquals("Q", (editor.blocks[5] as Block.QuoteBlock).text.value)
        assertTrue(editor.blocks[7] is Block.SeparatorBlock)
        assertEquals(8, editor.blocks.map { it.id }.toSet().size)
    }

    @Test
    fun duplicateInteractedActsOnTheBlockTheUserLastWorkedOn() {
        val a = text("a")
        val b = text("b")
        editor.load(listOf(a, b))
        editor.selection.onInteraction(b.id)

        editor.duplicateInteracted()

        assertEquals(listOf("a", "b", "b"), texts())
    }

    @Test
    fun duplicateOfABlockThatIsGoneDoesNothing() {
        editor.load(listOf(text("a")))

        assertFalse(editor.duplicateBlockOf("gone"))
        assertFalse(editor.canUndo.value)
    }

    // --- move, delete, convert ----------------------------------------------------------------------

    @Test
    fun moveUpAndDownKeepTheBlockFocusedAndEachIsOneUndoStep() {
        val a = text("a")
        val b = text("b")
        val c = text("c")
        editor.load(listOf(a, b, c))
        editor.selection.onInteraction(b.id)

        editor.moveInteracted(up = true)
        assertEquals(listOf("b", "a", "c"), texts())
        assertEquals(b.id, lastFocus().blockId)
        editor.moveInteracted(up = false)
        assertEquals(listOf("a", "b", "c"), texts())
        editor.undo()
        assertEquals(listOf("b", "a", "c"), texts())
        editor.undo()
        assertEquals(listOf("a", "b", "c"), texts())
    }

    @Test
    fun theFirstBlockCannotMoveUpAndTheLastCannotMoveDown() {
        val a = text("a")
        val b = text("b")
        editor.load(listOf(a, b))
        editor.selection.onInteraction(a.id)
        assertFalse(editor.canMoveInteracted(up = true))
        editor.selection.onInteraction(b.id)
        assertFalse(editor.canMoveInteracted(up = false))
    }

    @Test
    fun deleteIsOneUndoStepFocusesTheBlockAboveAndNeverLeavesTheNoteEmpty() {
        val a = text("a")
        val b = text("b")
        editor.load(listOf(a, b))
        editor.selection.onInteraction(b.id)

        editor.removeInteracted()

        assertEquals(listOf("a"), texts())
        assertEquals(a.id, lastFocus().blockId)
        assertFalse(editor.canRemoveInteracted())
        editor.undo()
        assertEquals(listOf("a", "b"), texts())
    }

    @Test
    fun convertKeepsTheTextAndTheCaretAndIsOneUndoStep() {
        val block = text("Hello")
        editor.load(listOf(block))
        editor.selection.onCaret(block.id, 3)
        editor.selection.onInteraction(block.id)

        editor.applyTool(BlockType.H2)

        val heading = editor.blocks.single() as Block.HBlock
        assertEquals("Hello", heading.text.value)
        assertEquals(3, lastFocus().caret)
        editor.undo()
        assertEquals("Hello", (editor.blocks.single() as Block.TextBlock).text.value)
    }
}
