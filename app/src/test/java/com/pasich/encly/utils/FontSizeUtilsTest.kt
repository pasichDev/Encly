package com.pasich.encly.utils

import androidx.compose.ui.unit.sp
import com.pasich.encly.dynamicBlocks.BlockType
import org.junit.Assert.assertEquals
import org.junit.Test

class FontSizeUtilsTest {

    @Test
    fun theBaseSizeIsKeptInsideItsRange() {
        assertEquals(FontSizeUtils.MIN_FONT_SIZE.sp, FontSizeUtils.getTextBlockFontSize(2))
        assertEquals(FontSizeUtils.MAX_FONT_SIZE.sp, FontSizeUtils.getListFontSize(99))
        assertEquals(BASE.sp, FontSizeUtils.getTextBlockFontSize(BASE))
    }

    @Test
    fun headingsStepDownFromH1ToH4() {
        val sizes = listOf(BlockType.H1, BlockType.H2, BlockType.H3, BlockType.H4, BlockType.TEXT)
            .map { FontSizeUtils.getHeaderFontSize(BASE, it).value }

        assertEquals(listOf(26f, 24f, 22f, 20f, 16f), sizes)
    }

    @Test
    fun quotesAndTitlesAreLargerThanText() {
        assertEquals((BASE + 2).sp, FontSizeUtils.getQuoteFontSize(BASE))
        assertEquals((BASE + 8).sp, FontSizeUtils.getNoteTitleFontSize(BASE))
        assertEquals((FontSizeUtils.MAX_FONT_SIZE + 8).sp, FontSizeUtils.getNoteTitleFontSize(100))
    }

    private companion object {
        const val BASE = 16
    }
}
