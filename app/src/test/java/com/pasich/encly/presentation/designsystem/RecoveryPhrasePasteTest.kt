package com.pasich.encly.presentation.designsystem

import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 12 recovery-word cells: phrases pasted the ways people copy them, and invalid words. */
class RecoveryPhrasePasteTest {
    private val state = RecoveryPhraseState()
    private val valid = String(MnemonicCode(WordCount.COUNT_12).chars).split(' ')

    @Test
    fun aNumberedListPastedFromNotesFillsTheCellsInOrder() {
        val numbered = valid.mapIndexed { i, word -> "${i + 1}. $word" }.joinToString("\n")

        state.onValueChange(0, numbered)

        assertEquals(valid, state.words)
        assertTrue(state.canSubmit)
    }

    @Test
    fun wordsSeparatedByTabsAndLineBreaksFillTheCells() {
        state.onValueChange(0, valid.joinToString("\t\r\n"))

        assertEquals(valid, state.words)
    }

    @Test
    fun moreThanTwelveWordsKeepTheFirstTwelve() {
        state.onValueChange(3, (valid + "abandon").joinToString(" "))

        assertEquals(valid, state.words)
    }

    @Test
    fun aPastedPhraseWithATypoFlagsThatCellOnly() {
        val typo = valid.toMutableList().apply { this[4] = "zzzz" }

        state.onValueChange(0, typo.joinToString(" "))

        assertEquals(4, state.firstInvalid)
        assertTrue(state.isInvalid(4))
        assertFalse(state.isInvalid(3))
        assertFalse(state.canSubmit)
    }

    @Test
    fun fixingTheFlaggedCellMakesThePhraseSubmittable() {
        val typo = valid.toMutableList().apply { this[4] = "zzzz" }
        state.onValueChange(0, typo.joinToString(" "))

        state.onValueChange(4, valid[4])

        assertNull(state.firstInvalid)
        assertTrue(state.canSubmit)
    }

    @Test
    fun aWholeWordInTheWrongPlaceFailsOnlyTheChecksum() {
        val swapped = valid.toMutableList().apply {
            val first = this[0]
            this[0] = this[1]
            this[1] = first
        }
        // A swap can keep a valid checksum by chance; only a failing one is checked here.
        state.onValueChange(0, swapped.joinToString(" "))

        assertNull(state.firstInvalid)
        assertTrue(state.isComplete)
        assertEquals(!state.canSubmit, state.checksumFailed)
    }

    @Test
    fun typingAWordLetterByLetterIsNeverFlaggedWhileItIsAPrefix() {
        val word = valid[0]

        (1..word.length).forEach { length ->
            assertNull(state.onValueChange(0, word.take(length)))
            assertFalse(state.isInvalid(0))
        }
        assertEquals(1, state.next(0))
        assertFalse(state.isInvalid(0))
    }

    @Test
    fun theLastCellStaysPutOnNextAndSpace() {
        assertEquals(11, state.next(11))
        assertEquals(11, state.onValueChange(11, "${valid[11]} "))
        assertEquals(valid[11], state.words[11])
    }

    @Test
    fun aDifferentCellCountIsHonoured() {
        val short = RecoveryPhraseState(count = 3)

        assertEquals(2, short.onValueChange(0, "orbit cactus velvet"))

        assertEquals(listOf("orbit", "cactus", "velvet"), short.words)
        assertEquals("orbit cactus velvet", String(short.toCharArray()))
    }
}
