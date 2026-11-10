package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A text selection that starts or ends inside a list item, where it covers, moves and is replaced. */
class TextRangeListsTest {
    private val editor = BlockEditorState { 0L }

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun list(vararg items: String) =
        Block.ListBlock(MutableStateFlow(items.map { ItemListBlock(it) }), BlockType.LIST_BULLET)

    private fun load(vararg blocks: Block) = editor.load(blocks.toList())

    private fun item(list: Block.ListBlock, at: Int, offset: Int) = TextPoint(list.id, offset, list.items.value[at].id)

    private fun shown() = editor.blocks.map { block ->
        when (block) {
            is Block.TextBlock -> "p:" + block.text.value
            is Block.ListBlock -> "l:" + block.items.value.joinToString("|") { it.value }
            else -> block::class.simpleName.orEmpty()
        }
    }

    // --- replacing ---------------------------------------------------------------------------------

    @Test
    fun aSelectionInsideOneListJoinsTheItemsItRunsOver() {
        val bullets = list("one", "two", "three", "four")
        load(bullets)
        editor.textRange.select(item(bullets, 0, 2), item(bullets, 2, 3))

        assertTrue(editor.replaceTextRange("-"))

        assertEquals(listOf("l:on-ee|four"), shown())
        editor.undo()
        assertEquals(listOf("l:one|two|three|four"), shown())
    }

    @Test
    fun aListThatTheSelectionStartsInKeepsItsEarlierItemsAndTakesTheRestOfTheLastBlock() {
        val bullets = list("one", "two")
        load(text("intro"), bullets, text("middle"), text("tail end"))
        editor.textRange.select(item(bullets, 1, 1), TextPoint(editor.blocks[3].id, 5))

        editor.replaceTextRange("")

        assertEquals(listOf("p:intro", "l:one|tend"), shown())
    }

    @Test
    fun aListThatTheSelectionStartsInAbsorbsTheItemsAfterTheEndOfAnotherList() {
        val first = list("one", "two")
        val second = list("red", "green", "blue")
        load(first, text("between"), second)
        editor.textRange.select(item(first, 1, 1), item(second, 1, 2))

        editor.replaceTextRange("")

        assertEquals(listOf("l:one|teen|blue"), shown())
        editor.undo()
        assertEquals(listOf("l:one|two", "p:between", "l:red|green|blue"), shown())
    }

    @Test
    fun aParagraphThatTheSelectionStartsInLeavesTheRestOfTheListItEndsIn() {
        val bullets = list("one", "two", "three")
        load(text("start here"), text("middle"), bullets)
        editor.textRange.select(TextPoint(editor.blocks[0].id, 5), item(bullets, 0, 2))

        editor.replaceTextRange("")

        assertEquals(listOf("p:starte", "l:two|three"), shown())
        editor.undo()
        assertEquals(listOf("p:start here", "p:middle", "l:one|two|three"), shown())
    }

    @Test
    fun aParagraphThatTheSelectionStartsInTakesAListEndedOnItsLastItem() {
        val bullets = list("one", "two")
        load(text("abc"), bullets)
        editor.textRange.select(TextPoint(editor.blocks[0].id, 1), item(bullets, 1, 1))

        editor.replaceTextRange("X")

        assertEquals(listOf("p:aXwo"), shown())
    }

    @Test
    fun aListEndedOnWithNoParagraphBeforeItKeepsItsKind() {
        val bullets = list("one", "two", "three")
        load(Block.SeparatorBlock(), bullets)
        editor.textRange.select(TextPoint(editor.blocks[0].id, 0), item(bullets, 1, 1))

        editor.replaceTextRange("Q")

        assertEquals(listOf("l:Qwo|three"), shown())
    }

    @Test
    fun theCursorGoesIntoTheItemThatHoldsTheResult() {
        val bullets = list("one", "two")
        load(bullets, text("tail"))
        editor.textRange.select(item(bullets, 1, 1), TextPoint(editor.blocks[1].id, 2))

        editor.replaceTextRange("")

        assertEquals(bullets.id, editor.selection.focusedBlockId.value)
        assertEquals(bullets.items.value[1].id, editor.selection.itemOf(bullets.id))
    }

    // --- what it covers ----------------------------------------------------------------------------

    @Test
    fun theCoverOfAFieldIsTheWholeOfItsMiddleAndTheCutOfItsEnds() {
        val bullets = list("one", "two", "three")
        load(text("abc"), bullets, text("def"))
        editor.textRange.select(TextPoint(editor.blocks[0].id, 1), TextPoint(editor.blocks[2].id, 2))

        assertEquals(1 to 3, editor.textRange.coverOf(editor.blocks[0].id, null, 3))
        assertEquals(0 to 3, editor.textRange.coverOf(bullets.id, bullets.items.value[0].id, 3))
        assertEquals(0 to 5, editor.textRange.coverOf(bullets.id, bullets.items.value[2].id, 5))
        assertEquals(0 to 2, editor.textRange.coverOf(editor.blocks[2].id, null, 3))
    }

    @Test
    fun aListItemOutsideTheSelectionIsNotCovered() {
        val bullets = list("one", "two", "three")
        load(bullets, text("tail"))
        editor.textRange.select(item(bullets, 1, 1), TextPoint(editor.blocks[1].id, 2))

        assertNull(editor.textRange.coverOf(bullets.id, bullets.items.value[0].id, 3))
        assertEquals(1 to 3, editor.textRange.coverOf(bullets.id, bullets.items.value[1].id, 3))
        assertNull(editor.textRange.coverOf("gone", null, 3))
    }

    @Test
    fun withoutASelectionNothingIsCovered() {
        load(text("abc"))

        assertNull(editor.textRange.coverOf(editor.blocks[0].id, null, 3))
    }

    @Test
    fun theCoverStaysInsideTheFieldAndAnEmptyOneHasNone() {
        load(text("abc"), text(""))
        editor.textRange.select(TextPoint(editor.blocks[0].id, 1), TextPoint(editor.blocks[1].id, 0))

        assertEquals(0 to 0, editor.textRange.coverOf(editor.blocks[1].id, null, 0))
        assertEquals(1 to 3, editor.textRange.coverOf(editor.blocks[0].id, null, 3))
    }

    @Test
    fun theEndsOfASelectionAreOrderedPointsWithTheirItems() {
        val bullets = list("one", "two")
        load(bullets, text("tail"))
        editor.textRange.select(TextPoint(editor.blocks[1].id, 2), item(bullets, 1, 1))

        val (from, to) = editor.textRange.orderedPoints()!!

        assertEquals(item(bullets, 1, 1), from)
        assertEquals(TextPoint(editor.blocks[1].id, 2), to)
    }

    @Test
    fun anUnnamedListPointStartsInTheFirstItemAndEndsInTheLast() {
        val bullets = list("one", "two")
        load(bullets)
        editor.textRange.select(TextPoint(bullets.id, 1), TextPoint(bullets.id, 99))

        val (from, to) = editor.textRange.orderedPoints()!!

        assertEquals(item(bullets, 0, 1), from)
        assertEquals(item(bullets, 1, 3), to)
    }

    @Test
    fun theEndsAreNotPointsWhereTheSelectionEndsInABlockWithNoText() {
        load(text("a"), Block.SeparatorBlock())
        assertNull(editor.textRange.orderedPoints())

        editor.textRange.select(TextPoint(editor.blocks[0].id, 0), TextPoint(editor.blocks[1].id, 0))
        assertNull(editor.textRange.orderedPoints())

        editor.textRange.select(TextPoint(editor.blocks[1].id, 0), TextPoint(editor.blocks[0].id, 1))
        assertNull(editor.textRange.orderedPoints())
    }

    // --- moving the end ----------------------------------------------------------------------------

    private fun head() = editor.textRange.ends.value!!.head

    private fun startAt(index: Int, offset: Int) = editor.textRange.select(
        TextPoint(editor.blocks[index].id, offset),
        TextPoint(editor.blocks[index].id, offset),
    )

    @Test
    fun theEndMovesByCharactersAndCrossesFromFieldToField() {
        load(text("ab"), text("cd"))
        startAt(0, 1)

        editor.textRange.moveHead(HeadMove.NEXT)
        assertEquals(TextPoint(editor.blocks[0].id, 2), head())
        editor.textRange.moveHead(HeadMove.NEXT)
        assertEquals(TextPoint(editor.blocks[1].id, 0), head())
        editor.textRange.moveHead(HeadMove.PREVIOUS)
        assertEquals(TextPoint(editor.blocks[0].id, 2), head())
        editor.textRange.moveHead(HeadMove.PREVIOUS)
        assertEquals(TextPoint(editor.blocks[0].id, 1), head())
    }

    @Test
    fun theEndStopsAtTheEdgesOfTheNote() {
        load(text("ab"), text("cd"))
        startAt(0, 0)
        editor.textRange.moveHead(HeadMove.PREVIOUS)
        assertEquals(TextPoint(editor.blocks[0].id, 0), head())

        startAt(1, 2)
        editor.textRange.moveHead(HeadMove.NEXT)
        assertEquals(TextPoint(editor.blocks[1].id, 2), head())
    }

    @Test
    fun aSurrogatePairIsOneStep() {
        load(text("a😀b"))
        startAt(0, 1)

        editor.textRange.moveHead(HeadMove.NEXT)
        assertEquals(3, head().offset)
        editor.textRange.moveHead(HeadMove.PREVIOUS)
        assertEquals(1, head().offset)
    }

    @Test
    fun upAndDownKeepTheOffsetAndStopAtTheEnds() {
        load(text("abcdef"), text("xy"), text("klmnop"))
        startAt(1, 2)

        editor.textRange.moveHead(HeadMove.UP)
        assertEquals(TextPoint(editor.blocks[0].id, 2), head())
        editor.textRange.moveHead(HeadMove.UP)
        assertEquals(TextPoint(editor.blocks[0].id, 0), head())

        startAt(1, 1)
        editor.textRange.moveHead(HeadMove.DOWN)
        assertEquals(TextPoint(editor.blocks[2].id, 1), head())
        editor.textRange.moveHead(HeadMove.DOWN)
        assertEquals(TextPoint(editor.blocks[2].id, 6), head())
    }

    @Test
    fun homeEndAndThePageAndNoteJumps() {
        val blocks = List(8) { text("line$it") }
        load(*blocks.toTypedArray())
        startAt(3, 2)

        editor.textRange.moveHead(HeadMove.FIELD_START)
        assertEquals(TextPoint(editor.blocks[3].id, 0), head())
        editor.textRange.moveHead(HeadMove.FIELD_END)
        assertEquals(TextPoint(editor.blocks[3].id, 5), head())
        editor.textRange.moveHead(HeadMove.PAGE_DOWN)
        assertEquals(TextPoint(editor.blocks[7].id, 5), head())
        editor.textRange.moveHead(HeadMove.PAGE_UP)
        assertEquals(TextPoint(editor.blocks[2].id, 5), head())
        editor.textRange.moveHead(HeadMove.NOTE_END)
        assertEquals(TextPoint(editor.blocks[7].id, 5), head())
        editor.textRange.moveHead(HeadMove.NOTE_START)
        assertEquals(TextPoint(editor.blocks[0].id, 0), head())
    }

    @Test
    fun theEndMovesAcrossListItemsAndPastBlocksWithNoText() {
        val bullets = list("one", "two")
        load(text("a"), Block.SeparatorBlock(), bullets)
        startAt(0, 1)

        editor.textRange.moveHead(HeadMove.NEXT)
        assertEquals(item(bullets, 0, 0), head())
        editor.textRange.moveHead(HeadMove.DOWN)
        assertEquals(item(bullets, 1, 0), head())
        editor.textRange.moveHead(HeadMove.UP)
        editor.textRange.moveHead(HeadMove.UP)
        assertEquals(TextPoint(editor.blocks[0].id, 0), head())
    }

    @Test
    fun anUnnamedListEndMovesFromItsLastItem() {
        val bullets = list("one", "two")
        load(text("a"), bullets)
        editor.textRange.select(TextPoint(editor.blocks[0].id, 0), TextPoint(bullets.id, 1))

        editor.textRange.moveHead(HeadMove.PREVIOUS)

        assertEquals(item(bullets, 1, 0), head())
    }

    @Test
    fun movingWithoutASelectionOrFromABlockWithNoTextDoesNothing() {
        load(text("a"), Block.SeparatorBlock())
        editor.textRange.moveHead(HeadMove.NEXT)
        assertFalse(editor.textRange.isActive)

        editor.textRange.select(TextPoint(editor.blocks[0].id, 0), TextPoint(editor.blocks[1].id, 0))
        editor.textRange.moveHead(HeadMove.NEXT)
        assertEquals(TextPoint(editor.blocks[1].id, 0), head())
    }

    // --- Shift plus a key in a field ---------------------------------------------------------------

    private fun extend(move: HeadMove, index: Int, from: Int, to: Int, edge: Boolean = false): Boolean {
        val id = editor.blocks[index].id
        return editor.textRange.extend(move, TextPoint(id, from), TextPoint(id, to), edge)
    }

    @Test
    fun aKeyThatStaysInTheFieldIsLeftToTheField() {
        load(text("abc"), text("def"))

        assertFalse(extend(HeadMove.PREVIOUS, 1, 2, 2))
        assertFalse(extend(HeadMove.NEXT, 0, 0, 1))
        assertFalse(extend(HeadMove.UP, 1, 0, 0, edge = false))
        assertFalse(extend(HeadMove.DOWN, 0, 0, 0, edge = false))
        assertFalse(extend(HeadMove.FIELD_START, 0, 1, 1))
        assertFalse(extend(HeadMove.FIELD_END, 0, 1, 1))
        assertFalse(editor.textRange.isActive)
    }

    @Test
    fun aKeyThatLeavesTheFieldStartsASelectionFromTheFieldsOwn() {
        load(text("abc"), text("def"))

        assertTrue(extend(HeadMove.NEXT, 0, 1, 3))

        assertEquals(TextPoint(editor.blocks[0].id, 1), editor.textRange.ends.value!!.anchor)
        assertEquals(TextPoint(editor.blocks[1].id, 0), head())
        assertTrue(editor.textRange.isCrossField)
    }

    @Test
    fun theKeysAtTheStartAndTheEdgeLinesLeaveToo() {
        load(text("abc"), text("def"), text("ghi"))

        assertTrue(extend(HeadMove.PREVIOUS, 1, 0, 0))
        editor.textRange.clear()
        assertTrue(extend(HeadMove.UP, 1, 1, 1, edge = true))
        editor.textRange.clear()
        assertTrue(extend(HeadMove.DOWN, 1, 1, 1, edge = true))
        editor.textRange.clear()
        assertTrue(extend(HeadMove.PAGE_UP, 1, 1, 1))
        editor.textRange.clear()
        assertTrue(extend(HeadMove.NOTE_END, 1, 1, 1))
    }

    @Test
    fun theFirstAndLastFieldOfTheNoteHaveNowhereToGo() {
        load(text("abc"), text("def"))

        assertFalse(extend(HeadMove.PREVIOUS, 0, 0, 0))
        assertFalse(extend(HeadMove.UP, 0, 0, 0, edge = true))
        assertFalse(extend(HeadMove.NEXT, 1, 3, 3))
        assertFalse(extend(HeadMove.DOWN, 1, 3, 3, edge = true))
    }

    @Test
    fun onceTheSelectionRunsAcrossFieldsEveryKeyMovesItsEnd() {
        load(text("abc"), text("def"))
        assertTrue(extend(HeadMove.NEXT, 0, 3, 3))

        assertTrue(extend(HeadMove.FIELD_END, 1, 0, 0))

        assertEquals(TextPoint(editor.blocks[1].id, 3), head())
    }

    @Test
    fun aCaretInABlockThatIsNotInTheNoteLeavesNothing() {
        load(text("a"))
        val gone = TextPoint("gone", 0)

        for (move in HeadMove.entries) {
            assertFalse(editor.textRange.extend(move, gone, gone, onEdgeLine = true), move.name)
        }
        assertFalse(editor.textRange.isActive)
    }

    // --- a list with no items ----------------------------------------------------------------------------

    @Test
    fun aListWithNoItemsHasNothingToSelectInAndIsLeftWhole() {
        val empty = Block.ListBlock(MutableStateFlow(emptyList()), BlockType.LIST_BULLET)
        load(text("abc"), empty, text("def"))
        editor.textRange.select(TextPoint(editor.blocks[0].id, 1), TextPoint(empty.id, 0))

        assertEquals(TextPoint(empty.id, 0), editor.textRange.orderedPoints()!!.second)
        assertEquals("bc", editor.textRangePlainText())
        assertEquals(0 to 0, editor.textRange.coverOf(empty.id, "none", 0))

        editor.replaceTextRange("X")
        assertEquals(listOf("p:aX", "p:def"), shown())
    }

    @Test
    fun aSelectionThatStartsInAListWithNoItemsIsReplacedByTheParagraphAfterIt() {
        val empty = Block.ListBlock(MutableStateFlow(emptyList()), BlockType.LIST_BULLET)
        load(empty, text("def"))
        editor.textRange.select(TextPoint(empty.id, 0), TextPoint(editor.blocks[1].id, 1))

        assertEquals("d", editor.textRangePlainText())
        assertEquals(0 to 1, editor.textRange.coverOf(editor.blocks[1].id, null, 3))
    }
}
