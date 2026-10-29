package com.pasich.encly.editor.state

import androidx.compose.ui.unit.dp
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.theme.EditorLineSpacing
import com.pasich.encly.editor.theme.blockGap
import com.pasich.encly.editor.theme.frameInset
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The corners of the editor's state operations that the scenario tests reach only by accident. */
class StateGapsTest {
    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun list(vararg values: String, type: BlockType = BlockType.LIST_BULLET) =
        Block.ListBlock(MutableStateFlow(values.map { ItemListBlock(it) }), type)

    private fun editorOf(vararg blocks: Block) = BlockEditorState { 0L }.also { it.load(blocks.toList()) }

    private fun enter(head: String = "", tail: String = "") = LineBreak(head, listOf("", ""), tail)

    // --- marks -------------------------------------------------------------------------------

    @Test
    fun everyMarkMakesItsBlock() {
        assertTrue(shortcutBlock(Shortcut(BlockType.SEPARATOR, "---"), "") is Block.SeparatorBlock)
        assertEquals(
            "https://x.org",
            (shortcutBlock(Shortcut(BlockType.LINK, ""), "https://x.org") as Block.LinkBlock).block.value.url,
        )
        assertEquals("plain", (shortcutBlock(Shortcut(BlockType.TEXT, ""), "plain") as Block.TextBlock).text.value)
    }

    @Test
    fun aPastedBlockEndingInAFieldNeedsNoTrailingParagraph() {
        val list = list("a")
        assertEquals(listOf<Block>(list), withTrailingParagraph(listOf(list)))
        assertTrue(withTrailingParagraph(listOf(Block.SeparatorBlock())).last() is Block.TextBlock)
        assertEquals(1, withTrailingParagraph(emptyList()).size)
    }

    @Test
    fun theTypedRuleNeedsTheCursorAtItsEnd() {
        assertNull(typedShortcut("---", cursor = 1))
        assertEquals(BlockType.SEPARATOR, typedShortcut("---", cursor = 3)?.type)
    }

    // --- text edits and the cursor -----------------------------------------------------------

    @Test
    fun aDiffKnowsWhereItsInsertionEnds() {
        assertEquals(4, TextDiff(start = 2, removed = "", inserted = "ab").end)
    }

    @Test
    fun equalTextsMoveNoCursor() {
        assertNull(changedCaret("same", "same"))
        assertNull(caretTargetOf("b", 1, 2))
    }

    @Test
    fun aListThatLostItsOnlyItemsMovesNoCursor() {
        assertNull(caretTargetOf("b", listOf(ItemListBlock("a")), emptyList<ItemListBlock>()))
    }

    @Test
    fun aConvertedBlockCarriesItsCursorOnlyWhereTheTextGoes() {
        val paragraph = text("ab\ncd")
        val items = Block.ListBlock(
            MutableStateFlow(listOf(ItemListBlock("ab"), ItemListBlock("cd"))),
            BlockType.LIST_BULLET,
        )

        assertNull(carriedCaret(Block.SeparatorBlock(), paragraph, 1, null))
        assertNull(carriedCaret(items, paragraph, 1, "no such item"))
        assertEquals(items.items.value.last().id, carriedCaret(paragraph, items, 100, null)?.itemId)
        val empty = Block.ListBlock(MutableStateFlow(emptyList()), BlockType.LIST_BULLET)
        assertNull(carriedCaret(paragraph, empty, 1, null))
    }

    // --- the toolbar's tools -----------------------------------------------------------------

    @Test
    fun linkAndSeparatorShowTheirOwnTool() {
        assertEquals(BlockType.LINK, Block.LinkBlock().toolType())
        assertEquals(BlockType.SEPARATOR, Block.SeparatorBlock().toolType())
    }

    @Test
    fun aNoteWithNothingToWriteInGetsAParagraphWhenTheKeyboardIsAskedFor() {
        val editor = editorOf(Block.SeparatorBlock())

        editor.focusWorkingBlock()

        assertEquals(2, editor.blocks.size)
        assertTrue(editor.blocks.last() is Block.TextBlock)
    }

    @Test
    fun conversionsKeepTextOrRefuse() {
        val heading = Block.HBlock(MutableStateFlow("h"), BlockType.H1)
        assertEquals("h", (convertBlock(heading, BlockType.TEXT) as Block.TextBlock).text.value)
        assertNull(convertBlock(text("not empty"), BlockType.LINK))
        assertNull(convertBlock(text("x"), BlockType.SEPARATOR))
        assertNull(convertBlock(Block.SeparatorBlock(), BlockType.H1))
        assertTrue(
            convertBlock(Block.QuoteBlock(MutableStateFlow("q")), BlockType.QUOTE, exact = true) is Block.TextBlock,
        )
        assertTrue(convertBlock(Block.LinkBlock(), BlockType.TEXT) == null)
        assertTrue(convertBlock(text(""), BlockType.LINK) is Block.LinkBlock)
    }

    @Test
    fun aListConvertsKeepingItsItems() {
        val bullets = list("a", "b")
        val converted = convertBlock(bullets, BlockType.LIST_NUMBER) as Block.ListBlock
        assertEquals(bullets.items.value, converted.items.value)
    }

    @Test
    fun thePositionOfTheWorkedOnBlockIsReported() {
        val editor = editorOf(text("a"), text("b"), text("c"))
        editor.selection.onInteraction(editor.blocks[1].id)
        assertEquals(BLOCK_IN_MIDDLE, editor.interactedBlockPosition())
        editor.selection.onInteraction(editor.blocks[0].id)
        assertEquals(BLOCK_AT_TOP, editor.interactedBlockPosition())
        editor.selection.onInteraction(editor.blocks[2].id)
        assertEquals(BLOCK_AT_BOTTOM, editor.interactedBlockPosition())
    }

    @Test
    fun addingAtTheEndAfterANonParagraphAddsOne() {
        val editor = editorOf(text("a"), Block.SeparatorBlock())

        editor.addBlockToEnd()
        assertEquals(3, editor.blocks.size)
        editor.addBlockToEnd()
        assertEquals(3, editor.blocks.size)
    }

    // --- Enter and Backspace -----------------------------------------------------------------

    @Test
    fun enterAtTheStartOfAnItemOpensAnEmptyOneAbove() {
        val list = list("a", "b")
        val editor = editorOf(list)
        val second = list.items.value[1]

        val kept = editor.listLineBreak(list.id, second.id, enter(head = "", tail = "b"))

        assertEquals("b", kept)
        assertEquals(listOf("a", "", "b"), list.items.value.map { it.value })
    }

    @Test
    fun enterInAnEmptyFirstItemLeavesTheListUpward() {
        val list = list("", "x")
        val editor = editorOf(list)

        editor.listLineBreak(list.id, list.items.value[0].id, enter())

        assertEquals(listOf("x"), list.items.value.map { it.value })
        assertTrue(editor.blocks.first() is Block.TextBlock)
    }

    @Test
    fun enterInAnEmptyMiddleItemSplitsTheList() {
        val list = list("a", "", "c")
        val editor = editorOf(list)

        editor.listLineBreak(list.id, list.items.value[1].id, enter())

        assertEquals(3, editor.blocks.size)
        assertTrue(editor.blocks[1] is Block.TextBlock)
    }

    @Test
    fun backspaceInTheOnlyItemTurnsTheListIntoAParagraph() {
        val list = list("solo")
        val editor = editorOf(list)

        assertTrue(editor.listBackspaceAtStart(list.id, list.items.value[0].id))

        assertEquals("solo", (editor.blocks.single() as Block.TextBlock).text.value)
        assertFalse(editor.listBackspaceAtStart("gone", "gone"))
    }

    @Test
    fun backspaceAtTheStartOfNothingToJoinDoesNothing() {
        val separator = Block.SeparatorBlock()
        val link = Block.LinkBlock()
        val editor = editorOf(text("first"), separator, text("t"), link, text("after link"))

        assertFalse(editor.backspaceAtStart(separator.id))
        assertFalse(editor.backspaceAtStart("gone"))
        assertFalse(editor.backspaceAtStart(editor.blocks[0].id))
        assertFalse(editor.backspaceAtStart(editor.blocks.last().id))
    }

    @Test
    fun backspaceAfterAQuoteJoinsTheParagraphToIt() {
        val quote = Block.QuoteBlock(MutableStateFlow("q"))
        val paragraph = text("p")
        val editor = editorOf(quote, paragraph)

        assertTrue(editor.backspaceAtStart(paragraph.id))

        assertEquals("qp", quote.text.value)
        assertEquals(1, editor.blocks.size)
    }

    // --- spacing -----------------------------------------------------------------------------

    @Test
    fun comfortableSpacingAddsToTheDefaults() {
        assertEquals(6.dp, EditorLineSpacing.COMFORTABLE.frameInset)
        assertEquals(12.dp, EditorLineSpacing.COMFORTABLE.blockGap(8.dp))
        assertEquals(0.dp, EditorLineSpacing.COMPACT.blockGap(8.dp))
        assertEquals(8.dp, EditorLineSpacing.DEFAULT.blockGap(8.dp))
    }
}
