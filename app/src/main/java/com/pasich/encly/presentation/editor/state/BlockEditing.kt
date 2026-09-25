package com.pasich.encly.presentation.editor.state

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.dynamicBlocks.TextualBlock
import kotlinx.coroutines.flow.MutableStateFlow

// How writing moves text between blocks: Enter splits a block at the cursor, a paste of several
// lines becomes blocks, Backspace at the start of a block joins it to the one above, and a mark
// typed at the start of a paragraph turns it into a heading, list, quote or separator. Each is
// one undo step, and no text is ever dropped.

/**
 * [lineBreak] in the text field of block [blockId] (a paragraph, heading or quote). Enter splits
 * the block at the cursor, the text after it going to a new paragraph; pasted lines become blocks.
 * Returns the text the field keeps; null when the block cannot take it (the break stays text).
 */
fun BlockEditorState.breakLine(blockId: String, lineBreak: LineBreak): String? {
    val index = indexOf(blockId)
    val block = blocks.getOrNull(index)
    if (block !is TextualBlock) return null
    return if (lineBreak.isEnter) {
        enter(index, block, lineBreak.head, lineBreak.tail)
    } else {
        pasteLines(index, block, lineBreak)
    }
}

private fun BlockEditorState.enter(index: Int, block: TextualBlock, head: String, tail: String): String {
    val styled = block !is Block.TextBlock
    return when {
        // An empty heading or quote: Enter turns it back into a paragraph.
        styled && head.isEmpty() && tail.isEmpty() -> {
            batch { replaceBlock(index, Block.TextBlock()) }
            selection.focusAt(index, caret = 0)
            ""
        }

        // At the start of a heading or quote: a paragraph opens above, the block stays as it is.
        styled && head.isEmpty() -> {
            batch { addBlock(index, Block.TextBlock()) }
            selection.focusAt(index + 1, caret = 0)
            tail
        }

        else -> {
            val next = Block.TextBlock(MutableStateFlow(tail))
            batch {
                setValue(blocks[index].id, block.text, head)
                addBlock(index + 1, next)
            }
            selection.focusAt(index + 1, caret = 0)
            head
        }
    }
}

private fun BlockEditorState.pasteLines(index: Int, block: TextualBlock, lineBreak: LineBreak): String {
    val (head, lines, tail) = lineBreak
    // Pasted into an empty paragraph: every line is read, the first one too.
    if (block is Block.TextBlock && head.isEmpty() && tail.isEmpty()) {
        val pasted = withTrailingParagraph(pastedBlocks(lines))
        batch {
            replaceBlock(index, pasted.first())
            pasted.drop(1).forEachIndexed { i, added -> addBlock(index + 1 + i, added) }
        }
        selection.focusAt(index + pasted.lastIndex, cursorToEnd = true)
        return ""
    }
    val kept = head + lines.first()
    val rest = lines.drop(1)
    // The last line joins the text after the cursor, which stays a paragraph.
    val carried = Block.TextBlock(MutableStateFlow(rest.last() + tail)).takeIf { tail.isNotEmpty() }
    val added = when (carried) {
        null -> withTrailingParagraph(pastedBlocks(rest))
        else -> pastedBlocks(rest.dropLast(1)) + carried
    }
    batch {
        setValue(blocks[index].id, block.text, kept)
        added.forEachIndexed { i, new -> addBlock(index + 1 + i, new) }
    }
    val last = index + added.size
    when (carried) {
        null -> selection.focusAt(last, cursorToEnd = true)
        else -> selection.focusAt(last, caret = rest.last().length)
    }
    return kept
}

/**
 * Backspace with the cursor at the start of block [blockId]. A heading or quote first turns
 * into a paragraph; a paragraph joins the block above (a heading, quote, paragraph or the last
 * item of a list), its text appended there, and a separator above it is removed. Returns false
 * when there is nothing to do (the first block, or an empty paragraph, which the caller removes).
 */
fun BlockEditorState.backspaceAtStart(blockId: String): Boolean {
    val index = indexOf(blockId)
    return when (val block = blocks.getOrNull(index)) {
        is Block.TextBlock -> block.text.value.isNotEmpty() && joinToPrevious(index, block.text.value)

        is Block.HBlock, is Block.QuoteBlock -> {
            val text = (block as TextualBlock).text.value
            batch { replaceBlock(index, Block.TextBlock(MutableStateFlow(text))) }
            selection.focusAt(index, caret = 0)
            true
        }

        else -> false
    }
}

/** Joins [text], the paragraph at [index], to the block above it; false when that cannot take it. */
private fun BlockEditorState.joinToPrevious(index: Int, text: String): Boolean {
    when (val previous = blocks.getOrNull(index - 1)) {
        is Block.SeparatorBlock -> {
            batch { removeBlock(index - 1) }
            selection.focusAt(index - 1, caret = 0)
        }

        is Block.TextBlock -> joinText(index, previous.text, text)

        is Block.HBlock -> joinText(index, previous.text, text)

        is Block.QuoteBlock -> joinText(index, previous.text, text)

        is Block.ListBlock -> joinToList(index, previous, text)

        // The first block, or a link card, which has no text to join.
        is Block.LinkBlock, null -> return false
    }
    return true
}

/** Appends [text], the paragraph at [index], to the last item of [list], the block above. */
private fun BlockEditorState.joinToList(index: Int, list: Block.ListBlock, text: String) {
    val items = list.items.value
    val last = items.lastOrNull()
    batch {
        val joined = last?.let { items.dropLast(1) + it.copy(value = it.value + text) } ?: listOf(ItemListBlock(text))
        setValue(list.id, list.items, joined)
        removeBlock(index)
    }
    val item = list.items.value.last()
    selection.focusAt(index - 1, caret = last?.value?.length ?: 0, itemId = item.id)
}

/** Appends [text], the paragraph at [index], to [previousText], the block above's, and removes the paragraph. */
private fun BlockEditorState.joinText(index: Int, previousText: MutableStateFlow<String>, text: String) {
    val join = previousText.value.length
    batch {
        setValue(blocks[index - 1].id, previousText, previousText.value + text)
        removeBlock(index)
    }
    selection.focusAt(index - 1, caret = join)
}

/**
 * [shortcut] typed into paragraph [blockId], which now reads [typed]: the paragraph becomes that
 * block with the text after the mark. The typing stays its own undo step, so undo first brings
 * back the paragraph with the mark as typed.
 */
fun BlockEditorState.applyShortcut(blockId: String, typed: String, shortcut: Shortcut): Boolean {
    val index = indexOf(blockId)
    val block = blocks.getOrNull(index) as? Block.TextBlock ?: return false
    changeValue(block.id, block.text, typed, mergeable = true)
    if (shortcut.type == BlockType.SEPARATOR) {
        replaceWithBlockAndParagraph(index, Block.SeparatorBlock())
    } else {
        val replacement = shortcutBlock(shortcut, typed.removePrefix(shortcut.marker))
        batch { replaceBlock(index, replacement) }
        val item = (replacement as? Block.ListBlock)?.items?.value?.firstOrNull()
        selection.focusAt(index, caret = 0, itemId = item?.id)
    }
    return true
}

/**
 * [pasted], a lone web address, pasted into the empty paragraph [blockId]: it becomes a link card
 * ([link]), and a paragraph follows it to go on writing in. Undo turns it back into the text.
 */
fun BlockEditorState.pasteLink(blockId: String, pasted: String, link: LinkDataBlock): Boolean {
    val index = indexOf(blockId)
    val block = blocks.getOrNull(index) as? Block.TextBlock
    if (block == null || block.text.value.isNotEmpty()) return false
    changeValue(block.id, block.text, pasted, mergeable = false)
    replaceWithBlockAndParagraph(index, Block.LinkBlock(MutableStateFlow(link)))
    return true
}

/** Puts [block] at [index] and focuses the paragraph after it, adding one unless there is one. */
private fun BlockEditorState.replaceWithBlockAndParagraph(index: Int, block: Block) {
    batch {
        replaceBlock(index, block)
        if (blocks.getOrNull(index + 1) !is Block.TextBlock) addBlock(index + 1, Block.TextBlock())
    }
    selection.focusAt(index + 1, caret = 0)
}
