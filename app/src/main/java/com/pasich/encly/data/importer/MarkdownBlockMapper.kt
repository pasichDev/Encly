package com.pasich.encly.data.importer

import com.pasich.encly.data.handoff.InlineHtml
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType

/**
 * Markdown as Encly blocks. Line based and forgiving; only the markup it reads is removed.
 *
 * - `#`..`####` -> H1-H4 (5 and 6 -> H4); `- [ ]` / `- [x]` -> LIST_CHECK; `-` `*` `+` ->
 *   LIST_BULLET; `1.` -> LIST_NUMBER (nested items are flattened, Encly lists are flat);
 *   `>` -> QUOTE; `---` / `***` -> SEPARATOR; everything else -> TEXT, one block per paragraph.
 * - `✅` / `☐` lines (Notesnook text exports) are checklist items. An Obsidian callout keeps its title
 *   (`> [!note] Decisions` -> `Decisions`), a wiki link keeps its alias or name.
 * - Fenced code keeps its lines as TEXT, markers removed. Inline markdown (`**bold**`, links) is
 *   kept as typed, which reads fine as plain text; HTML tags are removed and entities decoded
 *   ([InlineHtml]) outside code spans: the inline tags of real HTML (`<b>`, `<a href>`, `<br>`, ...)
 *   go and their text stays, while other angle-bracket text (`Map<String, Int>`, `<name>`) is kept.
 */
object MarkdownBlockMapper {
    private val HEADING = Regex("""^ {0,3}(#{1,6})[ \t]+(.*?)(?:[ \t]+#+)?[ \t]*$""")
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
    private val FENCE = Regex("""^ {0,3}(```|~~~).*$""")
    private val CODE_SPAN = Regex("""`[^`\n]+`""")
    private val AUTOLINK = Regex("""<((?:https?://|mailto:)[^\s<>]+|[^\s<>@/]+@[^\s<>@/]+)>""")

    fun toBlocks(markdown: String): List<Block> = Builder().apply { markdown.lines().forEach(::line) }.finish()

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

    private class Builder(private val paste: PasteRules? = null) {
        private val blocks = ArrayList<Block>()
        private val paragraph = ArrayList<String>()
        private val quote = ArrayList<String>()
        private val code = ArrayList<String>()
        private val items = ArrayList<ItemListBlock>()
        private var listType: BlockType? = null
        private var inFence = false

        fun line(raw: String) {
            if (paste?.plainFirstLine == true) {
                paste.plainFirstLine = false
                if (raw.isNotBlank()) {
                    paragraph += raw.trimEnd()
                    return
                }
            }
            val fence = FENCE.matches(raw)
            when {
                inFence && fence -> {
                    if (paste != null) code += raw
                    closeFence()
                }

                inFence -> code += raw

                fence -> {
                    flushAll()
                    inFence = true
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

        fun finish(): List<Block> {
            if (inFence) closeFence()
            flushAll()
            return blocks
        }

        private fun block(raw: String) {
            val heading = HEADING.matchEntire(raw)
            val check = CHECK.matchEntire(raw)
            val glyph = GLYPH_ITEM.matchEntire(raw)
            val bullet = BULLET.matchEntire(raw)
            val number = NUMBER.matchEntire(raw)
            val quoted = QUOTE.matchEntire(raw)
            val claimed = paste?.claim?.invoke(raw)
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
                    val level = heading.groupValues[1].length
                    blocks += ImportBlocks.heading(readable(heading.groupValues[2]), level)
                }

                check != null -> item(BlockType.LIST_CHECK, check.groupValues[2], check.groupValues[1] != " ")

                glyph != null -> item(BlockType.LIST_CHECK, glyph.groupValues[2], glyph.groupValues[1] != "☐")

                bullet != null -> item(BlockType.LIST_BULLET, bullet.groupValues[1], false)

                number != null -> item(BlockType.LIST_NUMBER, number.groupValues[1], false)

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

                else -> {
                    flushList()
                    flushQuote()
                    paragraph += readable(raw.trimEnd())
                }
            }
        }

        /** [text] as the importer reads it; a paste keeps it as it is. */
        private fun readable(text: String): String = if (paste == null) inline(text) else text

        private fun item(type: BlockType, text: String, checked: Boolean) {
            flushParagraph()
            flushQuote()
            if (listType != type) flushList()
            listType = type
            items += ItemListBlock(value = readable(text).trim(), isCheck = checked)
        }

        private fun closeFence() {
            inFence = false
            val text = code.joinToString("\n").trimEnd()
            code.clear()
            if (text.isNotBlank()) blocks += ImportBlocks.text(text)
        }

        private fun flushAll() {
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
            val kept = if (paste != null) items.toList() else items.filter { it.value.isNotBlank() }
            items.clear()
            listType = null
            if (kept.isNotEmpty()) blocks += ImportBlocks.list(type, kept)
        }
    }
}
