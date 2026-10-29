package com.pasich.encly.editor.theme

import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteSpacingTest {

    private val normal = 24.sp

    @Test
    fun theDefaultSizeKeepsTheThemeLineHeight() {
        assertEquals(24f, EditorLineSpacing.DEFAULT.lineHeight(16.sp, normal).value, 0.01f)
        assertEquals(30f, EditorLineSpacing.COMFORTABLE.lineHeight(16.sp, normal).value, 0.01f)
        assertEquals(16f, EditorLineSpacing.COMPACT.lineHeight(16.sp, normal).value, 0.01f)
    }

    @Test
    fun smallTextKeepsTheThemeLineHeight() {
        assertEquals(24f, EditorLineSpacing.DEFAULT.lineHeight(12.sp, normal).value, 0.01f)
    }

    @Test
    fun largeTextGrowsItsLineWithIt() {
        assertEquals(48f, EditorLineSpacing.DEFAULT.lineHeight(32.sp, normal).value, 0.01f)
        assertEquals(60f, EditorLineSpacing.COMFORTABLE.lineHeight(32.sp, normal).value, 0.01f)
        assertEquals(32f, EditorLineSpacing.COMPACT.lineHeight(32.sp, normal).value, 0.01f)
    }

    @Test
    fun aLineIsNeverShorterThanItsTextExceptWhenCompact() {
        for (size in 10..32) {
            assertEquals(true, EditorLineSpacing.DEFAULT.lineHeight(size.sp, normal).value >= size * 1.5f - 0.01f)
        }
    }
}
