package com.pasich.encly.core.security.bip39

import com.pasich.encly.core.security.SensitiveDataCleaner

/**
 * BIP-39 English mnemonics: entropy to words and the checksum check, nothing else. Encly never
 * turns the words into a BIP-39 seed (no PBKDF2): its keys come from SHA-256 of the normalized
 * words (see `Mnemonic`), so that step is deliberately absent.
 *
 * Plain Kotlin plus a [Sha256], so it can move to common code. Phrases are char arrays and every
 * intermediate array is wiped; arrays handed back belong to the caller.
 */
internal class Bip39(private val sha256: Sha256 = JvmSha256) {

    /**
     * The phrase for [entropy] (16, 20, 24, 28 or 32 bytes: 12 to 24 words), words joined by one
     * space, lower case.
     */
    fun encode(entropy: ByteArray): CharArray {
        require(entropy.size in ENTROPY_BYTES) { "BIP-39 entropy is 16 to 32 bytes in steps of 4" }
        val hash = sha256.digest(entropy)
        // Entropy followed by the first hash byte; only its top entropy/32 bits are the checksum.
        val bits = entropy + hash[0]
        val wordCount = (entropy.size * BITS_PER_BYTE + entropy.size / CHECKSUM_DIVISOR) / BITS_PER_WORD
        return try {
            val words = List(wordCount) { Bip39Wordlist.words[readWord(bits, it)] }
            val phrase = CharArray(words.sumOf { it.length } + wordCount - 1)
            var next = 0
            words.forEachIndexed { index, word ->
                if (index > 0) phrase[next++] = ' '
                word.forEach { phrase[next++] = it }
            }
            phrase
        } finally {
            SensitiveDataCleaner.clear(bits)
            SensitiveDataCleaner.clear(hash)
        }
    }

    /**
     * The entropy behind [phrase] (lower case, words separated by exactly one space), or null when
     * the word count is not 12/15/18/21/24, a word is not in the list, or the checksum is wrong.
     */
    @Suppress("ReturnCount") // Each rejection ends the decode at once; nothing is left to clean up but `bits`.
    fun decode(phrase: CharArray): ByteArray? {
        val wordCount = phrase.count { it == ' ' } + 1
        if (wordCount !in WORD_COUNTS) return null
        val entropyBytes = wordCount / WORDS_PER_GROUP * BYTES_PER_GROUP
        val bits = ByteArray(entropyBytes + 1)
        var start = 0
        for (word in 0 until wordCount) {
            var end = start
            while (end < phrase.size && phrase[end] != ' ') end++
            val index = Bip39Wordlist.indexOf(phrase, start, end)
            if (index < 0) {
                SensitiveDataCleaner.clear(bits)
                return null
            }
            writeWord(bits, word, index)
            start = end + 1
        }
        val entropy = bits.copyOf(entropyBytes)
        val hash = sha256.digest(entropy)
        // One compare after the work, with no early exit on which bit differs.
        val checksumMask = (BYTE_MASK shl (BITS_PER_BYTE - entropyBytes / CHECKSUM_DIVISOR)) and BYTE_MASK
        val difference = (bits[entropyBytes].toInt() xor hash[0].toInt()) and checksumMask
        SensitiveDataCleaner.clear(bits)
        SensitiveDataCleaner.clear(hash)
        if (difference == 0) return entropy
        SensitiveDataCleaner.clear(entropy)
        return null
    }

    /** Whether [phrase] decodes, i.e. the word count, every word and the checksum are right. */
    fun isValid(phrase: CharArray): Boolean {
        val entropy = decode(phrase) ?: return false
        SensitiveDataCleaner.clear(entropy)
        return true
    }

    private fun readWord(bits: ByteArray, word: Int): Int {
        var value = 0
        for (position in word * BITS_PER_WORD until (word + 1) * BITS_PER_WORD) {
            val bit = (bits[position / BITS_PER_BYTE].toInt() shr (BITS_PER_BYTE - 1 - position % BITS_PER_BYTE)) and 1
            value = (value shl 1) or bit
        }
        return value
    }

    private fun writeWord(bits: ByteArray, word: Int, index: Int) {
        for (bit in 0 until BITS_PER_WORD) {
            if ((index shr (BITS_PER_WORD - 1 - bit)) and 1 == 0) continue
            val position = word * BITS_PER_WORD + bit
            val mask = TOP_BIT shr (position % BITS_PER_BYTE)
            bits[position / BITS_PER_BYTE] = (bits[position / BITS_PER_BYTE].toInt() or mask).toByte()
        }
    }

    private companion object {
        const val BITS_PER_WORD = 11
        const val BITS_PER_BYTE = 8
        const val BYTE_MASK = 0xFF
        const val TOP_BIT = 0x80

        /** Every 3 words carry 4 bytes of entropy: 33 bits, 32 of entropy and one of checksum. */
        const val WORDS_PER_GROUP = 3
        const val BYTES_PER_GROUP = 4

        /** The checksum is entropy bits / 32, i.e. entropy bytes / 4 bits. */
        const val CHECKSUM_DIVISOR = 4
        val ENTROPY_BYTES = listOf(16, 20, 24, 28, 32)
        val WORD_COUNTS = listOf(12, 15, 18, 21, 24)
    }
}
