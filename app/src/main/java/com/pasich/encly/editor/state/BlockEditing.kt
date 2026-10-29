package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import com.pasich.encly.editor.model.TextualBlock
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
    val id = blocks[index].id
    return when {
        // An empty heading or quote: Enter turns it back into a paragraph.
        styled && head.isEmpty() && tail.isEmpty() -> {
            val paragraph = Block.TextBlock()
            batch { replaceBlock(index, paragraph) }
            attachCaretToLastStep(CaretTarget(id, null, 0), CaretTarget(paragraph.id, null, 0))
            selection.focusAt(index, caret = 0)
            ""
        }

        // At the start of a heading or quote: a paragraph opens above, the block stays as it is.
        styled && head.isEmpty() -> {
            batch { addBlock(index, Block.TextBlock()) }
            attachCaretToLastStep(CaretTarget(id, null, 0), CaretTarget(id, null, 0))
            selection.focusAt(index + 1, caret = 0)
            tail
        }

        else -> {
            val next = Block.TextBlock(MutableStateFlow(tail))
            batch {
                setValue(blocks[index].id, block.text, head)
                addBlock(index + 1, next)
            }
            attachCaretToLastStep(CaretTarget(id, null, head.length), CaretTarget(next.id, null, 0))
            selection.focusAt(index + 1, caret = 0)
            head
        }
    }
}

/**
 * Backspace with the cursor at the start of block [blockId]. A heading or quote first turns
 * into a paragraph; a paragraph joins the block above (a heading, quote, paragraph or the last
 * item of a list), its text appended there, and a separator above it is removed. An empty paragraph
 * under a paragraph takes the text of the one above instead (see mergeIntoCurrent). Returns false
 * when there is nothing to do (the first block, or an empty paragraph, which the caller removes).
 */
fun BlockEditorState.backspaceAtStart(blockId: String): Boolean {
    val index = indexOf(blockId)
    return when (val block = blocks.getOrNull(index)) {
        // An empty paragraph under a paragraph merges the upper one into itself too (see mergeIntoCurrent),
        // so Backspace held down crosses empty lines without the focus leaving the field.
        is Block.TextBlock ->
            (block.text.value.isNotEmpty() || blocks.getOrNull(index - 1) is Block.TextBlock) &&
                joinToPrevious(index, block.text.value)

        is Block.HBlock, is Block.QuoteBlock -> {
            val text = (block as TextualBlock).text.value
            val paragraph = Block.TextBlock(MutableStateFlow(text))
            batch { replaceBlock(index, paragraph) }
            attachCaretToLastStep(CaretTarget(block.id, null, 0), CaretTarget(paragraph.id, null, 0))
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
            val paragraph = CaretTarget(blocks[index].id, null, 0)
            batch { removeBlock(index - 1) }
            attachCaretToLastStep(paragraph, paragraph)
            selection.focusAt(index - 1, caret = 0)
        }

        is Block.TextBlock -> mergeIntoCurrent(index, previous, text)

        is Block.HBlock -> joinText(index, previous.text, text)

        is Block.QuoteBlock -> joinText(index, previous.text, text)

        // A locked checklist takes no edits: Backspace at the start of the block below it does nothing.
        is Block.ListBlock -> if (!previous.locked.value) joinToList(index, previous, text)

        // The first block, or a link card, which has no text to join.
        is Block.LinkBlock, null -> return false
    }
    return true
}

/** Appends [text], the paragraph at [index], to the last item of [list], the block above. */
private fun BlockEditorState.joinToList(index: Int, list: Block.ListBlock, text: String) {
    val items = list.items.value
    val last = items.lastOrNull()
    val paragraph = blocks[index].id
    batch {
        val joined = if (last == null) {
            listOf(ItemListBlock(text))
        } else {
            items.dropLast(1) + last.copy(value = last.value + text)
        }
        setValue(list.id, list.items, joined)
        removeBlock(index)
    }
    val item = list.items.value.last()
    val join = last?.value.orEmpty().length
    attachCaretToLastStep(CaretTarget(paragraph, null, 0), CaretTarget(list.id, item.id, join))
    selection.focusAt(index - 1, caret = join, itemId = item.id)
}

/**
 * Two paragraphs: the one above is removed and its text goes in front of [text], the paragraph at
 * [index], which keeps the focus and the keyboard. Backspace held down then goes on deleting, block
 * after block, in the field that has it, instead of the focus jumping up a block each time. One undo step.
 */
private fun BlockEditorState.mergeIntoCurrent(index: Int, previous: Block.TextBlock, text: String) {
    val current = blocks[index] as Block.TextBlock
    val join = previous.text.value.length
    batch {
        setValue(current.id, current.text, previous.text.value + text)
        removeBlock(index - 1)
    }
    attachCaretToLastStep(CaretTarget(current.id, null, 0), CaretTarget(current.id, null, join))
    selection.focusAt(index - 1, caret = join)
}

/** Appends [text], the paragraph at [index], to [previousText], the block above's, and removes the paragraph. */
private fun BlockEditorState.joinText(index: Int, previousText: MutableStateFlow<String>, text: String) {
    val join = previousText.value.length
    val paragraph = blocks[index].id
    val previous = blocks[index - 1].id
    batch {
        setValue(previous, previousText, previousText.value + text)
        removeBlock(index)
    }
    attachCaretToLastStep(CaretTarget(paragraph, null, 0), CaretTarget(previous, null, join))
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
        selection.focusAt(index, caret = 0, itemId = replacement.firstItemId())
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
internal fun BlockEditorState.replaceWithBlockAndParagraph(index: Int, block: Block) {
    batch {
        replaceBlock(index, block)
        if (blocks.getOrNull(index + 1) !is Block.TextBlock) addBlock(index + 1, Block.TextBlock())
    }
    selection.focusAt(index + 1, caret = 0)
}
