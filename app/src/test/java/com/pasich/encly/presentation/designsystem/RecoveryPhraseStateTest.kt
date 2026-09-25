package com.pasich.encly.presentation.designsystem

import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPhraseStateTest {
    private val state = RecoveryPhraseState()
    private val valid = String(MnemonicCode(WordCount.COUNT_12).chars).split(' ')

    private fun typeAll(words: List<String>) = words.forEachIndexed { i, word -> state.onValueChange(i, word) }

    @Test
    fun typingKeepsLowerCaseLettersOnly() {
        assertNull(state.onValueChange(0, "Or-bit1"))

        assertEquals("orbit", state.words[0])
    }

    @Test
    fun aCellHoldsNoMoreThanTheLongestWord() {
        state.onValueChange(0, "abandonabandon")

        assertEquals("abandona", state.words[0])
    }

    @Test
    fun nextCommitsAndBackspaceInAnEmptyCellGoesBack() {
        state.onValueChange(0, "aban")

        assertEquals(1, state.next(0))
        assertTrue(state.isInvalid(0))
        assertEquals(0, state.backspaceTarget(1))
        assertNull(state.backspaceTarget(0))
    }

    @Test
    fun aSpaceCommitsTheWordAndMovesOn() {
        assertEquals(1, state.onValueChange(0, "orbit "))

        assertEquals("orbit", state.words[0])
        assertEquals("", state.words[1])
    }

    @Test
    fun aSpaceInAnEmptyCellStaysPut() {
        assertNull(state.onValueChange(3, " "))

        assertEquals("", state.words[3])
    }

    @Test
    fun aPasteFillsFromThisCellOn() {
        assertEquals(5, state.onValueChange(2, "orbit  cactus\nvelvet"))

        assertEquals(listOf("", "", "orbit", "cactus", "velvet", ""), state.words.take(6))
    }

    @Test
    fun aWholePhrasePastedAnywhereFillsFromTheFirstCell() {
        val target = state.onValueChange(7, " " + valid.joinToString("  ").uppercase() + " ")

        assertEquals(valid, state.words)
        assertEquals(11, target)
        assertTrue(state.isComplete)
        assertTrue(state.canSubmit)
    }

    @Test
    fun aPasteNeverRunsPastTheLastCell() {
        state.onValueChange(10, "orbit cactus velvet")

        assertEquals(listOf("orbit", "cactus"), state.words.takeLast(2))
    }

    @Test
    fun aPrefixOfNoWordIsFlaggedAtOnce() {
        state.onValueChange(0, "aban")
        assertFalse(state.isInvalid(0))

        state.onValueChange(0, "abx")
        assertTrue(state.isInvalid(0))
        assertEquals(0, state.firstInvalid)
    }

    @Test
    fun anUnfinishedWordIsFlaggedOnlyOnceLeft() {
        state.onValueChange(4, "aban")
        assertFalse(state.isInvalid(4))

        state.leave(4)
        assertTrue(state.isInvalid(4))

        state.onValueChange(4, "abandon")
        assertFalse(state.isInvalid(4))
    }

    @Test
    fun twelveKnownWordsWithABadChecksumCannotBeSubmitted() {
        typeAll(List(12) { "abandon" })

        assertTrue(state.isComplete)
        assertTrue(state.checksumFailed)
        assertFalse(state.canSubmit)
    }

    @Test
    fun elevenWordsAreNotComplete() {
        typeAll(valid.take(11))

        assertFalse(state.isComplete)
        assertFalse(state.checksumFailed)
        assertFalse(state.canSubmit)
    }

    @Test
    fun theCharArrayIsTheWordsWithSingleSpaces() {
        typeAll(valid)

        assertArrayEquals(valid.joinToString(" ").toCharArray(), state.toCharArray())
    }

    @Test
    fun clearEmptiesEveryCell() {
        typeAll(valid)
        state.leave(0)

        state.clear()

        assertTrue(state.words.all(String::isEmpty))
        assertNull(state.firstInvalid)
        assertFalse(state.isComplete)
    }
}
