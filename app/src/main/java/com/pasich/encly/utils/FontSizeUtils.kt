package com.pasich.encly.utils

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pasich.encly.dynamicBlocks.BlockType

/**
 * Утиліта для обчислення розмірів шрифтів для різних типів блоків
 */
object FontSizeUtils {
    
    /**
     * Базовий розмір шрифту (від 10 до 32)
     */
    const val MIN_FONT_SIZE = 10
    const val MAX_FONT_SIZE = 32
    
    /**
     * Отримує розмір шрифту для текстових блоків
     */
    fun getTextBlockFontSize(baseFontSize: Int): TextUnit {
        return baseFontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE).sp
    }
    
    /**
     * Отримує розмір шрифту для заголовків з урахуванням їх типу
     */
    fun getHeaderFontSize(baseFontSize: Int, headerType: BlockType): TextUnit {
        val baseSize = baseFontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        
        return when (headerType) {
            BlockType.H1 -> (baseSize + 10).sp // +12sp для H1
            BlockType.H2 -> (baseSize + 8).sp  // +8sp для H2
            BlockType.H3 -> (baseSize + 6).sp  // +6sp для H3
            BlockType.H4 -> (baseSize + 4).sp  // +4sp для H4
            else -> baseSize.sp
        }
    }
    
    /**
     * Отримує розмір шрифту для цитат
     */
    fun getQuoteFontSize(baseFontSize: Int): TextUnit {
        val baseSize = baseFontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        return (baseSize + 2).sp // +2sp для цитат
    }
    
    /**
     * Отримує розмір шрифту для списків
     */
    fun getListFontSize(baseFontSize: Int): TextUnit {
        return baseFontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE).sp
    }
    
    /**
     * Отримує розмір шрифту для заголовка нотатки
     */
    fun getNoteTitleFontSize(baseFontSize: Int): TextUnit {
        val baseSize = baseFontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        return (baseSize + 8).sp // +8sp для заголовка нотатки
    }
}
