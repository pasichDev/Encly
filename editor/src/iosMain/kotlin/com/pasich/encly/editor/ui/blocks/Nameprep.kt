package com.pasich.encly.editor.ui.blocks

import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCompatibilityMapping

/**
 * The part of `java.net.IDN.toASCII` (IDNA 2003: Nameprep, RFC 3491, then Punycode) that [Punycode] does not do,
 * for iOS, where Kotlin/Native's Foundation has no IDN call. A label with a non-ASCII character goes through
 * the Nameprep steps in the order of the RFC:
 * - the IDNA dots (U+3002, U+FF0E, U+FF61) separate labels, as they do for the JDK;
 * - characters that map to nothing (RFC 3454 table B.1: soft hyphen, zero-width joiners, variation selectors,
 *   BOM, ...) are dropped, and the case is folded (`ß` is `ss`, final `ς` is `σ`);
 * - the label is normalized to NFKC with Foundation's own `precomposedStringWithCompatibilityMapping`;
 * - a label with a character Nameprep prohibits (non-ASCII spaces and controls, private use, non-characters,
 *   surrogates, tagging, bidi controls, ...) makes the name invalid, and so does one that starts with `xn--`
 *   after all this, which would pass for an already encoded label.
 *
 * Not done: the bidirectional rules of RFC 3454 section 6, and the rejection of code points that Unicode 3.2
 * had not assigned yet (the JDK refuses a name with an emoji, this accepts it, as browsers do). A name with
 * only ASCII characters is returned by [Punycode.toAsciiHost] as it is, as in the JDK.
 */
@Suppress("MagicNumber") // The code point ranges of RFC 3454 are the data of this file.
internal object Nameprep {
    private const val ACE_PREFIX = "xn--"
    private const val ASCII_END = 0x80
    private const val SUPPLEMENTARY_START = 0x10000
    private const val SURROGATE_BITS = 10

    /** [host] with its labels prepared, ready for [Punycode]; null when a label is prohibited. */
    fun prepare(host: String): String? {
        val labels = host.split('.', IDEOGRAPHIC_FULL_STOP, FULLWIDTH_FULL_STOP, HALFWIDTH_IDEOGRAPHIC_FULL_STOP)
        val prepared = ArrayList<String>(labels.size)
        for (label in labels) {
            prepared += if (label.all { it.code < ASCII_END }) label else prepareLabel(label) ?: return null
        }
        return prepared.joinToString(".")
    }

    private fun prepareLabel(label: String): String? {
        val mapped = StringBuilder(label.length)
        for (char in label) {
            if (isMappedToNothing(char.code)) continue
            when (char) {
                'ß' -> mapped.append("ss")
                'ς' -> mapped.append('σ')
                else -> mapped.append(char.lowercaseChar())
            }
        }
        val normalized = (mapped.toString() as NSString).precomposedStringWithCompatibilityMapping.lowercase()
        if (normalized.startsWith(ACE_PREFIX) || isProhibited(normalized)) return null
        return normalized
    }

    /** RFC 3454 table B.1. */
    private fun isMappedToNothing(code: Int): Boolean = code == 0x00AD || code == 0x034F || code == 0x1806 ||
        code in 0x180B..0x180D || code in 0x200B..0x200D || code == 0x2060 || code in 0xFE00..0xFE0F ||
        code == 0xFEFF

    /** The prohibited output of the Nameprep profile (RFC 3491 section 5), tables C.1.2 and C.2.2 to C.9. */
    private fun isProhibited(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val paired = char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()
            val code = if (paired) {
                SUPPLEMENTARY_START + ((char - Char.MIN_HIGH_SURROGATE) shl SURROGATE_BITS) +
                    (text[index + 1] - Char.MIN_LOW_SURROGATE)
            } else {
                char.code
            }
            if (isProhibitedPoint(code, paired)) return true
            index += if (paired) 2 else 1
        }
        return false
    }

    @Suppress("CyclomaticComplexMethod") // One flat list of the RFC's ranges.
    private fun isProhibitedPoint(code: Int, paired: Boolean): Boolean = when {
        // C.1.2 non-ASCII spaces, C.2.2 non-ASCII controls.
        code == 0x00A0 || code == 0x1680 || code in 0x2000..0x200B || code == 0x202F || code == 0x205F ||
            code == 0x3000 -> true

        code in 0x0080..0x009F || code == 0x06DD || code == 0x070F || code == 0x180E || code == 0x200C ||
            code == 0x200D || code == 0x2028 || code == 0x2029 || code in 0x2060..0x2063 ||
            code in 0x206A..0x206F || code == 0xFEFF || code in 0xFFF9..0xFFFC || code in 0x1D173..0x1D17A -> true

        // C.3 private use, C.4 non-characters, C.5 surrogates, C.6 and C.7, C.8 bidi controls and more.
        code in 0xE000..0xF8FF || code in 0xF0000..0xFFFFD || code in 0x100000..0x10FFFD -> true

        code in 0xFDD0..0xFDEF || (code and 0xFFFE) == 0xFFFE -> true

        code in 0xD800..0xDFFF && !paired -> true

        code in 0xFFF9..0xFFFD || code in 0x2FF0..0x2FFB -> true

        code == 0x0340 || code == 0x0341 || code == 0x200E || code == 0x200F || code in 0x202A..0x202E -> true

        // C.9 tagging characters.
        code == 0xE0001 || code in 0xE0020..0xE007F -> true

        else -> false
    }

    private const val IDEOGRAPHIC_FULL_STOP = '。'
    private const val FULLWIDTH_FULL_STOP = '．'
    private const val HALFWIDTH_IDEOGRAPHIC_FULL_STOP = '｡'
}
