package com.pasich.encly.data.export

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import com.pasich.encly.editor.model.MAX_LIST_LEVEL
import com.pasich.encly.editor.model.hasContent
import com.pasich.encly.editor.model.kindIn
import com.pasich.encly.editor.model.normalizedLevels
import com.pasich.encly.editor.model.numbers

/**
 * A note's blocks as Markdown (CommonMark with GFM task lists), for the plaintext export.
 *
 * - Blocks are separated by a blank line, so a separator `---` can never turn the line above it
 *   into a heading.
 * - Text is escaped so that what the user typed reads as typed: inline marks (`*`, `` ` ``, `[`,
 *   `<`, `\`, `~`, a word-edge `_`, an entity such as `&amp;`) and whatever would start a block at
 *   the start of a line (`#`, `>`, `-`, `+`, `1.`, `===`, an indent of four spaces).
 * - A line break inside a block is kept as a hard break (a trailing `\`).
 * - A link is `[title](url)`. Only `http`, `https`, `mailto` and `tel` addresses become links; any
 *   other scheme (`javascript:`, `data:`) is written as inline code, so the file never carries a
 *   link that runs something when opened in a viewer. A link's preview image is never written:
 *   `![](...)` would make a viewer fetch it from the network.
 */
object MarkdownBlocks {
    private const val NO_BREAK_SPACE = '\u00A0'

    /** The width of `- `, the marker of a bullet and of a task item. */
    private const val BULLET_WIDTH = 2
    private val LINK_SCHEMES = setOf("http", "https", "mailto", "tel")
    private val ENCODED_IN_LINK = mapOf(
        ' ' to "%20",
        '(' to "%28",
        ')' to "%29",
        '<' to "%3C",
        '>' to "%3E",
        '\\' to "%5C",
    )

    /** The Markdown of [blocks] (those without content are left out); empty when nothing is left. */
    fun render(blocks: List<Block>): String =
        blocks.filter { it.hasContent() }.map(::renderBlock).filter { it.isNotEmpty() }.joinToString("\n\n")

    private fun renderBlock(block: Block): String = when (block) {
        is Block.TextBlock -> paragraphs(block.text.value)
        is Block.HBlock -> heading(block.blockType, block.text.value)
        is Block.QuoteBlock -> quote(block.text.value)
        is Block.LinkBlock -> link(block.block.value)
        is Block.SeparatorBlock -> "---"
        is Block.ListBlock -> list(block.blockType, block.items.value)
    }

    private fun heading(type: BlockType, text: String): String {
        // H1 to H4 are consecutive.
        val level = type.ordinal - BlockType.H1.ordinal + 1
        // A heading is one line; a trailing "#" would be read as the closing sequence.
        val escaped = MarkdownText.escapeInline(MarkdownText.oneLine(text)).trimEnd()
        val line = if (escaped.endsWith("#")) escaped.dropLast(1) + "\\#" else escaped
        return "${"#".repeat(level)} $line".trimEnd()
    }

    private fun quote(text: String): String = MarkdownText.hardBreakLines(text).joinToString("\n") { line ->
        if (line.isEmpty()) ">" else "> $line"
    }

    private fun link(data: LinkDataBlock): String {
        val url = data.url.trim()
        val label = MarkdownText.oneLine(data.title).ifBlank { url }
        val target = safeTarget(url)
        if (target == null) return "`${MarkdownText.oneLine(url).replace('`', '\'')}`"
        return "[${MarkdownText.escapeInline(label)}]($target)"
    }

    /** [url] ready for `( … )`, or null for an address that must not become a link. */
    private fun safeTarget(url: String): String? {
        val scheme = url.substringBefore(':', "").lowercase()
        val clean = url.filterNot { it.isISOControl() || it == NO_BREAK_SPACE }
        return if (clean.isEmpty() || ':' !in clean || scheme !in LINK_SCHEMES) {
            null
        } else {
            // The characters that would end or break the destination are percent-encoded.
            clean.map { ENCODED_IN_LINK[it] ?: it.toString() }.joinToString("")
        }
    }

    private fun list(type: BlockType, items: List<ItemListBlock>): String {
        val shown = items.filter { it.value.isNotBlank() }.normalizedLevels(type)
        val numbers = shown.numbers(type)
        // How far each open level is indented: its parent's indent plus the width of the parent's list marker, which
        // is where the parent's text starts, as CommonMark wants a nested item to line up. (The box of a task item
        // belongs to its text, not to the marker.)
        val indents = IntArray(MAX_LIST_LEVEL + 2)
        return shown.withIndex().joinToString("\n") { (index, item) ->
            val kind = item.kindIn(type)
            val marker = when (kind) {
                BlockType.LIST_NUMBER -> "${numbers[index]}. "
                BlockType.LIST_CHECK -> if (item.isCheck) "- [x] " else "- [ ] "
                else -> "- "
            }
            val indent = indents[item.level]
            indents[item.level + 1] = indent + if (kind == BlockType.LIST_NUMBER) marker.length else BULLET_WIDTH
            val lines = MarkdownText.hardBreakLines(item.value)
            val continuation = " ".repeat(indent + marker.length)
            val first = " ".repeat(indent) + marker + lines.first()
            (listOf(first) + lines.drop(1).map { if (it.isEmpty()) "" else continuation + it }).joinToString("\n")
        }
    }

    private fun paragraphs(text: String): String = MarkdownText.hardBreakLines(text).joinToString("\n")
}
