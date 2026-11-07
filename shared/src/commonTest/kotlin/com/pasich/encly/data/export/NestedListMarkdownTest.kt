package com.pasich.encly.data.export

import com.pasich.encly.editor.codec.MarkdownBlockMapper
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.utils.NotesTextFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/** Nested lists out as Markdown and plain text, and back in again. */
class NestedListMarkdownTest {
    private fun row(text: String, level: Int = 0, kind: BlockType? = null, check: Boolean = false) =
        ItemListBlock(text, isCheck = check, level = level, kind = kind)

    private fun list(type: BlockType, vararg rows: ItemListBlock) =
        Block.ListBlock(MutableStateFlow(rows.toList()), type)

    private fun render(vararg blocks: Block) = MarkdownBlocks.render(blocks.toList())

    private fun levels(block: Block) = (block as Block.ListBlock).items.value.map { it.value to it.level }

    @Test
    fun aBulletedListIndentsEachLevelUnderTheTextOfItsParent() {
        val bullets = list(BlockType.LIST_BULLET, row("a"), row("b", 1), row("c", 2), row("d", 1), row("e"))

        assertEquals("- a\n  - b\n    - c\n  - d\n- e", render(bullets))
    }

    @Test
    fun aNumberedListRestartsItsNumbersAtEveryLevelAndIndentsByTheWidthOfTheMarker() {
        val numbers = list(BlockType.LIST_NUMBER, row("a"), row("b", 1), row("c", 1), row("d"), row("e", 1))

        assertEquals("1. a\n   1. b\n   2. c\n2. d\n   1. e", render(numbers))
    }

    @Test
    fun aNestedItemMayBeOfAnotherKindThanItsList() {
        val mixed = list(
            BlockType.LIST_BULLET,
            row("a"),
            row("b", 1, BlockType.LIST_NUMBER),
            row("c", 1, BlockType.LIST_NUMBER),
            row("d", 1, BlockType.LIST_CHECK, check = true),
            row("e", 2, BlockType.LIST_CHECK),
        )

        assertEquals("- a\n  1. b\n  2. c\n  - [x] d\n    - [ ] e", render(mixed))
    }

    @Test
    fun theLinesOfANestedItemAreIndentedUnderItsText() {
        val bullets = list(BlockType.LIST_BULLET, row("a"), row("b\nsecond line", 1))

        assertEquals("- a\n  - b\\\n    second line", render(bullets))
    }

    @Test
    fun aBlankItemIsLeftOutAndItsChildrenBecomeValid() {
        val bullets = list(BlockType.LIST_BULLET, row("a"), row(" ", 1), row("c", 2))

        assertEquals("- a\n  - c", render(bullets))
    }

    @Test
    fun aFlatListIsWrittenAsBefore() {
        assertEquals(
            "- [x] a\n- [ ] b",
            render(list(BlockType.LIST_CHECK, row("a", check = true), row("b"))),
        )
    }

    @Test
    fun whatIsExportedReadsBackAsTheSameLevelsAndKinds() {
        val original = list(
            BlockType.LIST_NUMBER,
            row("a"),
            row("b", 1, BlockType.LIST_BULLET),
            row("c", 2, BlockType.LIST_CHECK, check = true),
            row("d", 2, BlockType.LIST_CHECK),
            row("e", 1, BlockType.LIST_BULLET),
            row("f"),
            row("g", 1),
        )

        val back = MarkdownBlockMapper.toBlocks(render(original)).single() as Block.ListBlock

        assertEquals(BlockType.LIST_NUMBER, back.blockType)
        assertEquals(original.items.value.map { it.value to it.level }, levels(back))
        assertEquals(original.items.value.map { it.kind }, back.items.value.map { it.kind })
        assertEquals(original.items.value.map { it.isCheck }, back.items.value.map { it.isCheck })
    }

    @Test
    fun aNestedChecklistRoundTrips() {
        val original = list(BlockType.LIST_CHECK, row("a", check = true), row("b", 1), row("c", 1, check = true))

        val back = MarkdownBlockMapper.toBlocks(render(original)).single() as Block.ListBlock

        assertEquals(listOf("a" to 0, "b" to 1, "c" to 1), levels(back))
        assertEquals(listOf(true, false, true), back.items.value.map { it.isCheck })
    }

    @Test
    fun plainTextIndentsNestedItemsTwoSpacesALevel() {
        val numbers = list(BlockType.LIST_NUMBER, row("a"), row("b", 1), row("c", 1), row("d"))
        val checks = list(BlockType.LIST_CHECK, row("x", check = true), row("y", 1))
        val mixed = list(BlockType.LIST_BULLET, row("p"), row("q", 1, BlockType.LIST_NUMBER))

        assertEquals(
            "1. a\n  1. b\n  2. c\n2. d\n[x] x\n  [ ] y\n- p\n  1. q",
            NotesTextFormatter.blocksToPlainText(listOf(numbers, checks, mixed)),
        )
    }

    @Test
    fun theSearchTextOfANestedListIsItsItemsInOrder() {
        val bullets = list(BlockType.LIST_BULLET, row("a"), row("b", 1))

        assertEquals("a\nb", NotesTextFormatter.blocksToSearchText(listOf(bullets)))
    }
}
