package com.pasich.encly.presentation.screen.pincode

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The passphrase being typed: held only while the form is up, handed on as a CharArray, emptied when taken. */
class PassphraseInputTest {
    private val input = PassphraseInput()

    @Test
    fun nothingIsTypedAtFirst() {
        assertEquals("", input.text)
        assertFalse(input.canSubmit)
        assertFalse(input.revealed)
        assertFalse(input.suggested)
    }

    @Test
    fun somethingTypedCanBeSubmitted() {
        input.edit("a")

        assertTrue(input.canSubmit)
    }

    @Test
    fun aPasteLongerThanAnyPassphraseIsCut() {
        input.edit("x".repeat(PassphraseInput.MAX_TYPED_CHARS + 50))

        assertEquals(PassphraseInput.MAX_TYPED_CHARS, input.text.length)
    }

    @Test
    fun aCutNeverLeavesHalfOfAPair() {
        input.edit("x".repeat(PassphraseInput.MAX_TYPED_CHARS - 1) + "😀")

        assertEquals(PassphraseInput.MAX_TYPED_CHARS - 1, input.text.length)
    }

    @Test
    fun aHalfOfAPairWaitingForItsOtherHalfIsKept() {
        input.edit("abc\uD83D")
        assertEquals("abc\uD83D", input.text)

        input.edit("abc\uD83D\uDE00")

        assertEquals("abc\uD83D\uDE00", input.text)
    }

    @Test
    fun theCapIsTheOneTheFieldWasMadeWith() {
        val short = PassphraseInput(maxUnits = 10)

        short.edit("x".repeat(20))
        assertEquals(10, short.text.length)

        short.edit("x".repeat(9) + "\uD83D\uDE00")
        assertEquals(9, short.text.length)

        short.edit("y".repeat(10))
        assertEquals("y".repeat(10), short.text)
    }

    @Test
    fun peekIsACopyThatLeavesTheTextAlone() {
        input.edit("secret words")

        val copy = input.peek()
        copy.fill('x')

        assertEquals("secret words", input.text)
    }

    @Test
    fun takeHandsOverTheTextAndEmptiesTheField() {
        input.edit("secret words")
        input.revealed = true

        assertArrayEquals("secret words".toCharArray(), input.take())

        assertEquals("", input.text)
        assertFalse(input.revealed)
    }

    @Test
    fun aSuggestionIsShownInClearAndIsMarked() {
        input.fill("orbit cactus".toCharArray())

        assertEquals("orbit cactus", input.text)
        assertTrue(input.revealed)
        assertTrue(input.suggested)
    }

    @Test
    fun clearingForgetsTheTextTheMaskAndTheSuggestion() {
        input.fill("orbit cactus".toCharArray())

        input.clear()

        assertEquals("", input.text)
        assertFalse(input.revealed)
        assertFalse(input.suggested)
    }

    @Test
    fun submitEmptiesTheFieldWhenTheHandlerUsedTheText() {
        input.edit("secret words")
        var seen = ""

        input.submit { typed ->
            seen = String(typed)
            true
        }

        assertEquals("secret words", seen)
        assertEquals("", input.text)
    }

    @Test
    fun submitKeepsTheTextWhenTheHandlerRefusedIt() {
        input.edit("short")

        input.submit { false }

        assertEquals("short", input.text)
    }

    @Test
    fun submitOfNothingCallsNobody() {
        var called = false

        input.submit {
            called = true
            true
        }

        assertFalse(called)
    }
}
