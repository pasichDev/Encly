package com.pasich.encly.utils

import com.pasich.encly.editor.codec.BlockConverter
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.hasContent
import com.pasich.encly.editor.model.kindIn
import com.pasich.encly.editor.model.normalizedLevels
import com.pasich.encly.editor.model.numbers

/**
 * Utility class for formatting note text.
 */
object NotesTextFormatter {

    /**
     * Plain text of a note's stored blocks, or null when they cannot be read (damaged, or
     * written by a newer version). Never throws: list previews and search call it for every
     * note, and one bad note must not take the whole list down.
     */
    fun jsonToPlainText(json: String): String? {
        if (json.isEmpty()) return ""

        // Use the existing converter to get blocks from JSON
        return BlockConverter.jsonToBlocksOrNull(json)?.let(::blocksToPlainText)
    }

    /**
     * The text the user typed into [blocks], one block or list item per line, for search: no
     * checkbox or number markers, no separator lines and no quotation marks, so a query such
     * as "x" or "-" does not match every checklist or separator.
     */
    fun blocksToSearchText(blocks: List<Block>): String = blocks.flatMap { block ->
        when (block) {
            is Block.TextBlock -> listOf(block.text.value)
            is Block.HBlock -> listOf(block.text.value)
            is Block.QuoteBlock -> listOf(block.text.value)
            is Block.LinkBlock -> listOf(block.block.value.title, block.block.value.url)
            is Block.ListBlock -> block.items.value.map { it.value }
            is Block.SeparatorBlock -> emptyList()
        }
    }.filter { it.isNotBlank() }.joinToString("\n")

    /**
     * Converts a list of blocks into plain text without Markdown formatting, one block or list item
     * per line. Blank paragraphs (the blank lines a note keeps between its sections) are skipped and
     * no empty line separates blocks, so a card's short excerpt never spends a line on nothing.
     */
    fun blocksToPlainText(blocks: List<Block>): String {
        val text = StringBuilder()

        blocks.filter { it.hasContent() }.forEach { block ->
            when (block) {
                is Block.TextBlock -> {
                    text.append(block.text.value)
                    text.append("\n")
                }

                is Block.HBlock -> {
                    text.append(block.text.value)
                    text.append("\n")
                }

                is Block.QuoteBlock -> {
                    // For quotes, add quotation marks instead of a Markdown marker
                    text.append("«${block.text.value}»")
                    text.append("\n")
                }

                is Block.LinkBlock -> {
                    val linkData = block.block.value
                    // If there is a title, use it and append the URL in parentheses
                    if (linkData.title.isNotEmpty()) {
                        text.append("${linkData.title} (${linkData.url})")
                    } else {
                        text.append(linkData.url)
                    }
                    text.append("\n")
                }

                is Block.SeparatorBlock -> {
                    text.append("---------------------")
                    text.append("\n")
                }

                is Block.ListBlock -> {
                    // A nested item is indented two spaces a level; numbers start again at every level.
                    val items = block.items.value.normalizedLevels(block.blockType)
                    val numbers = items.numbers(block.blockType)
                    items.forEachIndexed { index, item ->
                        val marker = when (item.kindIn(block.blockType)) {
                            // For checkboxes, use [x] or [ ]
                            BlockType.LIST_CHECK -> if (item.isCheck) "[x]" else "[ ]"

                            // Leave numbered lists as they are
                            BlockType.LIST_NUMBER -> "${numbers[index]}."

                            // For bulleted lists, use a hyphen
                            else -> "-"
                        }
                        text.append("  ".repeat(item.level)).append("$marker ${item.value}\n")
                    }
                }

                else -> {
                    // Can do nothing here or log it
                }
            }
        }

        return text.toString().trim()
    }
}
