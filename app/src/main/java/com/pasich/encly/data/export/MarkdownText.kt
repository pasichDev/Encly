package com.pasich.encly.data.export

/**
 * Escaping of text for Markdown: what the user typed must read as typed, and never become other
 * Markdown (a heading, a list, a link, HTML). Used by every file of the export.
 */
object MarkdownText {
    private const val INDENT_CODE_WIDTH = 4
    private const val NO_BREAK_SPACE = '\u00A0'
    private val ENTITY = Regex("""&(?:#\d{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,31});""")
    private val ORDERED_MARKER = Regex("""^(\d{1,9})([.)])(\s|$)""")
    private val UNORDERED_MARKER = Regex("""^([-+])(\s|$)""")
    private val RULE_OR_SETEXT = Regex("""^(?:[-_*]\s*){3,}$|^-+\s*$|^=+\s*$""")
    private val ATX_HEADING = Regex("""^#{1,6}(\s|$)""")
    private val WHITESPACE = Regex("""\s+""")

    /** The characters that start inline Markdown wherever they stand (`_` and `&` are judged in place). */
    private val INLINE_MARKS = setOf('\\', '`', '*', '[', ']', '<', '~', '|')

    /**
     * [text] as escaped Markdown lines: a line break inside a paragraph ends the line with `\`
     * (a hard break), a blank line stays one blank line, and trailing spaces are dropped.
     */
    fun hardBreakLines(text: String): List<String> {
        val raw = text.replace("\r\n", "\n").replace('\r', '\n').trim('\n').split('\n')
        // Control characters (a NUL, an escape) have no place in a text file; a tab is kept.
        val lines = raw.map { line ->
            escapeLineStart(escapeInline(line.filterNot { it.isISOControl() && it != '\t' }.trimEnd()))
        }
        val out = ArrayList<String>(lines.size)
        var blankRun = 0
        for ((index, line) in lines.withIndex()) {
            if (line.isBlank()) {
                if (blankRun++ == 0) out += ""
                continue
            }
            blankRun = 0
            val next = lines.getOrNull(index + 1)
            out += if (next != null && next.isNotBlank()) "$line\\" else line
        }
        return out.dropLastWhile { it.isEmpty() }.ifEmpty { listOf("") }
    }

    /** [text] as one escaped line, for a title or a list entry. */
    fun line(text: String): String = escapeLineStart(escapeInline(oneLine(text)))

    fun oneLine(text: String): String = text.filterNot { it.isISOControl() && !it.isWhitespace() }
        .replace(WHITESPACE, " ").trim()

    /** Escapes what would be read as inline Markdown. */
    fun escapeInline(text: String): String = buildString(text.length) {
        text.forEachIndexed { index, c ->
            when {
                c in INLINE_MARKS -> append('\\').append(c)

                // An underscore between two letters or digits is not emphasis; elsewhere it can be.
                c == '_' -> {
                    val inWord = text.getOrNull(index - 1)?.isLetterOrDigit() == true &&
                        text.getOrNull(index + 1)?.isLetterOrDigit() == true
                    if (!inWord) append('\\')
                    append('_')
                }

                c == '&' && ENTITY.matchesAt(text, index) -> append("&amp;")

                else -> append(c)
            }
        }
    }

    /** Escapes what would start a block when [line] (already inline-escaped) stands at a line's start. */
    internal fun escapeLineStart(line: String): String {
        val body = line.trimStart(' ', '\t')
        val lead = line.length - body.length
        val width = line.take(lead).sumOf { if (it == '\t') INDENT_CODE_WIDTH else 1 }
        val head = if (width >= INDENT_CODE_WIDTH) NO_BREAK_SPACE.toString() else line.take(lead)
        val escaped = when {
            ATX_HEADING.containsMatchIn(body) || body.startsWith(">") -> "\\$body"
            RULE_OR_SETEXT.matches(body) -> "\\$body"
            UNORDERED_MARKER.containsMatchIn(body) -> "\\$body"
            else -> ORDERED_MARKER.replace(body, "$1\\\\$2$3")
        }
        return head + escaped
    }
}
