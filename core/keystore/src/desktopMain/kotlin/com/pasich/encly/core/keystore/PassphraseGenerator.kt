package com.pasich.encly.core.keystore

import com.pasich.encly.core.crypto.Crypto
import com.pasich.encly.core.security.SecretRule
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.core.security.bip39.Bip39Wordlist

/**
 * A suggested passphrase for the desktop password: [DEFAULT_WORD_COUNT] words of the BIP-39 English list that
 * Encly already bundles, drawn independently and uniformly from the platform CSPRNG and joined by single spaces
 * (five words are 55 bits). The draw uses rejection sampling, so no word is likelier than another.
 *
 * The result is a `CharArray` for the caller to wipe. The words themselves are the list's own shared `String`s,
 * which are public data.
 */
internal object PassphraseGenerator {
    const val DEFAULT_WORD_COUNT = 5

    private const val MAX_WORD_COUNT = 24
    private const val MAX_ATTEMPTS = 100
    private const val DRAW_BYTES = 4
    private const val BYTE_MASK = 0xFF
    private const val RANGE = 1L shl Int.SIZE_BITS

    /**
     * [wordCount] words of [words] separated by single spaces. The text is also guaranteed to pass the rule for
     * setting a password ([PasswordCanonical.checkForSet]; the words are ASCII, so the text is already in NFC): a
     * draw that does not (five equal short words, a chance of about one in 10^13) is thrown away and drawn again,
     * which keeps the rest uniform. [random] gives [Crypto.randomBytes]-style bytes and is the seam of the tests.
     */
    fun generate(
        wordCount: Int = DEFAULT_WORD_COUNT,
        words: List<String> = Bip39Wordlist.words,
        random: (Int) -> ByteArray = Crypto::randomBytes,
    ): CharArray {
        require(wordCount in 1..MAX_WORD_COUNT) { "wordCount must be in 1..$MAX_WORD_COUNT" }
        require(words.size > 1) { "The word list is too short" }
        repeat(MAX_ATTEMPTS) {
            val picked = Array(wordCount) { words[uniformIndex(words.size, random)] }
            val phrase = join(picked)
            if (PasswordCanonical.checkForSet(phrase) == SecretRule.OK) return phrase
            SensitiveDataCleaner.clear(phrase)
        }
        error("No passphrase passed the password rule in $MAX_ATTEMPTS draws; is the word list degenerate?")
    }

    /**
     * A uniform integer in `0 until bound` from [random] by rejection sampling: it takes 32 bits at a time and
     * discards a draw that falls in the uneven tail of the range, so that `value % bound` has no modulo bias.
     * The drawn bytes are wiped.
     */
    fun uniformIndex(bound: Int, random: (Int) -> ByteArray): Int {
        require(bound >= 1) { "bound must be positive" }
        val limit = RANGE / bound * bound
        while (true) {
            val raw = random(DRAW_BYTES)
            try {
                require(raw.size == DRAW_BYTES) { "The random source returned the wrong number of bytes" }
                var value = 0L
                for (byte in raw) value = (value shl Byte.SIZE_BITS) or (byte.toLong() and BYTE_MASK.toLong())
                if (value < limit) return (value % bound).toInt()
            } finally {
                SensitiveDataCleaner.clear(raw)
            }
        }
    }

    private fun join(picked: Array<String>): CharArray {
        val result = CharArray(picked.sumOf { it.length } + picked.size - 1)
        var at = 0
        for ((index, word) in picked.withIndex()) {
            if (index > 0) result[at++] = ' '
            word.toCharArray(result, at, 0, word.length)
            at += word.length
        }
        return result
    }
}
