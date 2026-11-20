package com.pasich.encly.editor.ui.blocks

import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The first-strong-character rule that sets the direction of a paragraph and of what sits around it. */
class ReadingDirectionTest {
    private val rtl = LayoutDirection.Rtl
    private val ltr = LayoutDirection.Ltr

    @Test
    fun latinTextReadsLeftToRight() {
        assertEquals(ltr, firstStrongDirection("hello"))
        assertEquals(ltr, firstStrongDirection("Привіт"))
        assertEquals(ltr, firstStrongDirection("你好"))
    }

    @Test
    fun letterOfARightToLeftScriptReadsRightToLeft() {
        assertEquals(rtl, firstStrongDirection("مرحبا"))
        assertEquals(rtl, firstStrongDirection("שלום"))
        assertEquals(rtl, firstStrongDirection("ܫܠܡܐ"))
        assertEquals(rtl, firstStrongDirection("ދ\u07A8ވ\u07ACހ\u07A8"))
        assertEquals(rtl, firstStrongDirection("ߊߟߏ"))
    }

    @Test
    fun presentationFormsOfArabicAndHebrewReadRightToLeft() {
        assertEquals(rtl, firstStrongDirection("\uFB2A"))
        assertEquals(rtl, firstStrongDirection("\uFDF2"))
        assertEquals(rtl, firstStrongDirection("\uFE8D"))
    }

    @Test
    fun theFirstStrongCharacterWinsInMixedText() {
        assertEquals(ltr, firstStrongDirection("hello مرحبا"))
        assertEquals(rtl, firstStrongDirection("مرحبا hello"))
    }

    @Test
    fun digitsPunctuationSpacesAndEmojiAreSkipped() {
        assertEquals(rtl, firstStrongDirection("  12. - (مرحبا"))
        assertEquals(ltr, firstStrongDirection("12. \uD83D\uDE00 hello"))
        // Arabic-Indic digits are numbers, not letters of the script.
        assertEquals(ltr, firstStrongDirection("\u0661\u0662 hello"))
        // Vowel marks are not letters either.
        assertEquals(rtl, firstStrongDirection("\u064Eب"))
    }

    @Test
    fun aTextWithoutAStrongCharacterHasNoDirection() {
        assertNull(firstStrongDirection(""))
        assertNull(firstStrongDirection("   "))
        assertNull(firstStrongDirection("123 -- ?!"))
        assertNull(firstStrongDirection("\uD83D\uDE00"))
    }

    @Test
    fun directionalMarksDecideBeforeLettersAfterThem() {
        assertEquals(rtl, firstStrongDirection("\u200Fhello"))
        assertEquals(ltr, firstStrongDirection("\u200Eمرحبا"))
        assertEquals(rtl, firstStrongDirection("\u061C123"))
    }
}
