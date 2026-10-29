package com.pasich.encly.editor.state

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.TextualBlock
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Pasted lines in a paragraph, heading or quote (Smart Paste). The text is read as Markdown
 * ([pastedBlocks]): a paste that is one paragraph goes into the block at the cursor, line breaks
 * and all; otherwise its first paragraph joins the text before the cursor, the other blocks
 * follow, and the text after the cursor goes on at the end of the last paragraph, as the kind of
 * block it came from (text after the cursor in a heading stays a heading of that level, in a
 * quote a quote). When the cursor is after text, the first pasted line joins it as plain text
 * and is never read as a mark; so is a single line with a trailing line break, next to any text.
 * Whitespace-only lines at either end of the paste are dropped; everything else is kept. One
 * undo step.
 */
internal fun BlockEditorState.pasteLines(index: Int, block: TextualBlock, lineBreak: LineBreak): String {
    val pasted = trimBlankLines(lineBreak.lines)
    // One content line is only a line break at the end of the copied text: no marks to read,
    // unless it lands in an empty block, where a lone address or a mark is what the user meant.
    val inText = lineBreak.head.isNotEmpty() || lineBreak.tail.isNotEmpty()
    val mapped = if (pasted.size == 1 &&
        inText
    ) {
        emptyList()
    } else {
        pastedBlocks(pasted, lineBreak.head.isNotEmpty(), markdown)
    }
    return when {
        pasted.isEmpty() -> {
            // Nothing but blank lines: no content to paste.
            selection.focusAt(index, caret = lineBreak.head.length)
            lineBreak.head + lineBreak.tail
        }

        // One line (its line break is just the end of the copied text), or text the mapper left
        // nothing of: pasted as typed.
        mapped.isEmpty() || mapped.singleOrNull() is Block.TextBlock -> pasteParagraph(index, block, lineBreak, pasted)

        else -> pasteBlocks(index, block, lineBreak, pasted, mapped)
    }
}

/** A paste that is one paragraph: it goes into the block at the cursor, line breaks and all. */
private fun BlockEditorState.pasteParagraph(
    index: Int,
    block: TextualBlock,
    lineBreak: LineBreak,
    pasted: List<String>,
): String {
    val text = pasted.joinToString("\n").let { if (block is Block.HBlock) it.replace('\n', ' ') else it }
    val kept = lineBreak.head + text + lineBreak.tail
    batch { setValue(blocks[index].id, block.text, kept) }
    selection.focusAt(index, caret = lineBreak.head.length + text.length)
    return kept
}

/** A paste of several paragraphs or marks: see [pasteLines]. */
private fun BlockEditorState.pasteBlocks(
    index: Int,
    block: TextualBlock,
    lineBreak: LineBreak,
    pasted: List<String>,
    mapped: List<Block>,
): String {
    val (head, _, tail) = lineBreak
    val first = mapped.first() as? Block.TextBlock
    val lead = when (first) {
        null -> head
        else -> head + pasted.first().takeWhile { it.isWhitespace() } + first.text.value.flattenFor(block)
    }
    var rest = if (first == null) mapped else mapped.drop(1)
    var caret = 0
    if (tail.isEmpty()) {
        rest = withTrailingParagraph(rest)
    } else {
        // The pasted text goes on into what followed the cursor.
        val last = rest.last() as? Block.TextBlock
        if (last == null) {
            rest = rest + block.sameKind(tail)
        } else {
            val joined = last.text.value + pasted.last().takeLastWhile { it.isWhitespace() }
            caret = joined.length
            rest = rest.dropLast(1) + block.sameKind(joined + tail)
        }
    }
    // Nothing before the cursor and nothing to join: the pasted blocks take the block's place.
    val replaceCurrent = first == null && head.isEmpty()
    batch {
        if (replaceCurrent) {
            replaceBlock(index, rest.first())
            rest.drop(1).forEachIndexed { i, added -> addBlock(index + 1 + i, added) }
        } else {
            setValue(blocks[index].id, block.text, lead)
            rest.forEachIndexed { i, added -> addBlock(index + 1 + i, added) }
        }
    }
    val last = if (replaceCurrent) index + rest.lastIndex else index + rest.size
    if (tail.isEmpty()) selection.focusAt(last, cursorToEnd = true) else selection.focusAt(last, caret = caret)
    return if (replaceCurrent) "" else lead
}

/** A heading is one line: line breaks of pasted text become spaces there. */
private fun String.flattenFor(block: TextualBlock): String = if (block is Block.HBlock) replace('\n', ' ') else this

/** A block of [this] block's kind (paragraph, quote or heading of the same level) holding [text]. */
private fun TextualBlock.sameKind(text: String): Block = when (this) {
    is Block.HBlock -> Block.HBlock(MutableStateFlow(text.replace('\n', ' ')), blockType)
    is Block.QuoteBlock -> Block.QuoteBlock(MutableStateFlow(text))
    else -> Block.TextBlock(MutableStateFlow(text))
}
