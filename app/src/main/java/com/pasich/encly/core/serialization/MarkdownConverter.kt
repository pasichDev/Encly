package com.pasich.encly.core.serialization

import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType

/**
 * Конвертер для преобразования блоков заметок в формат Markdown
 */
object MarkdownConverter {

    /**
     * Преобразует JSON-представление блоков в Markdown-текст
     *
     * @param json JSON-строка, содержащая блоки заметки
     * @return Строка в формате Markdown
     */
    fun jsonToMarkdown(json: String): String {
        if (json.isEmpty()) return ""

        // Используем существующий конвертер для получения блоков из JSON
        val blocks = BlockConverter.jsonToBlocks(json)
        return blocksToMarkdown(blocks)
    }

    /**
     * Преобразует список блоков в Markdown-текст
     *
     * @param blocks Список блоков для преобразования
     * @return Строка в формате Markdown
     */
    fun blocksToMarkdown(blocks: List<Block>): String {
        val markdown = StringBuilder()

        blocks.forEach { block ->
            when (block) {
                is Block.TextBlock -> {
                    markdown.append(block.text.value)
                    markdown.append("\n\n")
                }

                is Block.HBlock -> {
                    val headerLevel = when (block.blockType) {
                        BlockType.H1 -> "#"
                        BlockType.H2 -> "##"
                        BlockType.H3 -> "###"
                        BlockType.H4 -> "####"
                        else -> "#"
                    }
                    markdown.append("$headerLevel ${block.text.value}")
                    markdown.append("\n\n")
                }

                is Block.QuoteBlock -> {
                    // Разбиваем текст цитаты на строки и добавляем ">" в начало каждой строки
                    val quoteLines = block.text.value.split("\n")
                    quoteLines.forEach { line ->
                        markdown.append("> $line\n")
                    }
                    markdown.append("\n")
                }

                is Block.LinkBlock -> {
                    val linkData = block.block.value
                    // Если есть заголовок, используем его для текста ссылки, иначе используем URL
                    val linkText = linkData.title.ifEmpty { linkData.url }
                    markdown.append("[$linkText](${linkData.url})")
                    markdown.append("\n\n")
                }

                is Block.SeparatorBlock -> {
                    markdown.append("---")
                    markdown.append("\n\n")
                }


                is Block.ListBlock -> {
                    val items = block.items.value

                    if (items.isNotEmpty()) {
                        when (block.blockType) {
                            BlockType.LIST_CHECK -> {
                                // Создаем чекбоксы для каждого элемента
                                items.forEach { item ->
                                    val checkbox = if (item.isCheck) "[x]" else "[ ]"
                                    markdown.append("$checkbox ${item.value}\n")
                                }
                            }

                            BlockType.LIST_NUMBER -> {
                                // Создаем нумерованный список
                                items.forEachIndexed { index, item ->
                                    markdown.append("${index + 1}. ${item.value}\n")
                                }
                            }

                            else -> {
                                // Для всех остальных типов создаем маркированный список
                                items.forEach { item ->
                                    markdown.append("* ${item.value}\n")
                                }
                            }
                        }
                        markdown.append("\n")
                    }
                }
            }
        }

        return markdown.toString().trim()
    }
}
