package com.pasich.encly.dynamicBlocks.factory

import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType

/**
 * Фабрика для создания блоков разных типов
 * Использует паттерн "Factory Method" для унификации создания блоков
 */
object BlockFactory {
    /**
     * Создает блок соответствующего типа
     * @return созданный блок или null для неподдерживаемых типов
     */
    fun createBlock(blockType: BlockType): Block? {
        return when (blockType) {
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


}
