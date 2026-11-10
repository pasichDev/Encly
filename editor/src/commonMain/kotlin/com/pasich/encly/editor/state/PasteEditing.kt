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
        mapped.isEmpty() || mapped.singleOrNull() is Block.TextBlock ->
            pasteParagraph(PasteSite(index, block, lineBreak.head, lineBreak.tail), pasted)

        else -> pasteBlocks(
            PasteSite(index, block, lineBreak.head, lineBreak.tail),
            mapped,
            lead = pasted.first().takeWhile { it.isWhitespace() },
            trail = pasted.last().takeLastWhile { it.isWhitespace() },
        )
    }
}

/**
 * [pasted], the whole text of an insertion into the field of block [blockId] (item [itemId] when it is a list) that
 * read [head] before it and [tail] after it, when it is exactly what the last copy or cut of this editor put on the
 * clipboard ([BlockEditorState.rememberCopied]): the blocks that were copied go in, as they were, instead of the text
 * being read. In a paragraph, heading or quote the blocks follow the rules of [pasteLines]; in a list item they
 * become items, as plain text (a list item holds no other block). One undo step. Returns the text the field keeps,
 * or null when the paste is not that text or the field takes no paste (code, a table cell, a link).
 */
public fun BlockEditorState.pasteCopied(
    blockId: String,
    itemId: String?,
    head: String,
    pasted: String,
    tail: String,
): String? {
    val text = normalizeLineBreaks(pasted)
    val copied = copiedFor(text) ?: return null
    val index = indexOf(blockId)
    return when (val block = blocks.getOrNull(index)) {
        is Block.CodeBlock -> null

        is TextualBlock -> {
            val single = copied.singleOrNull() as? Block.TextBlock
            val site = PasteSite(index, block, head, tail)
            if (single != null) {
                pasteParagraph(site, single.text.value.split('\n'))
            } else {
                pasteBlocks(site, copied, lead = "", trail = "")
            }
        }

        is Block.ListBlock -> itemId?.let { itemAt(blockId, it) }
            ?.let { listPasteText(it, head, copied.plainText().split('\n'), tail) }

        else -> null
    }
}

/** Where a paste lands: the block at [index], which read [head] before the pasted text and [tail] after it. */
private data class PasteSite(val index: Int, val block: TextualBlock, val head: String, val tail: String)

/** A paste that is one paragraph: it goes into the block at the cursor, line breaks and all. */
private fun BlockEditorState.pasteParagraph(site: PasteSite, pasted: List<String>): String {
    val index = site.index
    val block = site.block
    val head = site.head
    val tail = site.tail
    val text = pasted.joinToString("\n").let { if (block is Block.HBlock) it.replace('\n', ' ') else it }
    val kept = head + text + tail
    batch { setValue(blocks[index].id, block.text, kept) }
    selection.focusAt(index, caret = head.length + text.length)
    return kept
}

/**
 * A paste of several paragraphs or marks: see [pasteLines]. [lead] is the whitespace the first pasted line started
 * with and [trail] the whitespace the last one ended in.
 */
private fun BlockEditorState.pasteBlocks(site: PasteSite, mapped: List<Block>, lead: String, trail: String): String {
    val index = site.index
    val block = site.block
    val head = site.head
    val tail = site.tail
    val first = mapped.first() as? Block.TextBlock
    val before = when (first) {
        null -> head
        else -> head + lead + first.text.value.flattenFor(block)
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
            val joined = last.text.value + trail
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
            setValue(blocks[index].id, block.text, before)
            rest.forEachIndexed { i, added -> addBlock(index + 1 + i, added) }
        }
    }
    val last = if (replaceCurrent) index + rest.lastIndex else index + rest.size
    if (tail.isEmpty()) selection.focusAt(last, cursorToEnd = true) else selection.focusAt(last, caret = caret)
    return if (replaceCurrent) "" else before
}

/** A heading is one line: line breaks of pasted text become spaces there. */
private fun String.flattenFor(block: TextualBlock): String = if (block is Block.HBlock) replace('\n', ' ') else this

/** A block of [this] block's kind (paragraph, quote or heading of the same level) holding [text]. */
private fun TextualBlock.sameKind(text: String): Block = when (this) {
    is Block.HBlock -> Block.HBlock(MutableStateFlow(text.replace('\n', ' ')), blockType)
    is Block.QuoteBlock -> Block.QuoteBlock(MutableStateFlow(text))
    else -> Block.TextBlock(MutableStateFlow(text))
}
