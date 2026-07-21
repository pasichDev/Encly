package com.pasich.encly.dynamicBlocks.utils

import com.pasich.encly.core.AppLogger
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.TextualBlock

/**
 * Утилитарный класс для работы с блоками
 * Содержит общие операции над блоками для уменьшения дублирования кода
 */
object BlockUtils {

    /**
     * Проверяет пустой ли блок
     */
    fun isBlockEmpty(block: Block): Boolean {
        val result = when (block) {
            is TextualBlock -> {
                val isEmpty = block.isEmpty()
                AppLogger.d("BlockUtils", "TextualBlock (${block::class.simpleName}) isEmpty: $isEmpty, text: '${block.text.value}'")
                isEmpty
            }
            is Block.LinkBlock -> {
                val isEmpty = block.block.value.url.isEmpty()
                AppLogger.d("BlockUtils", "LinkBlock isEmpty: $isEmpty")
                isEmpty
            }
            is Block.ListBlock -> {
                val isEmpty = block.items.value.isEmpty() || (block.items.value.size == 1 && block.items.value[0].value.isEmpty())
                AppLogger.d("BlockUtils", "ListBlock isEmpty: $isEmpty")
                isEmpty
            }
            else -> {
                AppLogger.d("BlockUtils", "Unknown block type: ${block::class.simpleName}")
                false
            }
        }
        AppLogger.d("BlockUtils", "isBlockEmpty result for ${block::class.simpleName}: $result")
        return result
    }
}
