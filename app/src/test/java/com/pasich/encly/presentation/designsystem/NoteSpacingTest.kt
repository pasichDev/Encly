package com.pasich.encly.presentation.designsystem

import androidx.compose.ui.unit.sp
import com.pasich.encly.domain.model.NoteLineSpacing
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSpacingTest {

    private val normal = 24.sp

    @Test
    fun theDefaultSizeKeepsTheThemeLineHeight() {
        assertEquals(24f, NoteLineSpacing.DEFAULT.lineHeight(16.sp, normal).value, 0.01f)
        assertEquals(30f, NoteLineSpacing.COMFORTABLE.lineHeight(16.sp, normal).value, 0.01f)
        assertEquals(16f, NoteLineSpacing.COMPACT.lineHeight(16.sp, normal).value, 0.01f)
    }

    @Test
    fun smallTextKeepsTheThemeLineHeight() {
        assertEquals(24f, NoteLineSpacing.DEFAULT.lineHeight(12.sp, normal).value, 0.01f)
    }

    @Test
    fun largeTextGrowsItsLineWithIt() {
        assertEquals(48f, NoteLineSpacing.DEFAULT.lineHeight(32.sp, normal).value, 0.01f)
        assertEquals(60f, NoteLineSpacing.COMFORTABLE.lineHeight(32.sp, normal).value, 0.01f)
        assertEquals(32f, NoteLineSpacing.COMPACT.lineHeight(32.sp, normal).value, 0.01f)
    }

    @Test
    fun aLineIsNeverShorterThanItsTextExceptWhenCompact() {
        for (size in 10..32) {
            assertEquals(true, NoteLineSpacing.DEFAULT.lineHeight(size.sp, normal).value >= size * 1.5f - 0.01f)
        }
    }
}
