package com.pasich.encly.core.security

import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryWordsTest {
    @Test
    fun knowsTheWordlist() {
        assertTrue(RecoveryWords.isWord("abandon"))
        assertTrue(RecoveryWords.isWord("zoo"))
        assertFalse(RecoveryWords.isWord("aban"))
        assertFalse(RecoveryWords.isWord("Abandon"))
    }

    @Test
    fun prefixes() {
        assertTrue(RecoveryWords.isPrefix(""))
        assertTrue(RecoveryWords.isPrefix("ab"))
        assertTrue(RecoveryWords.isPrefix("zoo"))
        assertFalse(RecoveryWords.isPrefix("zz"))
        assertFalse(RecoveryWords.isPrefix("abandonx"))
    }

    @Test
    fun checksum() {
        assertTrue(RecoveryWords.isValidPhrase(MnemonicCode(WordCount.COUNT_12).chars))
        assertFalse(RecoveryWords.isValidPhrase(List(12) { "abandon" }.joinToString(" ").toCharArray()))
    }
}
