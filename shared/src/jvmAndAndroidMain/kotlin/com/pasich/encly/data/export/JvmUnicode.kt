package com.pasich.encly.data.export

import java.text.Normalizer

/*
 * The JVM side (Android and desktop) of the file-name rules in MarkdownFileNames.kt: Unicode
 * normalization and the Unicode category of a code point have no Kotlin common equivalent, so they
 * live here, the one file of the export that uses java.text and java.lang.Character.
 */

private const val TAB = 0x09
private const val LINE_FEED = 0x0A
private const val CARRIAGE_RETURN = 0x0D
private val FORBIDDEN = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

/** [text] in Unicode normalization form C. */
internal actual fun normalizeNfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

/**
 * [text] without the control, format, separator, private-use, unassigned and lone-surrogate
 * characters and without the characters file systems reject; every kind of space becomes one
 * plain space. (Runs of spaces, dots at the ends and reserved names are the caller's.)
 */
internal actual fun keepFileNameCharacters(text: String): String {
    val kept = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        val cp = text.codePointAt(index)
        index += Character.charCount(cp)
        when {
            Character.isISOControl(cp) && cp != TAB && cp != LINE_FEED && cp != CARRIAGE_RETURN -> Unit
            Character.isSpaceChar(cp) || Character.isWhitespace(cp) -> kept.append(' ')
            cp.toChar() in FORBIDDEN && cp < Character.MIN_SUPPLEMENTARY_CODE_POINT -> Unit
            isPlain(cp) -> kept.appendCodePoint(cp)
        }
    }
    return kept.toString()
}

/** Not a control, format, separator, private-use, unassigned or lone surrogate character. */
private fun isPlain(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
    Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
    Character.PRIVATE_USE, Character.UNASSIGNED, Character.SURROGATE,
    -> false

    else -> true
}

/** The longest prefix of [text] that is at most [bytes] bytes of UTF-8, ending on a character. */
internal actual fun utf8Prefix(text: String, bytes: Int): String {
    var used = 0
    var end = 0
    while (end < text.length) {
        val cp = text.codePointAt(end)
        val size = String(Character.toChars(cp)).toByteArray().size
        if (used + size > bytes) break
        used += size
        end += Character.charCount(cp)
    }
    return text.substring(0, end)
}
