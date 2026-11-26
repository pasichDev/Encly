package com.pasich.encly.data.importer

/** UTF-8 checks that never hold a whole file as text. */
internal object Utf8 {
    private const val SLICE = 64 * 1024
    private const val CONTINUATION_MASK = 0xC0
    private const val CONTINUATION = 0x80

    /**
     * Whether [bytes] are valid UTF-8, checked in slices so the whole file is never held as text. A slice
     * ends where a character starts, so none is cut in two.
     */
    fun isValid(bytes: ByteArray): Boolean {
        var start = 0
        var valid = true
        while (valid && start < bytes.size) {
            var end = minOf(start + SLICE, bytes.size)
            while (end < bytes.size && end > start && isContinuation(bytes[end])) end--
            valid = end > start && decodes(bytes, start, end)
            start = end
        }
        return valid
    }

    private fun decodes(bytes: ByteArray, start: Int, end: Int): Boolean = try {
        bytes.decodeToString(start, end, throwOnInvalidSequence = true)
        true
    } catch (_: CharacterCodingException) {
        false
    }

    private fun isContinuation(byte: Byte) = (byte.toInt() and CONTINUATION_MASK) == CONTINUATION
}
