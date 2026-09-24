package com.pasich.encly.presentation.editor.state

import com.pasich.encly.domain.model.ItemListBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditsTest {
    @Test
    fun theCursorPlacesALineBreakTypedNextToAnother() {
        // "a|\nb" and "a\n|b" both become "a\n\nb": only the cursor tells them apart.
        assertEquals(TextDiff(1, "", "\n"), textDiff("a\nb", "a\n\nb", cursor = 2))
        assertEquals(TextDiff(2, "", "\n"), textDiff("a\nb", "a\n\nb", cursor = 3))
    }

    @Test
    fun withoutAFittingCursorTheSmallestChangedRangeIsTaken() {
        assertEquals(TextDiff(1, "eh", "he"), textDiff("teh cat", "the cat"))
        assertEquals(TextDiff(3, "lo", ""), textDiff("hello", "hel", cursor = 3))
    }

    @Test
    fun aLineBreakIsReadFromTheEditAroundTheCursor() {
        val old = "Hello world"
        val lineBreak = LineBreak.of(old, textDiff(old, "Hello\n world", cursor = 6))!!

        assertEquals(LineBreak("Hello", listOf("", ""), " world"), lineBreak)
        assertTrue(lineBreak.isEnter)
    }

    @Test
    fun carriageReturnsCountAsLineBreaks() {
        val lineBreak = LineBreak.of("", textDiff("", "one\r\ntwo\rthree"))!!

        assertEquals(listOf("one", "two", "three"), lineBreak.lines)
        assertFalse(lineBreak.isEnter)
        assertNull(LineBreak.of("ab", textDiff("ab", "abc")))
    }

    @Test
    fun typingAWordIsOneStepAndTheNextWordStartsAnother() {
        val step = typingStep("f", "hello", "hello ")
        val nextWord = typingStep("f", "hello ", "hello w")

        assertTrue(typingStep("f", "hell", "hello").continues(typingStep("f", "hel", "hell")))
        assertTrue(step.continues(typingStep("f", "hell", "hello")))
        assertTrue(nextWord.startsWord)
        assertFalse(nextWord.continues(step))
    }

    @Test
    fun switchingBetweenTypingAndDeletingOrAutocorrectStartsAStep() {
        val typing = typingStep("f", "ab", "abc")
        val deleting = typingStep("f", "abc", "ab")
        val autocorrect = typingStep("f", "teh", "the")

        assertEquals(TypingKind.DELETE, deleting.kind)
        assertFalse(deleting.continues(typing))
        assertEquals(TypingKind.REPLACE, autocorrect.kind)
        assertFalse(autocorrect.continues(autocorrect))
        assertFalse(typingStep("g", "ab", "abc").continues(typing))
    }

    @Test
    fun onlyTypingInOneListItemIsATypingStep() {
        val milk = ItemListBlock("mil")
        val eggs = ItemListBlock("eggs")
        val old = listOf(milk, eggs)

        val step = listTypingStep(old, listOf(milk.copy(value = "milk"), eggs))

        assertEquals(milk.id, step?.field)
        assertNull(listTypingStep(old, listOf(milk.copy(isCheck = true), eggs)))
        assertNull(listTypingStep(old, listOf(milk)))
        assertNull(listTypingStep(old, listOf(milk.copy(value = "m"), eggs.copy(value = "e"))))
    }
}
