package com.pasich.encly.editor.state

import com.pasich.encly.editor.MarkdownBlockParser
import com.pasich.encly.editor.codec.MarkdownBlockMapper
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.LinkDataBlock
import kotlinx.coroutines.flow.MutableStateFlow

// Smart Paste: what pasted plain text becomes. Only the plain-text clipboard is read; HTML is never parsed.

/**
 * [lines] without the whitespace-only lines before the first and after the last one that has
 * content. Blank lines between content stay: they are the author's paragraph breaks.
 */
fun trimBlankLines(lines: List<String>): List<String> {
    val first = lines.indexOfFirst { it.isNotBlank() }
    if (first < 0) return emptyList()
    return lines.subList(first, lines.indexOfLast { it.isNotBlank() } + 1)
}

/**
 * Pasted [lines] as blocks, read as Markdown by [markdown] (by default the editor's own
 * [MarkdownBlockMapper]) in its paste mode (nothing is rewritten or dropped): marks become their
 * blocks, a lone web address a link, a rule a separator, anything else a paragraph. A blank line
 * (any number of them) starts the next paragraph while a single line break stays inside its paragraph, and consecutive
 * items of one kind of list become one list. Whitespace-only lines at either end are not
 * content and are dropped; none in between is. With [plainFirstLine] the first line stays plain
 * text, marks and all: a paste that continues what is already on the line must not turn it into
 * a heading, list or link card.
 */
fun pastedBlocks(
    lines: List<String>,
    plainFirstLine: Boolean = false,
    markdown: MarkdownBlockParser = MarkdownBlockMapper,
): List<Block> {
    val text = trimBlankLines(lines).withIndex()
        .joinToString("\n") { (i, line) -> if (i == 0 && plainFirstLine) line else asMarkdownMark(line) }
    return markdown.parsePaste(text, plainFirstLine, ::pastedAddress)
}

/** [line] with the editor's own short marks ("[] ", "[x] ", "• ") written the way Markdown does. */
private fun asMarkdownMark(line: String): String {
    val shortcut = leadingShortcut(line) ?: return line
    val rest by lazy { line.removePrefix(shortcut.marker) }
    return when {
        shortcut.type == BlockType.LIST_CHECK && !shortcut.marker.startsWith("-") ->
            (if (shortcut.checked) "- [x] " else "- [ ] ") + rest

        shortcut.marker == "• " -> "- $rest"

        else -> line
    }
}

private fun pastedAddress(line: String): Block? = line.trim().takeIf(::isLoneWebAddress)
    ?.let { Block.LinkBlock(MutableStateFlow(LinkDataBlock(title = "", url = it))) }
