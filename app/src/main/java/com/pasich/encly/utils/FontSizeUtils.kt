package com.pasich.encly.utils

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pasich.encly.dynamicBlocks.BlockType

/**
 * Utility for calculating font sizes for different block types.
 */
object FontSizeUtils {
    
    /**
     * Base font size (from 10 to 32).
     */
    const val MIN_FONT_SIZE = 10
    const val MAX_FONT_SIZE = 32
    
    /**
     * Returns the font size for text blocks.
     */
    fun getTextBlockFontSize(baseFontSize: Int): TextUnit {
        return baseFontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE).sp
    }
    
    /**
     * Returns the font size for headers, taking their type into account.
     */
    fun getHeaderFontSize(baseFontSize: Int, headerType: BlockType): TextUnit {
        val baseSize = baseFontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        
        return when (headerType) {
            BlockType.H1 -> (baseSize + 10).sp // +12sp for H1
            BlockType.H2 -> (baseSize + 8).sp  // +8sp for H2
            BlockType.H3 -> (baseSize + 6).sp  // +6sp for H3
            BlockType.H4 -> (baseSize + 4).sp  // +4sp for H4
            else -> baseSize.sp
        }
    }
    
    /**
     * Returns the font size for quotes.
     */
    fun getQuoteFontSize(baseFontSize: Int): TextUnit {
        val baseSize = baseFontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        return (baseSize + 2).sp // +2sp for quotes
    }
    
    /**
     * Returns the font size for lists.
     */
    fun getListFontSize(baseFontSize: Int): TextUnit {
        return baseFontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE).sp
    }
    
    /**
     * Returns the font size for the note title.
     */
    fun getNoteTitleFontSize(baseFontSize: Int): TextUnit {
        val baseSize = baseFontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        return (baseSize + 8).sp // +8sp for the note title
    }
}
