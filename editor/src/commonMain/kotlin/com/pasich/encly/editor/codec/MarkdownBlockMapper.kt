package com.pasich.encly.editor.codec

import com.pasich.encly.editor.MarkdownBlockParser
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.MAX_LIST_LEVEL
import com.pasich.encly.editor.model.normalizedLevels
import com.pasich.encly.editor.state.cellText

/**
 * Markdown as Encly blocks. Line based and forgiving; only the markup it reads is removed.
 *
 * - `#`..`####` -> H1-H4 (5 and 6 -> H4); `- [ ]` / `- [x]` -> LIST_CHECK; `-` `*` `+` ->
 *   LIST_BULLET; `1.` -> LIST_NUMBER; items indented under another (2 or 4 spaces, or a tab) nest, up to three
 *   levels, and a nested item may be of another kind than its list;
 *   `>` -> QUOTE; `---` / `***` -> SEPARATOR; everything else -> TEXT, one block per paragraph.
 * - `✅` / `☐` lines (Notesnook text exports) are checklist items. An Obsidian callout keeps its title
 *   (`> [!note] Decisions` -> `Decisions`), a wiki link keeps its alias or name.
 * - Fenced code becomes a CODE block, fence markers removed and every space kept. A GFM pipe table (a header row, a
 *   `---` delimiter row, then rows) becomes a TABLE block; `\|` is a bar inside a cell, the column alignment is
 *   dropped, and a header row of only empty cells is the table without a header. Inline markdown (`**bold**`, links) is
 *   kept as typed, which reads fine as plain text; HTML tags are removed and entities decoded
 *   ([InlineHtml]) outside code spans: the inline tags of real HTML (`<b>`, `<a href>`, `<br>`, ...)
 *   go and their text stays, while other angle-bracket text (`Map<String, Int>`, `<name>`) is kept.
 */
public object MarkdownBlockMapper : MarkdownBlockParser {
    // Only the start is a pattern: a lazy title followed by an optional closing run backtracks quadratically on a
    // long run of blanks, so the title and its closing hashes are cut by hand (see [Heading]).
    private val HEADING_START = Regex("""^ {0,3}(#{1,6})[ \t]""")
    private val CHECK = Regex("""^[ \t]*[-*+][ \t]+\[([ xX])\][ \t]+(.*)$""")
    private val GLYPH_ITEM = Regex("""^[ \t]*([✅☑☐])[ \t]*(.*)$""")
    private val CALLOUT = Regex("""^\[![A-Za-z-]+][+-]?[ \t]*""")

    // No `[` in a name or alias: otherwise every `[[` of a long run of brackets scans to the end of the line.
    private val WIKI_LINK = Regex("""!?\[\[([^\[\]|\n]+)(?:\|([^\[\]\n]+))?]]""")
    private val BULLET = Regex("""^[ \t]*[-*+][ \t]+(.*)$""")

    // Up to three digits: "2024. Report" is text. Encly numbers its items itself, so a start number is not kept.
    private val NUMBER = Regex("""^[ \t]*\d{1,3}[.)][ \t]+(.*)$""")
    private val QUOTE = Regex("""^ {0,3}>[ \t]?(.*)$""")
    private val SEPARATOR = Regex("""^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$""")
    private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,}).*$""")
    private val FENCE_CLOSE = Regex("""^ {0,3}(`{3,}|~{3,})[ \t]*$""")
    private val TABLE_DELIMITER_CELL = Regex("""^:?-+:?$""")
    private val CODE_SPAN = Regex("""`[^`\n]+`""")
    private val AUTOLINK = Regex("""<((?:https?://|mailto:)[^\s<>]+|[^\s<>@/]+@[^\s<>@/]+)>""")

    /**
     * [markdown], a whole document, as blocks: headings, quotes, lists, checklists, separators and paragraphs.
     * Anything it does not understand stays as text, nothing is dropped.
     */
    public fun toBlocks(markdown: String): List<Block> = Builder().apply { markdown.lines().forEach(::line) }.finish()

    /**
     * The same line rules for text the user pastes into the editor, where nothing may be
     * rewritten: HTML, wiki links and callout markers stay as typed, fence lines stay in their
     * code paragraph, and blank list items are kept. [claim] may take a line whole as a block of
     * its own (a lone web address); it runs before every other rule. With [plainFirstLine] the
     * first line is plain text whatever it starts with, for a paste that continues a line.
     * [toBlocks] is unchanged.
     */
    internal fun pasteBlocks(
        text: String,
        plainFirstLine: Boolean = false,
        claim: (String) -> Block? = { null },
    ): List<Block> = Builder(PasteRules(claim, plainFirstLine)).apply { text.lines().forEach(::line) }.finish()

    override fun parsePaste(text: String, plainFirstLine: Boolean, claim: (String) -> Block?): List<Block> =
        pasteBlocks(text, plainFirstLine, claim)

    /** The columns [raw] is indented by: a space is one, a tab goes on to the next multiple of four. */
    private fun indentWidth(raw: String): Int = raw.takeWhile { it == ' ' || it == '\t' }
        .fold(0) { width, char -> if (char == '\t') width + TAB_WIDTH - width % TAB_WIDTH else width + 1 }

    private const val TAB_WIDTH = 4

    /** The indentation of each open level of the list being read. */
    private class IndentLevels {
        private val widths = ArrayList<Int>()

        /**
         * The level of an item indented by [width] columns: it nests under the item before it when indented more
         * than that one, however many spaces (2, 4, a tab) the writer uses, and comes out to the nearest level it
         * lines up with when indented less.
         */
        fun levelOf(width: Int): Int {
            while (widths.isNotEmpty() && widths.last() > width) widths.removeAt(widths.lastIndex)
            if (widths.isEmpty() || widths.last() < width) widths += width
            return (widths.size - 1).coerceAtMost(MAX_LIST_LEVEL)
        }

        fun clear() = widths.clear()
    }

    /** An ATX heading of [level] (1 to 6) with its [title], blanks and closing `#` run removed. */
    private class Heading(val level: Int, val title: String) {
        companion object {
            fun of(raw: String): Heading? {
                val start = HEADING_START.find(raw) ?: return null
                val title = raw.substring(start.value.length).trim(::isBlank)
                val closing = title.length - title.trimEnd('#').length
                val head = title.dropLast(closing)
                // A closing run counts only after a blank: "# a#" keeps its hash, "# #" is a title.
                val closed = closing > 0 && head.isNotEmpty() && isBlank(head.last())
                return Heading(start.groupValues[1].length, if (closed) head.trimEnd(::isBlank) else title)
            }

            private fun isBlank(char: Char) = char == ' ' || char == '\t'
        }
    }

    private class PasteRules(val claim: (String) -> Block?, var plainFirstLine: Boolean)

    /** [text] with HTML removed outside code spans. */
    internal fun inline(text: String): String {
        val linked = AUTOLINK.replace(text, "$1")
        val out = StringBuilder()
        var last = 0
        for (span in CODE_SPAN.findAll(linked)) {
            out.append(plain(linked.substring(last, span.range.first))).append(span.value)
            last = span.range.last + 1
        }
        return out.append(plain(linked.substring(last))).toString()
    }

    /** Wiki links as the text a reader sees (`[[Note|alias]]` -> alias), then no HTML. */
    private fun plain(text: String): String = InlineHtml.toPlainText(
        WIKI_LINK.replace(text) { it.groupValues[2].ifEmpty { it.groupValues[1] } },
        knownTagsOnly = true,
    )

    @Suppress("TooManyFunctions") // One function per kind of block the lines add up to.
    private class Builder(private val paste: PasteRules? = null) {
        private val blocks = ArrayList<Block>()
        private val paragraph = ArrayList<String>()
        private val quote = ArrayList<String>()
        private val code = ArrayList<String>()
        private val items = ArrayList<ItemListBlock>()
        private var listType: BlockType? = null

        private val indents = IndentLevels()
        private var inFence = false
        private var fenceMarker = ""

        // A line with a bar in it that may be the header of a table: told by the line that follows.
        private var tableHeader: String? = null
        private val tableRows = ArrayList<List<String>>()

        fun line(raw: String) {
            if (paste?.plainFirstLine == true) {
                paste.plainFirstLine = false
                if (raw.isNotBlank()) {
                    paragraph += raw.trimEnd()
                    return
                }
            }
            if (paste == null && !inFence && tableLine(raw)) return
            val fence = FENCE.matchEntire(raw)
            when {
                inFence && closesFence(raw) -> {
                    if (paste != null) code += raw
                    closeFence()
                }

                inFence -> code += raw

                fence != null -> {
                    flushAll()
                    inFence = true
                    fenceMarker = fence.groupValues[1]
                    if (paste != null) code += raw
                }

                raw.isBlank() -> {
                    // A blank line ends a paragraph or quote but not a list: loose lists stay one block.
                    flushParagraph()
                    flushQuote()
                }

                else -> block(raw)
            }
        }

        // A fence ends only at a line of the same mark, at least as long as the one that opened it, and nothing else.
        private fun closesFence(raw: String): Boolean {
            val match = FENCE_CLOSE.matchEntire(raw) ?: return false
            val mark = match.groupValues[1]
            return mark[0] == fenceMarker[0] && mark.length >= fenceMarker.length
        }

        fun finish(): List<Block> {
            releaseHeader()
            flushTable()
            if (inFence) closeFence()
            flushAll()
            return blocks
        }

        // Table rows. True when [raw] was taken as part of a table (or held as its possible header).
        private fun tableLine(raw: String): Boolean {
            val header = tableHeader
            tableHeader = null
            val headerCells = header?.let(::tableCells)
            if (headerCells != null && isDelimiter(raw, headerCells.size)) {
                flushAll()
                tableRows += headerCells
                return true
            }
            if (header != null) block(header, mayStartTable = false)
            val isRow = tableRows.isNotEmpty() && raw.isNotBlank() && '|' in raw && !FENCE.matches(raw)
            if (isRow) tableRows += tableCells(raw) else flushTable()
            return isRow
        }

        private fun releaseHeader() {
            tableHeader?.let { block(it, mayStartTable = false) }
            tableHeader = null
        }

        private fun isDelimiter(raw: String, columns: Int): Boolean {
            val cells = tableCells(raw)
            return '|' in raw && cells.size == columns && cells.all { TABLE_DELIMITER_CELL.matches(it) }
        }

        @Suppress("CyclomaticComplexMethod") // One branch per kind of line.
        private fun block(raw: String, mayStartTable: Boolean = true) {
            val heading = Heading.of(raw)
            val check = CHECK.matchEntire(raw)
            val glyph = GLYPH_ITEM.matchEntire(raw)
            val bullet = BULLET.matchEntire(raw)
            val number = NUMBER.matchEntire(raw)
            val quoted = QUOTE.matchEntire(raw)
            val claimed = paste?.run { claim(raw) }
            when {
                claimed != null -> {
                    flushAll()
                    blocks += claimed
                }

                SEPARATOR.matches(raw) -> {
                    flushAll()
                    blocks += Block.SeparatorBlock()
                }

                heading != null -> {
                    flushAll()
                    blocks += ImportBlocks.heading(readable(heading.title), heading.level)
                }

                check != null -> item(BlockType.LIST_CHECK, check.groupValues[2], check.groupValues[1] != " ", raw)

                glyph != null -> item(BlockType.LIST_CHECK, glyph.groupValues[2], glyph.groupValues[1] != "☐", raw)

                bullet != null -> item(BlockType.LIST_BULLET, bullet.groupValues[1], false, raw)

                number != null -> item(BlockType.LIST_NUMBER, number.groupValues[1], false, raw)

                quoted != null -> {
                    flushParagraph()
                    flushList()
                    quote +=
                        readable(
                            if (paste ==
                                null
                            ) {
                                CALLOUT.replaceFirst(quoted.groupValues[1], "")
                            } else {
                                quoted.groupValues[1]
                            },
                        )
                }

                // Indented text right after a list item continues that item.
                listType != null && raw.first().isWhitespace() -> {
                    val last = items.removeAt(items.lastIndex)
                    items += last.copy(value = (last.value + " " + readable(raw.trim())).trim())
                }

                paste == null && mayStartTable && '|' in raw -> {
                    flushList()
                    flushQuote()
                    tableHeader = raw
                }

                else -> {
                    flushList()
                    flushQuote()
                    paragraph += readable(raw.trimEnd())
                }
            }
        }

        /** [text] as the importer reads it; a paste keeps it as it is. */
        private fun readable(text: String): String = if (paste == null) inline(text) else text

        private fun item(type: BlockType, text: String, checked: Boolean, raw: String) {
            flushParagraph()
            flushQuote()
            val width = indentWidth(raw)
            var level = indents.levelOf(width)
            // A new kind at the top level starts a list of its own; deeper, a sub-list of that kind.
            if (listType != null && listType != type && level == 0) {
                flushList()
                level = indents.levelOf(width)
            }
            val kind = type.takeIf { level > 0 && it != listType }
            listType = listType ?: type
            items += ItemListBlock(value = readable(text).trim(), isCheck = checked, level = level, kind = kind)
        }

        private fun closeFence() {
            inFence = false
            val text = code.joinToString("\n").trimEnd()
            code.clear()
            // A paste keeps code as a paragraph, fences and all; an import makes it a code block.
            if (text.isNotBlank()) blocks += if (paste == null) ImportBlocks.code(text) else ImportBlocks.text(text)
        }

        private fun flushTable() {
            if (tableRows.isEmpty()) return
            val rows = tableRows.toList()
            tableRows.clear()
            // A header row of only empty cells is how a table without a header is written.
            val headless = rows.first().all { it.isEmpty() }
            blocks += ImportBlocks.table(if (headless) rows.drop(1) else rows, header = !headless)
        }

        // The cells of a pipe-table row: an outer bar dropped, `\|` a bar, `\\` a backslash, no line breaks or HTML.
        private fun tableCells(row: String): List<String> {
            val cells = ArrayList<String>()
            val cell = StringBuilder()
            var escaped = false
            for (c in row.trim().removePrefix("|")) {
                when {
                    escaped && (c == '|' || c == '\\') -> cell.append(c)
                    escaped -> cell.append('\\').append(c)
                    c == '|' -> cells.add(cell.toString().also { cell.clear() })
                    c != '\\' -> cell.append(c)
                }
                escaped = c == '\\' && !escaped
            }
            // A last cell that is empty is only the closing bar, unless it was escaped away.
            if (cell.isNotBlank() || cells.isEmpty()) cells.add(cell.toString())
            return cells.map { cellText(readable(it.trim())) }
        }

        private fun flushAll() {
            flushTable()
            flushParagraph()
            flushQuote()
            flushList()
        }

        private fun flushParagraph() {
            if (paragraph.isEmpty()) return
            val text = paragraph.joinToString("\n").trim()
            paragraph.clear()
            if (text.isNotEmpty()) blocks += ImportBlocks.text(text)
        }

        private fun flushQuote() {
            if (quote.isEmpty()) return
            val text = quote.joinToString("\n").trim()
            quote.clear()
            if (text.isNotEmpty()) blocks += ImportBlocks.quote(text)
        }

        private fun flushList() {
            val type = listType ?: return
            // A paste keeps an empty item (an empty checklist template); an import drops it.
            val kept = (if (paste != null) items.toList() else items.filter { it.value.isNotBlank() })
                .normalizedLevels(type)
            items.clear()
            indents.clear()
            listType = null
            if (kept.isNotEmpty()) blocks += ImportBlocks.list(type, kept)
        }
    }
}
