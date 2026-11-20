package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextRangeSelectionTest {
    private val editor = BlockEditorState { 0L }

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun heading(value: String) = Block.HBlock(MutableStateFlow(value), BlockType.H2)

    private fun quote(value: String) = Block.QuoteBlock(MutableStateFlow(value))

    private fun load(vararg blocks: Block) = editor.load(blocks.toList())

    private fun point(index: Int, offset: Int) = TextPoint(editor.blocks[index].id, offset)

    private fun select(from: TextPoint, to: TextPoint) = editor.textRange.select(from, to)

    private fun shown() = editor.blocks.map { block ->
        when (block) {
            is Block.TextBlock -> "p:" + block.text.value
            is Block.HBlock -> "h:" + block.text.value
            is Block.QuoteBlock -> "q:" + block.text.value
            else -> block::class.simpleName.orEmpty()
        }
    }

    // --- the selection ---------------------------------------------------------------------------

    @Test
    fun thereIsNoSelectionAtFirst() {
        load(text("a"))

        assertFalse(editor.textRange.isActive)
        assertEquals(0, editor.textRange.blockCount())
        assertTrue(editor.textRangeBlocks().isEmpty())
        assertEquals("", editor.textRangePlainText())
        assertFalse(editor.replaceTextRange("x"))
    }

    @Test
    fun blocksThatAreNotInTheNoteCannotBeSelected() {
        load(text("a"), text("b"))

        select(TextPoint("gone", 0), point(1, 1))
        assertFalse(editor.textRange.isActive)
        select(point(0, 0), TextPoint("gone", 0))
        assertFalse(editor.textRange.isActive)
        editor.textRange.extendTo(point(1, 0))
        assertFalse(editor.textRange.isActive)
    }

    @Test
    fun extendingKeepsTheAnchorAndMovesTheHead() {
        load(text("abc"), text("def"), text("ghi"))
        select(point(0, 1), point(0, 2))
        assertTrue(editor.textRange.isActive)

        editor.textRange.extendTo(point(2, 2))

        assertEquals(point(0, 1), editor.textRange.ends.value!!.anchor)
        assertEquals(3, editor.textRange.blockCount())
        assertEquals("bc\n\ndef\n\ngh", editor.textRangePlainText())
    }

    @Test
    fun aSelectionMadeBackwardsReadsTheSameAsOneMadeForward() {
        load(text("abc"), text("def"))

        select(point(1, 2), point(0, 1))

        assertEquals("bc\n\nde", editor.textRangePlainText())
        select(point(0, 2), point(0, 1))
        assertEquals("b", editor.textRangePlainText())
    }

    @Test
    fun selectAllCoversEveryBlockFromStartToEnd() {
        load(text("abc"), Block.SeparatorBlock(), text("def"))

        editor.textRange.selectAll()

        assertEquals(3, editor.textRange.blockCount())
        assertEquals("abc\n\n---\n\ndef", editor.textRangePlainText())
    }

    @Test
    fun clearingForgetsIt() {
        load(text("a"))
        select(point(0, 0), point(0, 1))

        editor.textRange.clear()

        assertFalse(editor.textRange.isActive)
        editor.textRange.extendTo(point(0, 1))
        assertFalse(editor.textRange.isActive)
    }

    @Test
    fun aSelectionWhoseBlockWasRemovedResolvesToNothing() {
        load(text("a"), text("b"), text("c"))
        select(point(0, 0), point(2, 1))

        editor.removeBlock(editor.blocks[2], BlockRemoveAction.REMOVE, refocus = false)

        assertEquals(0, editor.textRange.blockCount())
        assertFalse(editor.replaceTextRange(""))
    }

    @Test
    fun offsetsOutsideTheTextAreKeptInside() {
        load(text("abc"), text("def"))

        select(point(0, -5), point(1, 99))

        assertEquals("abc\n\ndef", editor.textRangePlainText())
    }

    @Test
    fun theEmojiAtAnEdgeIsNeverCutInTwo() {
        load(text("a😀b"), text("c😀d"))

        // The start is in the middle of the first emoji, the end in the middle of the second one.
        select(point(0, 2), point(1, 2))

        assertEquals("😀b\n\nc😀", editor.textRangePlainText())
    }

    @Test
    fun anOffsetBetweenTwoUnrelatedSurrogatesIsLeftAlone() {
        load(text("😀😀"))

        select(point(0, 2), point(0, 4))

        assertEquals("😀", editor.textRangePlainText())
    }

    // --- what it covers ----------------------------------------------------------------------------

    @Test
    fun theMiddleOfTheSelectionIsWholeAndEveryKindOfBlockIsIncluded() {
        val bullets = Block.ListBlock(
            MutableStateFlow(listOf(ItemListBlock("one"), ItemListBlock("two"))),
            BlockType.LIST_BULLET,
        )
        load(heading("Head"), quote("said"), Block.SeparatorBlock(), bullets, text("tail"))

        select(point(0, 2), point(4, 2))

        assertEquals("ad\n\nsaid\n\n---\n\n- one\n- two\n\nta", editor.textRangePlainText())
        // The copies share nothing with the note.
        assertEquals("Head", (editor.blocks[0] as Block.HBlock).text.value)
    }

    @Test
    fun aListIsSelectedFromTheItemAndOffsetAnEndIsIn() {
        val checks = Block.ListBlock(
            MutableStateFlow(
                listOf(ItemListBlock("alpha", isCheck = true), ItemListBlock("beta"), ItemListBlock("gamma")),
            ),
            BlockType.LIST_CHECK,
        )
        load(text("abc"), checks, text("def"))

        select(point(0, 1), TextPoint(checks.id, 2, checks.items.value[1].id))
        assertEquals("bc\n\n[x] alpha\n[ ] be", editor.textRangePlainText())

        select(TextPoint(checks.id, 1, checks.items.value[1].id), point(2, 1))
        assertEquals("[ ] eta\n[ ] gamma\n\nd", editor.textRangePlainText())

        select(TextPoint(checks.id, 1, checks.items.value[0].id), TextPoint(checks.id, 3, checks.items.value[2].id))
        assertEquals("[x] lpha\n[ ] beta\n[ ] gam", editor.textRangePlainText())
    }

    // --- replacing, deleting -----------------------------------------------------------------------

    @Test
    fun theRemainsOfTheFirstAndLastBlockJoinAndTheBlocksBetweenGoInOneStep() {
        load(heading("Heading"), text("middle"), quote("quoted text"))
        select(point(0, 3), point(2, 6))

        assertTrue(editor.replaceTextRange(""))

        // The first block's type wins.
        assertEquals(listOf("h:Hea text"), shown())
        assertFalse(editor.textRange.isActive)

        editor.undo()
        assertEquals(listOf("h:Heading", "p:middle", "q:quoted text"), shown())
        assertFalse(editor.canUndo.value)

        editor.redo()
        assertEquals(listOf("h:Hea text"), shown())
    }

    @Test
    fun typedTextReplacesTheSelectionBetweenTheRemains() {
        load(text("one two"), text("three four"))
        select(point(0, 4), point(1, 6))

        editor.replaceTextRange("X")

        assertEquals(listOf("p:one Xfour"), shown())
    }

    @Test
    fun aSelectionInsideOneBlockIsAnOrdinaryReplace() {
        load(text("hello world"))
        select(point(0, 0), point(0, 6))

        editor.replaceTextRange("")

        assertEquals(listOf("p:world"), shown())
        editor.undo()
        assertEquals(listOf("p:hello world"), shown())
    }

    @Test
    fun theEmojiAtTheEdgesIsReplacedWhole() {
        load(text("a😀b"), text("c😀d"))
        select(point(0, 2), point(1, 2))

        editor.replaceTextRange("")

        assertEquals(listOf("p:ad"), shown())
    }

    @Test
    fun aFirstBlockThatIsNotTextLetsTheLastBlocksTypeWin() {
        load(Block.SeparatorBlock(), text("middle"), quote("quoted"))
        select(point(0, 0), point(2, 3))

        editor.replaceTextRange("")

        assertEquals(listOf("q:ted"), shown())
        editor.undo()
        assertEquals(listOf("SeparatorBlock", "p:middle", "q:quoted"), shown())
    }

    @Test
    fun aLastBlockThatIsNotTextLeavesTheFirstBlocksRemainder() {
        load(text("keep this"), Block.SeparatorBlock())
        select(point(0, 4), point(1, 0))

        editor.replaceTextRange("")

        assertEquals(listOf("p:keep"), shown())
    }

    @Test
    fun aSelectionOfOnlyBlocksWithoutTextIsReplacedByTheLastBlocksList() {
        val bullets = Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("one"))), BlockType.LIST_BULLET)
        load(Block.SeparatorBlock(), bullets)
        editor.textRange.selectAll()

        editor.replaceTextRange("new")

        // The last block is a list, so the result is a list.
        assertEquals(listOf("ListBlock"), shown())
        assertEquals(listOf("new"), (editor.blocks.single() as Block.ListBlock).items.value.map { it.value })
        editor.undo()
        assertEquals(listOf("SeparatorBlock", "ListBlock"), shown())
        assertFalse(editor.canUndo.value)
    }

    @Test
    fun deletingTheWholeNoteLeavesOneEmptyParagraphAndUndoBringsItAllBack() {
        load(text("a"), heading("b"), text("c"))
        editor.textRange.selectAll()

        editor.replaceTextRange("")

        assertEquals(listOf("p:"), shown())
        editor.undo()
        assertEquals(listOf("p:a", "h:b", "p:c"), shown())
    }

    // --- the cursor --------------------------------------------------------------------------------

    @Test
    fun theCursorGoesAfterTheReplacementAndUndoPutsItAtTheHead() = runTest {
        load(text("one two"), text("three four"))
        select(point(0, 4), point(1, 6))
        val head = editor.textRange.ends.value!!.head

        editor.replaceTextRange("X")
        assertEquals(FocusRequest(editor.blocks[0].id, caret = 5), editor.selection.requests.first())

        val firstId = editor.blocks[0].id
        editor.undo()
        // Undone, the cursor is back at the end of the selection, in the block it was in.
        assertEquals(FocusRequest(head.blockId, caret = head.offset), editor.selection.requests.first())
        assertEquals(2, editor.blocks.size)
        editor.redo()
        assertEquals(FocusRequest(firstId, caret = 5), editor.selection.requests.first())
    }

    @Test
    fun theSelectionEndsWhenAFieldTakesFocusOrAnEditIsUndone() {
        load(text("a"), text("b"))

        select(point(0, 0), point(1, 1))
        editor.selection.onFocused(editor.blocks[0].id)
        assertFalse(editor.textRange.isActive)

        editor.addBlock(text("c"))
        select(point(0, 0), point(1, 1))
        editor.undo()
        assertFalse(editor.textRange.isActive)

        select(point(0, 0), point(1, 1))
        load(text("z"))
        assertFalse(editor.textRange.isActive)
    }

    @Test
    fun aSelectionWhoseAnchorBlockWasRemovedResolvesToNothing() {
        load(text("a"), text("b"), text("c"))
        select(point(0, 0), point(2, 1))

        editor.removeBlock(editor.blocks[0], BlockRemoveAction.REMOVE, refocus = false)

        assertEquals(0, editor.textRange.blockCount())
    }

    @Test
    fun aLoneHighSurrogateIsNotTakenForHalfOfAPair() {
        load(text("a\uD83Db"))

        select(point(0, 2), point(0, 3))

        assertEquals("b", editor.textRangePlainText())
    }

    @Test
    fun twoBlocksWithNoTextBecomeAParagraphOfTheReplacement() {
        load(Block.SeparatorBlock(), Block.LinkBlock(), text("z"))
        select(point(0, 0), point(1, 0))

        editor.replaceTextRange("new")

        assertEquals(listOf("p:new", "p:z"), shown())
        editor.undo()
        assertEquals(listOf("SeparatorBlock", "LinkBlock", "p:z"), shown())
    }
}
