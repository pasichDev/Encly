package com.pasich.encly.core.security

import java.nio.CharBuffer
import java.security.MessageDigest

/**
 * The one normalization every recovery-phrase key goes through: the vault's recovery KEK and
 * the backup root both hash exactly these bytes, so a phrase that opens one opens the other.
 */
internal object Mnemonic {
    /** Lower-cases and collapses any whitespace between words to a single space. */
    fun normalize(words: CharArray): CharArray {
        val out = CharArray(words.size)
        var length = 0
        var pendingSpace = false
        for (c in words) {
            if (c.isWhitespace()) {
                pendingSpace = length > 0
            } else {
                if (pendingSpace) {
                    out[length++] = ' '
                    pendingSpace = false
                }
                out[length++] = c.lowercaseChar()
            }
        }
        return try {
            out.copyOf(length)
        } finally {
            SensitiveDataCleaner.clear(out)
        }
    }

    /** SHA-256 of the normalized words in UTF-8. No String copy of the words is made. */
    fun seedHash(words: CharArray): ByteArray {
        val normalized = normalize(words)
        val utf8 = utf8(normalized)
        SensitiveDataCleaner.clear(normalized)
        return try {
            MessageDigest.getInstance("SHA-256").digest(utf8)
        } finally {
            SensitiveDataCleaner.clear(utf8)
        }
    }

    private fun utf8(chars: CharArray): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (buffer.hasArray()) SensitiveDataCleaner.clear(buffer.array())
        return bytes
    }
}
