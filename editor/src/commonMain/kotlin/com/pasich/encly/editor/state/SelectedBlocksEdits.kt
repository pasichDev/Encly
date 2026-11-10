package com.pasich.encly.editor.state

import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.TextualBlock
import com.pasich.encly.editor.model.hasContent
import com.pasich.encly.editor.model.kindIn
import com.pasich.encly.editor.model.normalizedLevels
import com.pasich.encly.editor.model.numbers

/**
 * Removes every picked block as one undo step and leaves block selection mode. The note keeps at
 * least one block: when every block is picked, the first becomes an empty paragraph, inside the same
 * step. The cursor goes to the end of the block above the gap (the first one with a field when there
 * is none), so the keyboard's undo works at once. False when no block was picked.
 */
public fun BlockEditorState.deleteSelectedBlocks(): Boolean {
    val indexes = pickedBlocks.picked().map { indexOf(it.id) }.sorted()
    if (indexes.isEmpty()) return false
    val removesAll = indexes.size == blocks.size
    batch {
        // Highest first: a removal never shifts the position of one still to come.
        indexes.asReversed().forEach { index ->
            if (removesAll && index == 0) {
                replaceBlock(0, Block.TextBlock(placeholder = EditorString.PRESS_TO_EDIT))
            } else {
                removeBlock(index)
            }
        }
    }
    pickedBlocks.clear()
    selection.focusAt(blocks.previousFocusableIndex(indexes.first()), cursorToEnd = true)
    return true
}

/** The picked blocks as plain text (see [plainText]); empty when none is picked. */
public fun BlockEditorState.pickedPlainText(): String = pickedBlocks.picked().plainText()

/**
 * [this] list of blocks as plain text: one paragraph per block, a blank line between blocks, and
 * lists one item to a line with a plain marker ("- ", "1. ", `[ ] ` or `[x] `). Blocks with no
 * content are left out. Markdown, for a host that wants it, is the host's own renderer.
 */
public fun List<Block>.plainText(): String = filter { it.hasContent() }.joinToString("\n\n") { it.plainLines() }

private fun Block.plainLines(): String = when (this) {
    is Block.TextBlock, is Block.HBlock, is Block.QuoteBlock, is Block.CodeBlock -> (this as TextualBlock).text.value

    is Block.SeparatorBlock -> "---"

    is Block.TableBlock -> table.value.rows.joinToString("\n") { row -> row.cells.joinToString(" | ") { it.value } }

    is Block.LinkBlock -> block.value.let { if (it.title.isBlank()) it.url else "${it.title} (${it.url})" }

    is Block.ListBlock -> {
        // A nested item is indented two spaces a level, and numbers start again at every level.
        val shown = items.value.filter { it.value.isNotBlank() }.normalizedLevels(blockType)
        val numbers = shown.numbers(blockType)
        shown.withIndex().joinToString("\n") { (at, item) ->
            val marker = when (item.kindIn(blockType)) {
                BlockType.LIST_NUMBER -> "${numbers[at]}. "
                BlockType.LIST_CHECK -> if (item.isCheck) "[x] " else "[ ] "
                else -> "- "
            }
            "  ".repeat(item.level) + marker + item.value
        }
    }
}
