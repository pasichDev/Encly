package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Text selection across blocks and block selection when a list has nested items. */
class TextRangeNestedTest {
    private val editor = BlockEditorState { 0L }

    private fun row(text: String, level: Int = 0, kind: BlockType? = null, check: Boolean = false) =
        ItemListBlock(text, isCheck = check, level = level, kind = kind)

    private fun list(type: BlockType, vararg rows: ItemListBlock) =
        Block.ListBlock(MutableStateFlow(rows.toList()), type)

    private fun text(value: String) = Block.TextBlock(MutableStateFlow(value))

    private fun point(list: Block.ListBlock, text: String, offset: Int) =
        TextPoint(list.id, offset, list.items.value.first { it.value == text }.id)

    private fun Block.ListBlock.shape() = items.value.joinToString(" ") { "${it.value}${it.level}" }

    // a0 b1 c2 d1 e0
    private fun tree(type: BlockType = BlockType.LIST_BULLET) =
        list(type, row("a"), row("b", 1), row("c", 2), row("d", 1), row("e"))

    @Test
    fun aSelectionRunsOverNestedItemsAsItDoesOverFlatOnes() {
        val list = tree()
        editor.load(listOf(list))
        editor.textRange.select(point(list, "b", 0), point(list, "d", 1))

        assertTrue(editor.textRange.isCrossField)
        val (from, to) = editor.textRange.orderedPoints() ?: error("selected")
        assertEquals(list.items.value[1].id, from.itemId)
        assertEquals(list.items.value[3].id, to.itemId)
        assertEquals(0 to 1, editor.textRange.coverOf(list.id, list.items.value[1].id, 1))
        assertEquals(0 to 1, editor.textRange.coverOf(list.id, list.items.value[2].id, 1))
        assertEquals(0 to 1, editor.textRange.coverOf(list.id, list.items.value[3].id, 1))
        assertNull(editor.textRange.coverOf(list.id, list.items.value[4].id, 1))
    }

    @Test
    fun theHeadMovesFromItemToItemThroughNestedOnes() {
        val list = tree()
        editor.load(listOf(list))
        editor.textRange.select(point(list, "a", 0), point(list, "a", 1))

        editor.textRange.moveHead(HeadMove.DOWN)
        editor.textRange.moveHead(HeadMove.DOWN)

        assertEquals(list.items.value[2].id, editor.textRange.ends.value?.head?.itemId)
        editor.textRange.moveHead(HeadMove.NOTE_END)
        assertEquals(list.items.value[4].id, editor.textRange.ends.value?.head?.itemId)
    }

    @Test
    fun theCopiedPartStartsAtTheTopLevelAndKeepsTheRestOfTheShape() {
        val list = tree()
        editor.load(listOf(list))
        editor.textRange.select(point(list, "c", 0), point(list, "e", 1))

        val copied = editor.textRangeBlocks().single() as Block.ListBlock

        assertEquals("c0 d1 e0", copied.shape())
        // Nothing is shared with the note.
        assertTrue(copied.items.value.none { copy -> list.items.value.any { it.id == copy.id } })
    }

    @Test
    fun theCopiedTextIndentsNestedItemsAndNumbersEachLevel() {
        val numbered = list(BlockType.LIST_NUMBER, row("a"), row("b", 1), row("c", 1), row("d"))
        editor.load(listOf(numbered))
        editor.textRange.select(point(numbered, "a", 0), point(numbered, "d", 1))

        assertEquals("1. a\n  1. b\n  2. c\n2. d", editor.textRangePlainText())
    }

    @Test
    fun theCopiedTextShowsTheKindOfANestedItem() {
        val bullets = list(
            BlockType.LIST_BULLET,
            row("a"),
            row("b", 1, BlockType.LIST_CHECK, check = true),
            row("c", 1, BlockType.LIST_CHECK),
            row("d", 1, BlockType.LIST_NUMBER),
        )
        editor.load(listOf(bullets))
        editor.textRange.selectAll()

        assertEquals("- a\n  [x] b\n  [ ] c\n  1. d", editor.textRangePlainText())
    }

    @Test
    fun replacingASelectionKeepsTheLevelOfTheItemItStartsIn() {
        val list = tree()
        editor.load(listOf(list))
        editor.textRange.select(point(list, "b", 1), point(list, "d", 0))

        assertTrue(editor.replaceTextRange("-"))

        assertEquals("a0 b-d1 e0", list.shape())
        assertEquals(listOf("a", "b-d", "e"), list.items.value.map { it.value })
        assertEquals(listOf(0, 1, 0), list.items.value.map { it.level })
        editor.undo()
        assertEquals("a0 b1 c2 d1 e0", list.shape())
        editor.redo()
        assertEquals(listOf(0, 1, 0), list.items.value.map { it.level })
    }

    @Test
    fun aSelectionThatEndsInTheMiddleOfNestedItemsLeavesTheRestAtValidLevels() {
        val list = tree()
        val intro = text("intro")
        editor.load(listOf(intro, list))
        // From the paragraph to the end of b: the rest (c, d, e) now follows a paragraph, so c starts at the top and
        // d stays its child.
        editor.textRange.select(TextPoint(intro.id, 2), point(list, "b", 1))

        assertTrue(editor.replaceTextRange(""))

        val rest = editor.blocks.last() as Block.ListBlock
        assertEquals("c0 d1 e0", rest.shape())
    }

    @Test
    fun aListThatTheSelectionEndsInKeepsItsTailAtValidLevels() {
        val list = tree()
        val intro = text("intro")
        editor.load(listOf(intro, list))
        editor.textRange.select(TextPoint(intro.id, 5), point(list, "b", 1))

        editor.replaceTextRange("!")

        // The paragraph's first block wins; the items after the selection stay as a list of their own.
        assertEquals("intro!", (editor.blocks.first() as Block.TextBlock).text.value)
        assertEquals("c0 d1 e0", (editor.blocks.last() as Block.ListBlock).shape())
    }

    @Test
    fun cuttingAWholeNestedListLeavesOneEmptyItem() {
        val list = tree()
        editor.load(listOf(list))
        editor.textRange.selectAll()

        assertTrue(editor.replaceTextRange(""))

        assertEquals(listOf(""), (editor.blocks.single() as Block.ListBlock).items.value.map { it.value })
    }

    // --- block selection --------------------------------------------------------------------------

    @Test
    fun aPickedNestedListIsDeletedDuplicatedAndCopiedWhole() {
        val list = tree()
        val after = text("after")
        editor.load(listOf(list, after))

        editor.duplicateBlockOf(list.id)

        val copy = editor.blocks[1] as Block.ListBlock
        assertEquals("a0 b1 c2 d1 e0", copy.shape())
        assertTrue(copy.items.value.none { item -> list.items.value.any { it.id == item.id } })

        editor.pickedBlocks.select(copy.id)
        assertEquals("- a\n  - b\n    - c\n  - d\n- e", listOf<Block>(copy).plainText())
        assertTrue(editor.deleteSelectedBlocks())
        assertEquals(2, editor.blocks.size)
    }

    @Test
    fun cuttingTheOnlyListOfANoteLeavesAnEmptyParagraph() {
        val list = tree()
        editor.load(listOf(list))
        editor.pickedBlocks.select(list.id)

        assertTrue(editor.deleteSelectedBlocks())

        assertTrue(editor.blocks.single() is Block.TextBlock)
    }
}
