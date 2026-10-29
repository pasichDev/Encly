package com.pasich.encly.editor.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BlockFactoryTest {

    @Test
    fun everyTypeGetsAnEmptyBlockOfThatType() {
        BlockType.entries.forEach { type ->
            val block = BlockFactory.createBlock(type)
            val created = when (block) {
                is Block.TextBlock -> BlockType.TEXT
                is Block.HBlock -> block.blockType
                is Block.QuoteBlock -> BlockType.QUOTE
                is Block.LinkBlock -> BlockType.LINK
                is Block.SeparatorBlock -> BlockType.SEPARATOR
                is Block.ListBlock -> block.blockType
            }
            assertEquals(type, created)
            if (type != BlockType.SEPARATOR) assertTrue(BlockUtils.isBlockEmpty(block), "$type starts empty")
        }
    }

    @Test
    fun eachNewBlockHasItsOwnId() {
        assertNotEquals(BlockFactory.createBlock(BlockType.TEXT).id, BlockFactory.createBlock(BlockType.TEXT).id)
    }

    @Test
    fun aListStartsWithOneEmptyItemToTypeInto() {
        val list = BlockFactory.createBlock(BlockType.LIST_CHECK) as Block.ListBlock

        assertEquals(1, list.items.value.size)
        assertFalse(list.items.value.single().isCheck)
    }

    @Test
    fun emptinessPerBlockKind() {
        assertFalse(BlockUtils.isBlockEmpty(Block.TextBlock(MutableStateFlow("x"))))
        assertFalse(BlockUtils.isBlockEmpty(Block.QuoteBlock(MutableStateFlow("q"))))
        assertFalse(BlockUtils.isBlockEmpty(Block.LinkBlock(MutableStateFlow(LinkDataBlock(url = "https://a.b")))))
        assertTrue(BlockUtils.isBlockEmpty(Block.ListBlock(MutableStateFlow(emptyList()), BlockType.LIST_BULLET)))
        assertFalse(
            BlockUtils.isBlockEmpty(
                Block.ListBlock(MutableStateFlow(listOf(ItemListBlock(""), ItemListBlock(""))), BlockType.LIST_BULLET),
            ),
        )
        assertFalse(BlockUtils.isBlockEmpty(Block.SeparatorBlock()))
    }

    @Test
    fun onlyBlocksWithContentAreStored() {
        assertFalse(Block.TextBlock(MutableStateFlow("   ")).hasContent())
        assertTrue(Block.HBlock(MutableStateFlow("Title"), BlockType.H2).hasContent())
        assertFalse(Block.ListBlock(MutableStateFlow(listOf(ItemListBlock(" "))), BlockType.LIST_CHECK).hasContent())
        assertTrue(Block.ListBlock(MutableStateFlow(listOf(ItemListBlock("milk"))), BlockType.LIST_CHECK).hasContent())
        assertFalse(Block.LinkBlock().hasContent())
        assertTrue(Block.SeparatorBlock().hasContent())
    }
}
