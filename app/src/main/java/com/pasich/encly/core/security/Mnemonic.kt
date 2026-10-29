package com.pasich.encly.core.security

import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.core.security.bip39.Bip39

/**
 * The one normalization every recovery-phrase key goes through: the vault's recovery KEK and
 * the backup root both hash exactly these bytes, so a phrase that opens one opens the other.
 */
internal object Mnemonic {
    private const val ENTROPY_BYTES = 16

    /** A new 12-word recovery phrase from 128 bits of system randomness. */
    fun generate(): CharArray {
        val entropy = Crypto.randomBytes(ENTROPY_BYTES)
        return try {
            Bip39().encode(entropy)
        } finally {
            SensitiveDataCleaner.clear(entropy)
        }
    }

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
        val utf8 = Utf8.encode(normalized)
        SensitiveDataCleaner.clear(normalized)
        return try {
            Crypto.sha256(utf8)
        } finally {
            SensitiveDataCleaner.clear(utf8)
        }
    }
}
