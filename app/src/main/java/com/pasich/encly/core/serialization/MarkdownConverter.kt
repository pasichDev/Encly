package com.pasich.encly.core.serialization

import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType

/**
 * Converter for transforming note blocks into Markdown format.
 */
object MarkdownConverter {

    /**
     * Converts a JSON representation of blocks into Markdown text.
     *
     * @param json JSON string containing the note blocks
     * @return a string in Markdown format
     */
    fun jsonToMarkdown(json: String): String {
        if (json.isEmpty()) return ""

        // Use the existing converter to obtain blocks from JSON
        val blocks = BlockConverter.jsonToBlocks(json)
        return blocksToMarkdown(blocks)
    }

    /**
     * Converts a list of blocks into Markdown text.
     *
     * @param blocks the list of blocks to convert
     * @return a string in Markdown format
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
                    // Split the quote text into lines and prepend ">" to each line
                    val quoteLines = block.text.value.split("\n")
                    quoteLines.forEach { line ->
                        markdown.append("> $line\n")
                    }
                    markdown.append("\n")
                }

                is Block.LinkBlock -> {
                    val linkData = block.block.value
                    // If there is a title, use it as the link text; otherwise use the URL
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
                                // Create checkboxes for each item
                                items.forEach { item ->
                                    val checkbox = if (item.isCheck) "[x]" else "[ ]"
                                    markdown.append("$checkbox ${item.value}\n")
                                }
                            }

                            BlockType.LIST_NUMBER -> {
                                // Create a numbered list
                                items.forEachIndexed { index, item ->
                                    markdown.append("${index + 1}. ${item.value}\n")
                                }
                            }

                            else -> {
                                // For all other types, create a bulleted list
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
