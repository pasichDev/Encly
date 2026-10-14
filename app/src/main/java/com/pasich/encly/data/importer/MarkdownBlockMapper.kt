package com.pasich.encly.data.importer

import com.pasich.encly.data.handoff.InlineHtml
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType

/**
 * Markdown as Encly blocks. Line based and forgiving; text is never dropped.
 *
 * - `#`..`####` -> H1-H4 (5 and 6 -> H4); `- [ ]` / `- [x]` -> LIST_CHECK; `-` `*` `+` ->
 *   LIST_BULLET; `1.` -> LIST_NUMBER (nested items are flattened, Encly lists are flat);
 *   `>` -> QUOTE; `---` / `***` -> SEPARATOR; everything else -> TEXT, one block per paragraph.
 * - `✅` / `☐` lines (Notesnook text exports) are checklist items. An Obsidian callout keeps its title
 *   (`> [!note] Decisions` -> `Decisions`), a wiki link keeps its alias or name.
 * - Fenced code keeps its lines as TEXT, markers removed. Inline markdown (`**bold**`, links) is
 *   kept as typed, which reads fine as plain text; HTML tags are removed and entities decoded
 *   ([InlineHtml]) outside code spans, so no raw markup reaches the editor.
 */
object MarkdownBlockMapper {
    private val HEADING = Regex("""^ {0,3}(#{1,6})[ \t]+(.*?)(?:[ \t]+#+)?[ \t]*$""")
    private val CHECK = Regex("""^[ \t]*[-*+][ \t]+\[([ xX])\][ \t]+(.*)$""")
    private val GLYPH_ITEM = Regex("""^[ \t]*([✅☑☐])[ \t]*(.*)$""")
    private val CALLOUT = Regex("""^\[![A-Za-z-]+][+-]?[ \t]*""")
    private val WIKI_LINK = Regex("""!?\[\[([^\]|\n]+)(?:\|([^\]\n]+))?]]""")
    private val BULLET = Regex("""^[ \t]*[-*+][ \t]+(.*)$""")
    private val NUMBER = Regex("""^[ \t]*\d{1,9}[.)][ \t]+(.*)$""")
    private val QUOTE = Regex("""^ {0,3}>[ \t]?(.*)$""")
    private val SEPARATOR = Regex("""^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$""")
    private val FENCE = Regex("""^ {0,3}(```|~~~).*$""")
    private val CODE_SPAN = Regex("""`[^`\n]+`""")
    private val AUTOLINK = Regex("""<((?:https?://|mailto:)[^\s<>]+|[^\s<>@/]+@[^\s<>@/]+)>""")

    fun toBlocks(markdown: String): List<Block> = Builder().apply { markdown.lines().forEach(::line) }.finish()

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
    )

    private class Builder {
        private val blocks = ArrayList<Block>()
        private val paragraph = ArrayList<String>()
        private val quote = ArrayList<String>()
        private val code = ArrayList<String>()
        private val items = ArrayList<ItemListBlock>()
        private var listType: BlockType? = null
        private var inFence = false

        fun line(raw: String) {
            val fence = FENCE.matches(raw)
            when {
                inFence && fence -> closeFence()

                inFence -> code += raw

                fence -> {
                    flushAll()
                    inFence = true
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
            when {
                SEPARATOR.matches(raw) -> {
                    flushAll()
                    blocks += Block.SeparatorBlock()
                }

                heading != null -> {
                    flushAll()
                    val level = heading.groupValues[1].length
                    blocks += ImportBlocks.heading(inline(heading.groupValues[2]), level)
                }

                check != null -> item(BlockType.LIST_CHECK, check.groupValues[2], check.groupValues[1] != " ")

                glyph != null -> item(BlockType.LIST_CHECK, glyph.groupValues[2], glyph.groupValues[1] != "☐")

                bullet != null -> item(BlockType.LIST_BULLET, bullet.groupValues[1], false)

                number != null -> item(BlockType.LIST_NUMBER, number.groupValues[1], false)

                quoted != null -> {
                    flushParagraph()
                    flushList()
                    quote += inline(CALLOUT.replaceFirst(quoted.groupValues[1], ""))
                }

                // Indented text right after a list item continues that item.
                listType != null && raw.first().isWhitespace() -> {
                    val last = items.removeAt(items.lastIndex)
                    items += last.copy(value = (last.value + " " + inline(raw.trim())).trim())
                }

                else -> {
                    flushList()
                    flushQuote()
                    paragraph += inline(raw.trimEnd())
                }
            }
        }

        private fun item(type: BlockType, text: String, checked: Boolean) {
            flushParagraph()
            flushQuote()
            if (listType != type) flushList()
            listType = type
            items += ItemListBlock(value = inline(text).trim(), isCheck = checked)
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
            val kept = items.filter { it.value.isNotBlank() }
            items.clear()
            listType = null
            if (kept.isNotEmpty()) blocks += ImportBlocks.list(type, kept)
        }
    }
}
