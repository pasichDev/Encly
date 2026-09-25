package com.pasich.encly.core.security

import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode

/** The English BIP39 wordlist the recovery phrase is drawn from, for checking typed words. */
object RecoveryWords {
    /** Words in a recovery phrase. */
    const val COUNT = 12

    /** Sorted, as BIP39 publishes it. */
    private val words: List<String> by lazy { Mnemonics.getCachedWords(Mnemonics.DEFAULT_LANGUAGE_CODE) }
    private val wordSet: Set<String> by lazy { words.toHashSet() }

    fun isWord(word: String): Boolean = word in wordSet

    /** Whether some word starts with [prefix]; an empty prefix always does. */
    fun isPrefix(prefix: String): Boolean {
        if (prefix.isEmpty()) return true
        val index = words.binarySearch(prefix)
        return index >= 0 || words.getOrNull(-index - 1)?.startsWith(prefix) == true
    }

    /** Whether [phrase] (lower case, single spaces) is a valid mnemonic, checksum included. */
    fun isValidPhrase(phrase: CharArray): Boolean = try {
        MnemonicCode(phrase).validate()
        true
    } catch (_: Exception) {
        false
    }
}
