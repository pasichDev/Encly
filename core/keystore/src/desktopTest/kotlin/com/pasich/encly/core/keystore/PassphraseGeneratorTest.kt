package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.SecretRule
import com.pasich.encly.core.security.bip39.Bip39Wordlist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PassphraseGeneratorTest {
    /** A random source that replays the given 32-bit values, big-endian, and records how many draws were made. */
    private class Replay(private vararg val values: Long) {
        var draws = 0

        fun source(): (Int) -> ByteArray = { size ->
            assertEquals(4, size)
            val value = values[draws++]
            ByteArray(4) { (value shr (24 - 8 * it)).toByte() }
        }
    }

    @Test
    fun defaultIsFiveWordsOfTheListSeparatedBySingleSpaces() {
        val phrase = String(PassphraseGenerator.generate())

        val parts = phrase.split(' ')
        assertEquals(5, parts.size)
        assertTrue(parts.all { it in Bip39Wordlist.words }, phrase)
        assertTrue(phrase == phrase.trim() && !phrase.contains("  "))
    }

    @Test
    fun theWordCountIsHonoured() {
        for (count in listOf(5, 8, 12)) {
            assertEquals(count, String(PassphraseGenerator.generate(wordCount = count)).split(' ').size)
        }
        // One word is shorter than the shortest password that may be set: no draw can pass, so it gives up.
        assertFailsWith<IllegalStateException> { PassphraseGenerator.generate(wordCount = 1) }
        assertFailsWith<IllegalArgumentException> { PassphraseGenerator.generate(wordCount = 0) }
        assertFailsWith<IllegalArgumentException> { PassphraseGenerator.generate(wordCount = 25) }
    }

    @Test
    fun everyPassphraseFromTheListPassesTheSetRule() {
        repeat(500) {
            val phrase = PassphraseGenerator.generate()
            assertEquals(SecretRule.OK, PasswordCanonical.checkForSet(phrase), String(phrase))
        }
    }

    @Test
    fun aDrawThatBreaksTheRuleIsDrawnAgain() {
        // Five times the first word, then five distinct ones: the first attempt has only 4 distinct characters.
        val words = listOf("aaa", "bbb", "ccc", "ddd", "eee")
        val replay = Replay(0, 0, 0, 0, 0, 0, 1, 2, 3, 4)

        val phrase = String(PassphraseGenerator.generate(words = words, random = replay.source()))

        assertEquals("aaa bbb ccc ddd eee", phrase)
        assertEquals(10, replay.draws)
    }

    @Test
    fun aWordListOfOneWordIsRefused() {
        assertFailsWith<IllegalArgumentException> { PassphraseGenerator.generate(words = listOf("only")) }
        assertFailsWith<IllegalArgumentException> { PassphraseGenerator.generate(words = emptyList()) }
    }

    @Test
    fun aDegenerateListGivesUpInsteadOfLoopingForever() {
        assertFailsWith<IllegalStateException> {
            PassphraseGenerator.generate(words = listOf("aa", "aa"), random = { ByteArray(4) })
        }
    }

    @Test
    fun theIndexIsThePlainRemainderWhenTheDrawIsInTheEvenRange() {
        val replay = Replay(0, 5, 10, 2047)

        val picked = List(4) { PassphraseGenerator.uniformIndex(2048, replay.source()) }

        // 2048 divides 2^32, so nothing is ever rejected.
        assertEquals(listOf(0, 5, 10, 2047), picked)
    }

    @Test
    fun aDrawFromTheUnevenTailIsRejectedAndRedrawn() {
        // bound 3: 2^32 = 3 * 1431655765 + 1, so the single value 4294967295 would favour 0 and is discarded.
        val replay = Replay(4_294_967_295, 4_294_967_294)

        val index = PassphraseGenerator.uniformIndex(3, replay.source())

        assertEquals(2, index) // 4294967294 % 3
        assertEquals(2, replay.draws)
    }

    @Test
    fun everyResidueIsReachableAndTheTopOfTheRangeIsHandledAsUnsigned() {
        // For bound 7 the accepted draws are below 4294967292 (a multiple of 7): 0xFFFFFFFB is the last of them.
        val replay = Replay(0xFFFFFFFBL, 0x80000000L, 0x7FFFFFFFL)

        assertEquals((0xFFFFFFFBL % 7).toInt(), PassphraseGenerator.uniformIndex(7, replay.source()))
        assertEquals((0x80000000L % 7).toInt(), PassphraseGenerator.uniformIndex(7, replay.source()))
        assertEquals((0x7FFFFFFFL % 7).toInt(), PassphraseGenerator.uniformIndex(7, replay.source()))
    }

    @Test
    fun theLastDrawBelowTheEvenLimitIsAcceptedAndEveryDrawFromTheLimitUpIsRejected() {
        // limit = the largest multiple of the bound that fits in 32 bits: the draws below it map evenly to the residues.
        for (bound in listOf(2, 3, 5, 7, 10, 2047, 2049, 1_000_003)) {
            val limit = (1L shl 32) / bound * bound

            val last = Replay(limit - 1)
            assertEquals(bound - 1, PassphraseGenerator.uniformIndex(bound, last.source()), "bound $bound")
            assertEquals(1, last.draws)

            // At the limit and above (up to 2^32 - 1) the draw is thrown away and the next one is used.
            for (rejected in listOf(limit, (1L shl 32) - 1)) {
                if (rejected < limit || rejected >= (1L shl 32)) continue
                val replay = Replay(rejected, 0)
                assertEquals(
                    0,
                    PassphraseGenerator.uniformIndex(bound, replay.source()),
                    "bound $bound, draw $rejected",
                )
                assertEquals(2, replay.draws)
            }
        }
    }

    @Test
    fun aBoundThatDividesTwoToThe32NeverRejectsAnything() {
        for (bound in listOf(1, 2, 256, 2048)) {
            val replay = Replay(0xFFFFFFFFL)

            assertEquals(bound - 1, PassphraseGenerator.uniformIndex(bound, replay.source()))
            assertEquals(1, replay.draws)
        }
    }

    @Test
    fun aBoundBelowOneAndAShortRandomSourceAreRefused() {
        assertFailsWith<IllegalArgumentException> { PassphraseGenerator.uniformIndex(0) { ByteArray(4) } }
        assertFailsWith<IllegalArgumentException> { PassphraseGenerator.uniformIndex(-5) { ByteArray(4) } }
        assertFailsWith<IllegalArgumentException> { PassphraseGenerator.uniformIndex(3) { ByteArray(2) } }
    }

    @Test
    fun theDrawnBytesAreWiped() {
        val drawn = ByteArray(4) { 0x11 }

        PassphraseGenerator.uniformIndex(2048) { drawn }

        assertTrue(drawn.all { it == 0.toByte() })
    }
}
