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

/** Indenting, outdenting and moving list items, and what writing in a nested list does; each is one undo step. */
class ListNestingTest {
    private val editor = BlockEditorState { 0L }
    private val enter = LineBreak("", listOf("", ""), "")

    private fun enterAt(text: String, cursor: Int) = LineBreak(text.take(cursor), listOf("", ""), text.drop(cursor))

    private fun row(text: String, level: Int = 0, kind: BlockType? = null, check: Boolean = false) =
        ItemListBlock(text, isCheck = check, level = level, kind = kind)

    private fun list(type: BlockType = BlockType.LIST_BULLET, vararg rows: ItemListBlock) =
        Block.ListBlock(MutableStateFlow(rows.toList()), type)

    private fun Block.ListBlock.shape() = items.value.joinToString(" ") { "${it.value}${it.level}" }

    private fun Block.ListBlock.id(text: String) = items.value.first { it.value == text }.id

    private fun Block.ListBlock.at(text: String) = items.value.first { it.value == text }

    // a0 b1 c2 d1 e0
    private fun tree(type: BlockType = BlockType.LIST_BULLET) =
        list(type, row("a"), row("b", 1), row("c", 2), row("d", 1), row("e"))

    // --- indent and outdent -----------------------------------------------------------------------

    @Test
    fun anItemIsIndentedUnderTheOneAboveAsOneUndoStep() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b"), row("c"))
        editor.load(listOf(list))

        assertTrue(editor.shiftListItem(list.id, list.id("b"), deeper = true))

        assertEquals("a0 b1 c0", list.shape())
        editor.undo()
        assertEquals("a0 b0 c0", list.shape())
        assertFalse(editor.canUndo.value)
        editor.redo()
        assertEquals("a0 b1 c0", list.shape())
    }

    @Test
    fun theFirstItemCannotBeIndentedAndNothingIsRecorded() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b"))
        editor.load(listOf(list))

        assertFalse(editor.canShiftListItem(list.id, list.id("a"), deeper = true))
        assertFalse(editor.shiftListItem(list.id, list.id("a"), deeper = true))

        assertEquals("a0 b0", list.shape())
        assertFalse(editor.canUndo.value)
    }

    @Test
    fun anItemIsIndentedAtMostThreeLevelsDeepAndOneDeeperThanTheItemAbove() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b"), row("c"), row("d"))
        editor.load(listOf(list))

        assertTrue(editor.shiftListItem(list.id, list.id("b"), deeper = true))
        assertTrue(editor.shiftListItem(list.id, list.id("c"), deeper = true))
        assertTrue(editor.shiftListItem(list.id, list.id("c"), deeper = true))
        // c is now two levels in; a third level is not offered, and d may only follow c to level 2.
        assertFalse(editor.shiftListItem(list.id, list.id("c"), deeper = true))
        assertTrue(editor.shiftListItem(list.id, list.id("d"), deeper = true))
        assertTrue(editor.shiftListItem(list.id, list.id("d"), deeper = true))
        assertFalse(editor.shiftListItem(list.id, list.id("d"), deeper = true))

        assertEquals("a0 b1 c2 d2", list.shape())
    }

    @Test
    fun anItemMovesWithItsChildren() {
        val list = tree()
        editor.load(listOf(list))

        editor.shiftListItem(list.id, list.id("e"), deeper = true)
        editor.shiftListItem(list.id, list.id("b"), deeper = false)

        assertEquals("a0 b0 c1 d1 e1", list.shape())
    }

    @Test
    fun anItemAtTheTopCannotComeOut() {
        val list = tree()
        editor.load(listOf(list))

        assertFalse(editor.canShiftListItem(list.id, list.id("a"), deeper = false))
        assertFalse(editor.shiftListItem(list.id, list.id("a"), deeper = false))
        assertEquals("a0 b1 c2 d1 e0", list.shape())
    }

    @Test
    fun anUnknownListOrItemChangesNothing() {
        val list = tree()
        editor.load(listOf(list))

        assertFalse(editor.shiftListItem("nope", list.id("b"), deeper = false))
        assertFalse(editor.shiftListItem(list.id, "nope", deeper = false))
        assertFalse(editor.moveListItem(list.id, "nope", 1))
        assertFalse(editor.setNestedListKind(list.id, "nope", BlockType.LIST_CHECK))
        assertFalse(editor.canMoveListItem(list.id, "nope", 1))
        assertFalse(editor.canShiftListItem(list.id, "nope", deeper = true))
        assertFalse(editor.canUndo.value)
    }

    @Test
    fun theFocusedItemKeepsItsFocusAndCursorWhenItIsShifted() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("hello"))
        editor.load(listOf(list))
        val item = list.id("hello")
        editor.selection.onFocused(list.id)
        editor.selection.onItemCaret(list.id, item, 3)

        editor.shiftListItem(list.id, item, deeper = true)

        assertEquals(item, editor.selection.itemOf(list.id))
        assertEquals(3, editor.selection.caretOf(list.id))
    }

    @Test
    fun anItemThatIsNotBeingWrittenInIsShiftedWithoutTakingFocus() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b"))
        editor.load(listOf(list))

        editor.shiftListItem(list.id, list.id("b"), deeper = true)

        assertNull(editor.selection.focusedBlockId.value)
        assertNull(editor.selection.itemOf(list.id))
    }

    @Test
    fun undoingAShiftPutsTheCursorBackInTheItem() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("hello"))
        editor.load(listOf(list))
        val item = list.id("hello")
        editor.selection.onFocused(list.id)
        editor.selection.onItemCaret(list.id, item, 2)
        editor.shiftListItem(list.id, item, deeper = true)

        editor.undo()

        assertEquals(item, editor.selection.itemOf(list.id))
        assertEquals("a0 hello0", list.shape())
    }

    // --- moving -----------------------------------------------------------------------------------

    @Test
    fun anItemMovesDownAndUpPastItsSiblingsAsOneUndoStepEach() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b"), row("c"))
        editor.load(listOf(list))

        assertTrue(editor.canMoveListItem(list.id, list.id("a"), 1))
        assertTrue(editor.moveListItem(list.id, list.id("a"), 1))
        assertEquals(listOf("b", "a", "c"), list.items.value.map { it.value })
        assertTrue(editor.moveListItem(list.id, list.id("c"), -2))
        assertEquals(listOf("c", "b", "a"), list.items.value.map { it.value })

        editor.undo()
        assertEquals(listOf("b", "a", "c"), list.items.value.map { it.value })
        editor.undo()
        assertEquals(listOf("a", "b", "c"), list.items.value.map { it.value })
        assertFalse(editor.canUndo.value)
        editor.redo()
        editor.redo()
        assertEquals(listOf("c", "b", "a"), list.items.value.map { it.value })
    }

    @Test
    fun aMovedItemTakesItsChildrenAlong() {
        val list = tree()
        editor.load(listOf(list))

        assertTrue(editor.moveListItem(list.id, list.id("a"), 1))

        assertEquals("e0 a0 b1 c2 d1", list.shape())
        editor.undo()
        assertEquals("a0 b1 c2 d1 e0", list.shape())
    }

    @Test
    fun anItemCannotMoveOutOfItsParentOrPastTheEnds() {
        val list = tree()
        editor.load(listOf(list))

        assertFalse(editor.canMoveListItem(list.id, list.id("a"), -1))
        assertFalse(editor.moveListItem(list.id, list.id("a"), -1))
        assertFalse(editor.moveListItem(list.id, list.id("e"), 1))
        // c is the only child of b.
        assertFalse(editor.moveListItem(list.id, list.id("c"), 1))
        assertFalse(editor.moveListItem(list.id, list.id("c"), -1))
        assertFalse(editor.moveListItem(list.id, list.id("b"), 0))
        assertFalse(editor.canUndo.value)
    }

    @Test
    fun theIdsOfMovedItemsAreKeptSoTheirFieldsFollowThem() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b"))
        editor.load(listOf(list))
        val ids = list.items.value.map { it.id }

        editor.moveListItem(list.id, ids[0], 1)

        assertEquals(listOf(ids[1], ids[0]), list.items.value.map { it.id })
    }

    // --- Enter ------------------------------------------------------------------------------------

    @Test
    fun enterInANestedItemStartsAnItemAtTheSameLevel() {
        val list = tree()
        editor.load(listOf(list))

        editor.listLineBreak(list.id, list.id("b"), enterAt("b", 1))

        assertEquals(listOf("a", "b", "", "c", "d", "e"), list.items.value.map { it.value })
        assertEquals(listOf(0, 1, 1, 2, 1, 0), list.items.value.map { it.level })
    }

    @Test
    fun enterInTheMiddleOfANestedItemKeepsTheLevelOfBothHalves() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("hello", 1, BlockType.LIST_CHECK))
        editor.load(listOf(list))

        editor.listLineBreak(list.id, list.id("hello"), enterAt("hello", 2))

        assertEquals(listOf("a", "he", "llo"), list.items.value.map { it.value })
        assertEquals(listOf(0, 1, 1), list.items.value.map { it.level })
        assertEquals(listOf(null, BlockType.LIST_CHECK, BlockType.LIST_CHECK), list.items.value.map { it.kind })
    }

    @Test
    fun enterAtTheStartOfANestedItemOpensAnItemAboveAtTheSameLevel() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b", 1))
        editor.load(listOf(list))

        editor.listLineBreak(list.id, list.id("b"), enterAt("b", 0))

        assertEquals(listOf("a", "", "b"), list.items.value.map { it.value })
        assertEquals(listOf(0, 1, 1), list.items.value.map { it.level })
    }

    @Test
    fun enterInAnEmptyNestedItemBringsItOutOneLevelInsteadOfLeavingTheList() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b", 1), row("", 2))
        editor.load(listOf(list))

        editor.listLineBreak(list.id, list.items.value[2].id, enter)
        assertEquals(listOf(0, 1, 1), list.items.value.map { it.level })
        editor.listLineBreak(list.id, list.items.value[2].id, enter)
        assertEquals(listOf(0, 1, 0), list.items.value.map { it.level })
        assertEquals(1, editor.blocks.size)
        editor.undo()
        assertEquals(listOf(0, 1, 1), list.items.value.map { it.level })
    }

    @Test
    fun enterInAnEmptyItemAtTheTopStillLeavesTheListAndTheRestKeepsItsShape() {
        val list = list(BlockType.LIST_BULLET, row("a"), row(""), row("b", 1), row("c", 2), row("d"))
        editor.load(listOf(list))

        editor.listLineBreak(list.id, list.items.value[1].id, enter)

        assertEquals(3, editor.blocks.size)
        assertEquals("a0", (editor.blocks[0] as Block.ListBlock).shape())
        // The list that continues starts at the top: b was a child of the item that left.
        assertEquals("b0 c1 d0", (editor.blocks[2] as Block.ListBlock).shape())
    }

    // --- Backspace --------------------------------------------------------------------------------

    @Test
    fun backspaceAtTheStartOfANestedItemBringsItOutBeforeAnythingIsJoined() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b", 1), row("c", 2))
        editor.load(listOf(list))

        assertTrue(editor.listBackspaceAtStart(list.id, list.id("b")))
        assertEquals("a0 b0 c1", list.shape())
        assertEquals(listOf("a", "b", "c"), list.items.value.map { it.value })

        // Now at the top, the next Backspace joins it to the item above.
        assertTrue(editor.listBackspaceAtStart(list.id, list.id("b")))
        assertEquals(listOf("ab", "c"), list.items.value.map { it.value })
        assertEquals("ab0 c1", list.shape())
    }

    @Test
    fun backspaceInANestedItemIsOneUndoStep() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b", 1))
        editor.load(listOf(list))

        editor.listBackspaceAtStart(list.id, list.id("b"))
        editor.undo()

        assertEquals("a0 b1", list.shape())
        assertFalse(editor.canUndo.value)
    }

    @Test
    fun anItemThatJoinsTheOneAboveTakesItsPlaceAndLevel() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b", 1), row("c", 2), row("d"))
        editor.load(listOf(list))

        editor.listBackspaceAtStart(list.id, list.id("d"))

        assertEquals(listOf("cd"), list.items.value.map { it.value }.drop(2))
        assertEquals("a0 b1 cd2", list.shape())
    }

    @Test
    fun theFirstItemStillLeavesTheListAsAParagraphAndItsChildrenStartTheListAtTheTop() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b", 1), row("c", 2))
        editor.load(listOf(list))

        editor.listBackspaceAtStart(list.id, list.id("a"))

        assertTrue(editor.blocks[0] is Block.TextBlock)
        assertEquals("b0 c1", (editor.blocks[1] as Block.ListBlock).shape())
    }

    // --- paste ------------------------------------------------------------------------------------

    @Test
    fun pastedLinesJoinTheListAtTheLevelOfTheItemTheyAreIn() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b", 1))
        editor.load(listOf(list))

        editor.listLineBreak(list.id, list.id("b"), LineBreak("b", listOf("", "- x", "- y"), ""))

        assertEquals("a0 b1 x1 y1", list.shape())
    }

    // --- kind of a nested list --------------------------------------------------------------------

    @Test
    fun aNestedItemAndItsSiblingsCanBecomeAListOfAnotherKindAsOneUndoStep() {
        val list = tree()
        editor.load(listOf(list))

        assertTrue(editor.setNestedListKind(list.id, list.id("b"), BlockType.LIST_CHECK))

        val check = BlockType.LIST_CHECK
        assertEquals(listOf(null, check, null, check, null), list.items.value.map { it.kind })
        editor.undo()
        assertEquals(listOf(null, null, null, null, null), list.items.value.map { it.kind })
    }

    @Test
    fun theKindOfATopItemIsTheListsAndAnythingButAListKindChangesNothing() {
        val list = tree()
        editor.load(listOf(list))

        assertFalse(editor.setNestedListKind(list.id, list.id("a"), BlockType.LIST_CHECK))
        assertFalse(editor.setNestedListKind(list.id, list.id("b"), BlockType.QUOTE))
        assertFalse(editor.setNestedListKind(list.id, list.id("b"), BlockType.LIST_BULLET))
        assertFalse(editor.canUndo.value)
    }

    @Test
    fun aListToolSetsTheKindOfTheNestedItemTheUserIsInAndNotOfTheWholeList() {
        val list = tree()
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)
        editor.selection.onItemCaret(list.id, list.id("d"), 0)

        editor.applyTool(BlockType.LIST_NUMBER)

        assertTrue(editor.blocks.single() === list)
        assertEquals(BlockType.LIST_BULLET, list.blockType)
        assertEquals(BlockType.LIST_NUMBER, list.at("d").kind)
        assertEquals(BlockType.LIST_NUMBER, list.at("b").kind)
        assertNull(list.at("e").kind)
    }

    @Test
    fun aListToolInAListWithoutAnItemTheUserWroteInConvertsTheWholeList() {
        val list = tree()
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)

        editor.applyTool(BlockType.LIST_NUMBER)

        assertEquals(BlockType.LIST_NUMBER, (editor.blocks.single() as Block.ListBlock).blockType)
    }

    @Test
    fun aListToolInATopItemConvertsTheWholeListAndDropsTheNestedKinds() {
        val list = list(BlockType.LIST_BULLET, row("a"), row("b", 1, BlockType.LIST_CHECK))
        editor.load(listOf(list))
        editor.selection.onFocused(list.id)
        editor.selection.onItemCaret(list.id, list.id("a"), 0)

        editor.applyTool(BlockType.LIST_NUMBER)

        val converted = editor.blocks.single() as Block.ListBlock
        assertEquals(BlockType.LIST_NUMBER, converted.blockType)
        assertEquals(listOf(0, 1), converted.items.value.map { it.level })
        assertEquals(listOf(null, null), converted.items.value.map { it.kind })
    }

    // --- the item the toolbar acts on -------------------------------------------------------------

    @Test
    fun theToolbarActsOnTheItemTheUserLastWroteInOfTheBlockTheyWorkOn() {
        val list = tree()
        val paragraph = Block.TextBlock(MutableStateFlow("p"))
        editor.load(listOf(paragraph, list))

        editor.selection.onFocused(paragraph.id)
        assertNull(editor.interactedListItem())

        editor.selection.onFocused(list.id)
        assertNull(editor.interactedListItem())
        editor.selection.onItemCaret(list.id, list.id("c"), 0)
        assertEquals(list.id to list.id("c"), editor.interactedListItem())

        // An item that is gone is not offered.
        editor.selection.onItemCaret(list.id, "gone", 0)
        assertNull(editor.interactedListItem())
    }

    // --- levels in storage that are not valid -----------------------------------------------------

    @Test
    fun anItemMovesByTheLevelsItIsShownAtWhenStoredOnesAreNotValid() {
        val list = list(BlockType.LIST_BULLET, row("a", 2), row("b", 2))
        editor.load(listOf(list))

        // Shown as a0 b1: b is a child of a, so it can come out but not go deeper.
        assertTrue(editor.canShiftListItem(list.id, list.id("b"), deeper = false))
        assertFalse(editor.canShiftListItem(list.id, list.id("b"), deeper = true))
        assertTrue(editor.shiftListItem(list.id, list.id("b"), deeper = false))
        assertEquals("a0 b0", list.shape())
    }

    @Test
    fun backspaceInTheFirstItemOfAListWithAStoredLevelStillLeavesTheList() {
        val list = list(BlockType.LIST_BULLET, row("a", 1), row("b"))
        editor.load(listOf(list))

        assertTrue(editor.listBackspaceAtStart(list.id, list.id("a")))

        assertTrue(editor.blocks[0] is Block.TextBlock)
    }
}
