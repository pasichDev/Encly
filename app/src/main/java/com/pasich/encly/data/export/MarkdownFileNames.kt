package com.pasich.encly.data.export

import java.text.Normalizer
import java.util.Locale

/**
 * File names for the exported notes: safe on every file system and in a zip, and unique.
 *
 * A name is built from the note's title, so it is treated as untrusted text: it is normalized to
 * NFC, loses control, format and separator characters (a right-to-left override can fake an
 * extension), the characters Windows and the zip format reject (`/ \ : * ? " < > |`), leading
 * dots (a hidden file, `..`) and trailing dots and spaces, avoids the device names Windows
 * reserves, and is cut to a byte budget at a character boundary. Two names that differ only in
 * case count as the same one, because macOS and Windows would overwrite one with the other.
 */
class MarkdownFileNames(reserved: Collection<String> = emptyList(), private val maxBytes: Int = MAX_NAME_BYTES) {
    private val taken = HashSet<String>()

    init {
        reserved.forEach { taken += key(it) }
    }

    /**
     * A unique `<name>.md` for [title] inside [folder] (`""` or e.g. `"archive/"`); [fallback] if
     * the title has no usable text.
     */
    fun next(title: String, fallback: String, folder: String = ""): String {
        val base = sanitize(title).ifEmpty { sanitize(fallback).ifEmpty { "note" } }
        var candidate = fit(base, "")
        var n = 1
        while (!taken.add(key(folder + candidate + EXTENSION))) {
            n++
            candidate = fit(base, " ($n)")
        }
        return folder + candidate + EXTENSION
    }

    /** [base] cut so that it, [suffix] and the extension fit the byte budget. */
    private fun fit(base: String, suffix: String): String {
        val room = maxBytes - EXTENSION.length - suffix.toByteArray().size
        return cutBytes(base, room.coerceAtLeast(1)).trimEnd('.', ' ') + suffix
    }

    private fun key(path: String) = Normalizer.normalize(path, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    companion object {
        const val EXTENSION = ".md"
        const val MAX_NAME_BYTES = 100
        private const val TAB = 0x09
        private const val LINE_FEED = 0x0A
        private const val CARRIAGE_RETURN = 0x0D
        private val FORBIDDEN = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        private val RESERVED = Regex("""^(?:CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])$""", RegexOption.IGNORE_CASE)

        /** [title] reduced to characters that are safe in a file name; may be empty. */
        fun sanitize(title: String): String {
            val text = Normalizer.normalize(title, Normalizer.Form.NFC)
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
            val spaced = kept.toString().replace(Regex(" {2,}"), " ").trim().trimStart('.', ' ').trimEnd('.', ' ')
            return if (RESERVED.matches(spaced.substringBefore('.').trim())) "_$spaced" else spaced
        }

        /** Not a control, format, separator, private-use, unassigned or lone surrogate character. */
        private fun isPlain(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
            Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
            Character.PRIVATE_USE, Character.UNASSIGNED, Character.SURROGATE,
            -> false

            else -> true
        }

        /** The longest prefix of [text] that is at most [bytes] bytes of UTF-8, ending on a character. */
        fun cutBytes(text: String, bytes: Int): String {
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
    }
}
