package com.pasich.encly.dynamicBlocks.factory

import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType

/**
 * Factory for creating blocks of different types.
 * Uses the "Factory Method" pattern to unify block creation.
 */
object BlockFactory {
    /**
     * Creates a new, empty block of [blockType].
     */
    fun createBlock(blockType: BlockType): Block = when (blockType) {
        BlockType.TEXT -> Block.TextBlock()
        BlockType.LIST_CHECK -> Block.ListBlock(blockType = BlockType.LIST_CHECK)
        BlockType.LIST_NUMBER -> Block.ListBlock(blockType = BlockType.LIST_NUMBER)
        BlockType.LINK -> Block.LinkBlock()
        BlockType.H1 -> Block.HBlock(blockType = BlockType.H1)
        BlockType.H2 -> Block.HBlock(blockType = BlockType.H2)
        BlockType.H3 -> Block.HBlock(blockType = BlockType.H3)
        BlockType.H4 -> Block.HBlock(blockType = BlockType.H4)
        BlockType.QUOTE -> Block.QuoteBlock()
        BlockType.SEPARATOR -> Block.SeparatorBlock()
    }
}
