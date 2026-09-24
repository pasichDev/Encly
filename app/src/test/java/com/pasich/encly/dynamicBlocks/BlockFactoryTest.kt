package com.pasich.encly.dynamicBlocks

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.factory.BlockFactory
import com.pasich.encly.dynamicBlocks.utils.BlockUtils
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
            if (type != BlockType.SEPARATOR) assertTrue("$type starts empty", BlockUtils.isBlockEmpty(block))
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
