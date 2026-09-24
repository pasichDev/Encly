package com.pasich.encly.presentation.editor.state

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.dynamicBlocks.TextualBlock
import kotlinx.coroutines.flow.MutableStateFlow

// The markdown-like marks a writer types at the start of a paragraph ("# ", "- ", "1. ",
// "[] ", "> ", "---"), and the same marks in pasted text.

/** A paragraph mark: the paragraph becomes a block of [type], [checked] for "[x] ". */
data class Shortcut(val type: BlockType, val marker: String, val checked: Boolean = false)

private val HEADINGS = listOf(BlockType.H1, BlockType.H2, BlockType.H3, BlockType.H4)
private val NUMBER_MARK = Regex("^\\d{1,3}[.)] ")
private val BULLET_MARKS = listOf("- ", "* ", "• ")
private val OPEN_CHECK_MARKS = listOf("[] ", "[ ] ", "- [ ] ", "- [] ")
private val DONE_CHECK_MARKS = listOf("[x] ", "[X] ", "- [x] ", "- [X] ")
private val RULES = setOf("---", "***", "___")
private const val QUOTE_MARK = "> "
private const val TYPED_RULE = "---"

/** The mark [line] starts with, if any; the text after it is the block's. */
fun leadingShortcut(line: String): Shortcut? {
    val hashes = line.takeWhile { it == '#' }.length
    return when {
        hashes in 1..HEADINGS.size && line.startsWith("${"#".repeat(hashes)} ") ->
            Shortcut(HEADINGS[hashes - 1], line.take(hashes + 1))

        // Before bullets: "- [ ] " is a checklist, not a bullet.
        OPEN_CHECK_MARKS.any { line.startsWith(it) } ->
            Shortcut(BlockType.LIST_CHECK, OPEN_CHECK_MARKS.first { line.startsWith(it) })

        DONE_CHECK_MARKS.any { line.startsWith(it) } ->
            Shortcut(BlockType.LIST_CHECK, DONE_CHECK_MARKS.first { line.startsWith(it) }, checked = true)

        BULLET_MARKS.any { line.startsWith(it) } ->
            Shortcut(BlockType.LIST_BULLET, BULLET_MARKS.first { line.startsWith(it) })

        line.startsWith(QUOTE_MARK) -> Shortcut(BlockType.QUOTE, QUOTE_MARK)

        else -> NUMBER_MARK.find(line)?.let { Shortcut(BlockType.LIST_NUMBER, it.value) }
    }
}

/**
 * The shortcut a writer just completed in a paragraph that now reads [text], with the cursor at
 * [cursor]: a mark typed at the very start, its space included (the cursor right after it), or
 * "---" as the paragraph's whole text. Null otherwise, so a mark pasted or typed mid-text stays
 * text.
 */
fun typedShortcut(text: String, cursor: Int): Shortcut? = when {
    text == TYPED_RULE && cursor == text.length -> Shortcut(BlockType.SEPARATOR, text)
    else -> leadingShortcut(text)?.takeIf { cursor == it.marker.length }
}

/** [shortcut] applied: the block that replaces the paragraph, holding [rest], the text after the mark. */
fun shortcutBlock(shortcut: Shortcut, rest: String): Block = when (shortcut.type) {
    BlockType.H1, BlockType.H2, BlockType.H3, BlockType.H4 ->
        Block.HBlock(MutableStateFlow(rest), blockType = shortcut.type)

    BlockType.QUOTE -> Block.QuoteBlock(MutableStateFlow(rest))

    BlockType.LIST_CHECK, BlockType.LIST_BULLET, BlockType.LIST_NUMBER ->
        Block.ListBlock(MutableStateFlow(listOf(ItemListBlock(rest, shortcut.checked))), blockType = shortcut.type)

    BlockType.SEPARATOR -> Block.SeparatorBlock()

    BlockType.LINK -> Block.LinkBlock(MutableStateFlow(LinkDataBlock(url = rest)))

    BlockType.TEXT -> Block.TextBlock(MutableStateFlow(rest))
}

private val LONE_WEB_ADDRESS = Regex("^https?://[^\\s\\\\]+$", RegexOption.IGNORE_CASE)

/** Whether [line] is a web address and nothing else: pasted alone, it becomes a link card. */
fun isLoneWebAddress(line: String): Boolean = LONE_WEB_ADDRESS.matches(line.trim())

/**
 * Pasted [lines] as blocks: marks become their blocks, a lone web address a link, a rule a
 * separator, anything else a paragraph. Blank lines only separate paragraphs, and consecutive
 * items of one kind of list become one list.
 */
fun pastedBlocks(lines: List<String>): List<Block> {
    val result = mutableListOf<Block>()
    for (line in lines) {
        if (line.isBlank()) continue
        val block = pastedBlock(line)
        val previous = result.lastOrNull()
        if (block is Block.ListBlock && previous is Block.ListBlock && previous.blockType == block.blockType) {
            previous.items.value += block.items.value
        } else {
            result += block
        }
    }
    return result
}

private fun pastedBlock(line: String): Block {
    val trimmed = line.trim()
    val shortcut = leadingShortcut(line)
    return when {
        trimmed in RULES -> Block.SeparatorBlock()
        isLoneWebAddress(trimmed) -> Block.LinkBlock(MutableStateFlow(LinkDataBlock(title = "", url = trimmed)))
        shortcut != null -> shortcutBlock(shortcut, line.removePrefix(shortcut.marker))
        else -> Block.TextBlock(MutableStateFlow(line))
    }
}

/** A pasted line as a list item's text: without a list mark of its own. */
fun listItemText(line: String): ItemListBlock {
    val shortcut = leadingShortcut(line)?.takeIf { it.type in LIST_TYPES } ?: return ItemListBlock(line)
    return ItemListBlock(line.removePrefix(shortcut.marker), shortcut.checked)
}

private val LIST_TYPES = setOf(BlockType.LIST_BULLET, BlockType.LIST_NUMBER, BlockType.LIST_CHECK)

/** Line breaks as the editor splits on them: `\r\n`, `\r` and the Unicode separators become `\n`. */
fun normalizeLineBreaks(text: String): String =
    text.replace("\r\n", "\n").replace('\r', '\n').replace('\u2028', '\n').replace('\u2029', '\n')

/** [pasted] followed by a paragraph to go on writing in, unless it ends in a field already. */
internal fun withTrailingParagraph(pasted: List<Block>): List<Block> {
    val last = pasted.lastOrNull()
    val endsInField = last is TextualBlock || last is Block.ListBlock
    return if (endsInField) pasted else pasted + Block.TextBlock()
}
