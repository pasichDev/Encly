package com.pasich.encly.data.handoff

/**
 * Editor.js stores inline formatting as HTML inside a block's text (`<b>`, `<i>`, `<mark>`,
 * `<code>`, `<a>`, `<br>`, entities). Encly blocks are plain text, so the formatting is dropped
 * and only what the user typed is kept: no raw markup reaches the vault.
 */
object InlineHtml {
    private val LINE_BREAK = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""</?[A-Za-z][^<>]*>""")
    private val ENTITY = Regex("""&(#[0-9]{1,7}|#[xX][0-9A-Fa-f]{1,6}|[A-Za-z]{2,8});""")
    private const val HEX = 16

    // As char codes, not literals: the characters are invisible in source.
    private const val NO_BREAK_SPACE_CODE = 0x00A0
    private const val ZERO_WIDTH_SPACE_CODE = 0x200B
    private const val BYTE_ORDER_MARK_CODE = 0xFEFF
    private val NO_BREAK_SPACE = Char(NO_BREAK_SPACE_CODE)
    private val ZERO_WIDTH_SPACE = Char(ZERO_WIDTH_SPACE_CODE)
    private val BYTE_ORDER_MARK = Char(BYTE_ORDER_MARK_CODE)

    private val NAMED = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
    )

    /**
     * [html] as plain text: `<br>` becomes a line break, every other tag is removed, entities
     * are decoded once (so a typed "&lt;b&gt;" stays the text "<b>"), and the invisible
     * characters the editor leaves behind are dropped. Not trimmed.
     */
    fun toPlainText(html: String): String {
        val withoutTags = TAG.replace(LINE_BREAK.replace(html, "\n"), "")
        return ENTITY.replace(withoutTags) { decode(it.groupValues[1]) ?: it.value }
            .replace(NO_BREAK_SPACE, ' ')
            .replace(ZERO_WIDTH_SPACE.toString(), "")
            .replace(BYTE_ORDER_MARK.toString(), "")
    }

    private fun decode(entity: String): String? {
        val codePoint = when {
            entity.startsWith("#x", ignoreCase = true) -> entity.substring(2).toIntOrNull(HEX)
            entity.startsWith("#") -> entity.substring(1).toIntOrNull()
            else -> return NAMED[entity]
        }
        return codePoint?.takeIf(::isTextCodePoint)?.let { String(Character.toChars(it)) }
    }

    /** NUL and lone surrogates are not text; such an entity is kept as typed. */
    private fun isTextCodePoint(codePoint: Int): Boolean = Character.isValidCodePoint(codePoint) &&
        codePoint != 0 &&
        codePoint !in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code
}
