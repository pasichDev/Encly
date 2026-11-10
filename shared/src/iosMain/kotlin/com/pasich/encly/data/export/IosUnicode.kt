@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.data.export

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCanonicalMapping

/*
 * The iOS side of the file-name rules in MarkdownFileNames.kt. Normalization is Foundation's. The Unicode category
 * of a character below U+10000 comes from Kotlin's own `CharCategory`; above it Kotlin has none, so Foundation's
 * `illegalCharacterSet` (the unassigned code points) and the ranges of the private-use planes, the tag characters
 * and the musical-symbol format characters stand in for it.
 */

private const val TAB = 0x09
private const val LINE_FEED = 0x0A
private const val CARRIAGE_RETURN = 0x0D
private const val SUPPLEMENTARY_START = 0x10000
private const val SURROGATE_BITS = 10
private const val BYTES_1 = 0x80
private const val BYTES_2 = 0x800
private const val BYTES_3 = 0x10000
private val FORBIDDEN = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

/** [text] in Unicode normalization form C. */
internal actual fun normalizeNfc(text: String): String = (text as NSString).precomposedStringWithCanonicalMapping

/**
 * [text] without the control, format, separator, private-use, unassigned and lone-surrogate
 * characters and without the characters file systems reject; every kind of space becomes one
 * plain space. (Runs of spaces, dots at the ends and reserved names are the caller's.)
 */
internal actual fun keepFileNameCharacters(text: String): String {
    val kept = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        val char = text[index]
        val paired = char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()
        if (paired) {
            val cp = SUPPLEMENTARY_START + ((char - Char.MIN_HIGH_SURROGATE) shl SURROGATE_BITS) +
                (text[index + 1] - Char.MIN_LOW_SURROGATE)
            if (isPlainSupplementary(cp)) kept.append(char).append(text[index + 1])
            index += 2
            continue
        }
        index++
        when {
            char.isISOControl() && char.code != TAB && char.code != LINE_FEED && char.code != CARRIAGE_RETURN -> Unit
            char.isWhitespace() -> kept.append(' ')
            char in FORBIDDEN -> Unit
            isPlain(char) -> kept.append(char)
        }
    }
    return kept.toString()
}

/** Not a control, format, separator, private-use, unassigned or lone surrogate character. */
private fun isPlain(char: Char): Boolean = when (char.category) {
    CharCategory.CONTROL, CharCategory.FORMAT, CharCategory.LINE_SEPARATOR, CharCategory.PARAGRAPH_SEPARATOR,
    CharCategory.PRIVATE_USE, CharCategory.UNASSIGNED, CharCategory.SURROGATE,
    -> false

    else -> true
}

@Suppress("MagicNumber") // The code point ranges are the data of this function.
private fun isPlainSupplementary(cp: Int): Boolean = when {
    // Private-use planes 15 and 16.
    cp in 0xF0000..0x10FFFF -> false

    // Language tag characters (format).
    cp in 0xE0000..0xE007F -> false

    // Musical symbol format characters.
    cp in 0x1D173..0x1D17A -> false

    else -> !NSCharacterSet.illegalCharacterSet().longCharacterIsMember(cp.toUInt())
}

/** The longest prefix of [text] that is at most [bytes] bytes of UTF-8, ending on a character. */
@Suppress("MagicNumber") // The sizes of a UTF-8 sequence.
internal actual fun utf8Prefix(text: String, bytes: Int): String {
    var used = 0
    var end = 0
    while (end < text.length) {
        val char = text[end]
        val paired = char.isHighSurrogate() && end + 1 < text.length && text[end + 1].isLowSurrogate()
        val units = if (paired) 2 else 1
        val size = when {
            paired -> 4

            // A lone surrogate is written as "?", as on the JVM.
            char.isSurrogate() -> 1

            char.code < BYTES_1 -> 1

            char.code < BYTES_2 -> 2

            char.code < BYTES_3 -> 3

            else -> 4
        }
        if (used + size > bytes) break
        used += size
        end += units
    }
    return text.substring(0, end)
}
