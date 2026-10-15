package com.pasich.encly.presentation.editor.state

import com.pasich.encly.data.importer.MarkdownBlockMapper
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
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
 * Pasted [lines] as blocks, read as Markdown by the importer's [MarkdownBlockMapper] in its
 * paste mode (nothing is rewritten or dropped): marks become their blocks, a lone web address a
 * link, a rule a separator, anything else a paragraph. A blank line (any number of them) starts
 * the next paragraph while a single line break stays inside its paragraph, and consecutive
 * items of one kind of list become one list. Whitespace-only lines at either end are not
 * content and are dropped; none in between is.
 */
fun pastedBlocks(lines: List<String>): List<Block> {
    val text = trimBlankLines(lines).joinToString("\n", transform = ::asMarkdownMark)
    return MarkdownBlockMapper.pasteBlocks(text, claim = ::pastedAddress)
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
