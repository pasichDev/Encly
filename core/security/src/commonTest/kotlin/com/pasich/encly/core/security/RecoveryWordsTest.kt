package com.pasich.encly.core.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        assertTrue(RecoveryWords.isValidPhrase(Mnemonic.generate()))
        assertFalse(RecoveryWords.isValidPhrase(List(12) { "abandon" }.joinToString(" ").toCharArray()))
    }
}
