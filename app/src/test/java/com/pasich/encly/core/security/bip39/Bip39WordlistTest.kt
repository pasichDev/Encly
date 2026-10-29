package com.pasich.encly.core.security.bip39

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class Bip39WordlistTest {
    private val words = Bip39Wordlist.words

    @Test
    fun isTheCanonicalEnglishList() {
        // SHA-256 of bitcoin/bips bip-0039/english.txt: the 2048 words, one per line, final newline.
        val canonical = "2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda"
        val digest = MessageDigest.getInstance("SHA-256").digest((words.joinToString("\n") + "\n").toByteArray())

        assertEquals(canonical, digest.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun hasTheShapeBip39Promises() {
        assertEquals(2048, words.size)
        assertEquals("abandon", words.first())
        assertEquals("zoo", words.last())
        assertEquals(words.sorted(), words)
        assertEquals(2048, words.toSet().size)
        assertTrue(words.all { word -> word.length in 3..8 && word.all { it in 'a'..'z' } })
        assertEquals(2048, words.map { it.take(4) }.toSet().size)
    }

    @Test
    fun lookupFindsEveryWordInPlace() {
        words.forEachIndexed { index, word ->
            val padded = "x $word y".toCharArray()
            assertEquals(word, index, Bip39Wordlist.indexOf(padded, 2, 2 + word.length))
        }
    }

    @Test
    fun lookupRejectsNearMisses() {
        listOf("", "a", "aban", "abandonn", "Abandon", "zoos", "zo", "~").forEach {
            assertEquals(it, -1, Bip39Wordlist.indexOf(it.toCharArray(), 0, it.length))
        }
    }
}
