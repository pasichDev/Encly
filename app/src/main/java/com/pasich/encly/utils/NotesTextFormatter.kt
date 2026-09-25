package com.pasich.encly.utils

import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType

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
     * Converts a list of blocks into plain text without Markdown formatting.
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
                    // For quotes, add quotation marks instead of a Markdown marker
                    text.append("«${block.text.value}»")
                    text.append("\n\n")
                }

                is Block.LinkBlock -> {
                    val linkData = block.block.value
                    // If there is a title, use it and append the URL in parentheses
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
                                // For checkboxes, use [x] or [ ]
                                items.forEach { item ->
                                    val status = if (item.isCheck) "[x]" else "[ ]"
                                    text.append("$status ${item.value}\n")
                                }
                            }

                            BlockType.LIST_NUMBER -> {
                                // Leave numbered lists as they are
                                items.forEachIndexed { index, item ->
                                    text.append("${index + 1}. ${item.value}\n")
                                }
                            }

                            else -> {
                                // For bulleted lists, use a hyphen
                                items.forEach { item ->
                                    text.append("- ${item.value}\n")
                                }
                            }
                        }
                        text.append("\n")
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
