package com.pasich.encly.dynamicBlocks.utils

import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.TextualBlock

/**
 * Utility class for working with blocks.
 * Contains common block operations to reduce code duplication.
 */
object BlockUtils {

    /**
     * Checks whether the block is empty.
     */
    fun isBlockEmpty(block: Block): Boolean = when (block) {
        is TextualBlock -> block.isEmpty()

        is Block.LinkBlock -> block.block.value.url.isEmpty()

        is Block.ListBlock -> {
            val items = block.items.value
            items.isEmpty() || (items.size == 1 && items[0].value.isEmpty())
        }

        else -> false
    }
}
