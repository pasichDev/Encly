package com.pasich.encly.core.security.bip39

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** BIP-39 against the official vectors, and the rules of its checksum and word checks. */
class Bip39Test {
    private val abandon = List(11) { "abandon" }.joinToString(" ") + " about"

    private fun encode(entropy: ByteArray): String = Bip39().encode(entropy).concatToString()

    @Test
    fun officialVectorsEncode() {
        assertEquals(24, Bip39Vectors.all.size)
        Bip39Vectors.all.forEach { (entropy, mnemonic) -> assertEquals(mnemonic, encode(entropy)) }
    }

    @Test
    fun officialVectorsDecode() {
        Bip39Vectors.all.forEach { (entropy, mnemonic) ->
            assertContentEquals(entropy, Bip39().decode(mnemonic.toCharArray()), mnemonic)
            assertTrue(Bip39().isValid(mnemonic.toCharArray()), mnemonic)
        }
    }

    @Test
    fun entropyOfEverySizeRoundTrips() {
        listOf(16, 20, 24, 28, 32).forEach { size ->
            val entropy = ByteArray(size) { (it * 7 + size).toByte() }
            val phrase = Bip39().encode(entropy)
            assertEquals(size * 3 / 4, phrase.concatToString().split(' ').size)
            assertContentEquals(entropy, Bip39().decode(phrase))
        }
    }

    @Test
    fun oddEntropySizesAreRefused() {
        listOf(0, 15, 17, 33).forEach { size ->
            assertFailsWith<IllegalArgumentException> { Bip39().encode(ByteArray(size)) }
        }
    }

    @Test
    fun decodeWipesNothingItWasGiven() {
        val phrase = abandon.toCharArray()
        Bip39().decode(phrase)
        assertEquals(abandon, phrase.concatToString())
    }

    @Test
    fun aWrongChecksumDecodesToNothing() {
        assertNull(Bip39().decode(abandon.replaceAfterLast(' ', "abandon").toCharArray()))
        assertFalse(Bip39().isValid(abandon.replaceAfterLast(' ', "abandon").toCharArray()))
    }

    @Test
    fun anUnknownWordDecodesToNothing() {
        assertNull(Bip39().decode(abandon.replaceAfterLast(' ', "aboot").toCharArray()))
        assertNull(Bip39().decode(abandon.replaceAfterLast(' ', "abo").toCharArray()))
        assertNull(Bip39().decode(abandon.replace("abandon", "abandonx").toCharArray()))
    }

    @Test
    fun aWrongWordCountDecodesToNothing() {
        val words = abandon.split(' ')
        listOf(0, 1, 11, 13, 14, 25).forEach { count ->
            val phrase = List(count) { words[it % 11] }.joinToString(" ")
            assertNull(Bip39().decode(phrase.toCharArray()), "$count")
        }
    }

    @Test
    fun theChecksumOfEveryLengthIsChecked() {
        listOf(16, 20, 24, 28, 32).forEach { size ->
            val phrase = Bip39().encode(ByteArray(size) { it.toByte() })
            assertNotNull(Bip39().decode(phrase))
            // The last word carries the checksum bits: another word of the same entropy bits fails.
            val words = phrase.concatToString().split(' ').toMutableList()
            val last = words.last()
            words[words.lastIndex] = Bip39Wordlist.words.first { it != last && it.take(1) == last.take(1) }
            val changed = words.joinToString(" ").toCharArray()
            // Some neighbours still check out (the checksum is only a few bits); none may equal the original.
            assertTrue(Bip39().decode(changed)?.contentEquals(ByteArray(size) { it.toByte() }) != true)
        }
    }
}
