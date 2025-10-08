package com.pasich.encly.utils

import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType

/**
 * Утилитарный класс для форматирования текста заметок
 */
object NotesTextFormatter {

    fun jsonToPlainText(json: String): String {
        if (json.isEmpty()) return ""

        // Используем существующий конвертер для получения блоков из JSON
        val blocks = BlockConverter.jsonToBlocks(json)
        return blocksToPlainText(blocks)
    }


    /**
     * Преобразует список блоков в обычный текст без форматирования Markdown
     */
    fun blocksToPlainText(blocks: List<Block>): String {
        val text = StringBuilder()

        blocks.forEach { block ->
            when (block) {
                is Block.TextBlock -> {
                    text.append(block.text.value)
                    text.append("\n\n")
                }

                is Block.HBlock -> {
                    text.append(block.text.value)
                    text.append("\n\n")
                }


                is Block.QuoteBlock -> {
                    // Для цитаты добавляем кавычки вместо маркера Markdown
                    text.append("«${block.text.value}»")
                    text.append("\n\n")
                }

                is Block.LinkBlock -> {
                    val linkData = block.block.value
                    // Если есть заголовок, используем его и добавляем URL в скобках
                    if (linkData.title.isNotEmpty()) {
                        text.append("${linkData.title} (${linkData.url})")
                    } else {
                        text.append(linkData.url)
                    }
                    text.append("\n\n")
                }

                is Block.SeparatorBlock -> {
                    text.append("---------------------")
                    text.append("\n\n")
                }


                is Block.ListBlock -> {
                    val items = block.items.value

                    if (items.isNotEmpty()) {
                        when (block.blockType) {
                            BlockType.LIST_CHECK -> {
                                // Для чекбоксов используем [x] или [ ]
                                items.forEach { item ->
                                    val status = if (item.isCheck) "[x]" else "[ ]"
                                    text.append("$status ${item.value}\n")
                                }
                            }

                            BlockType.LIST_NUMBER -> {
                                // Нумерованные списки оставляем как есть
                                items.forEachIndexed { index, item ->
                                    text.append("${index + 1}. ${item.value}\n")
                                }
                            }

                            else -> {
                                // Для маркированных списков используем дефис
                                items.forEach { item ->
                                    text.append("- ${item.value}\n")
                                }
                            }
                        }
                        text.append("\n")
                    }
                }

                else -> {
                    // Можна нічого не робити або логнути
                }
            }
        }

        return text.toString().trim()
    }
}
