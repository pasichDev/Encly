package com.pasich.encly.data.export

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

    private fun key(path: String) = normalizeNfc(path).lowercase()

    companion object {
        const val EXTENSION = ".md"
        const val MAX_NAME_BYTES = 100
        private val RESERVED = Regex("""^(?:CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])$""", RegexOption.IGNORE_CASE)

        /** [title] reduced to characters that are safe in a file name; may be empty. */
        fun sanitize(title: String): String {
            val kept = keepFileNameCharacters(normalizeNfc(title))
            val spaced = kept.replace(Regex(" {2,}"), " ").trim().trimStart('.', ' ').trimEnd('.', ' ')
            return if (RESERVED.matches(spaced.substringBefore('.').trim())) "_$spaced" else spaced
        }

        /** The longest prefix of [text] that is at most [bytes] bytes of UTF-8, ending on a character. */
        fun cutBytes(text: String, bytes: Int): String = utf8Prefix(text, bytes)
    }
}
