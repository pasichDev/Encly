package com.pasich.encly.presentation.screen.editnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.ui.theme.fontSet
import com.pasich.encly.utils.FontSizeUtils

/**
 * Default values for fallback scenarios
 */
private object EditNoteDefaults {
    val DEFAULT_FONT_SIZE = 16.sp
    val DEFAULT_FONT_STYLE = FontStyleType.DEFAULT
    const val DEFAULT_SIMPLE_EDIT = false

    val DEFAULT_FONT_FAMILIES = DEFAULT_FONT_STYLE.fontFamilies()
}

/**
 * CompositionLocal for the base font size
 */
val LocalBaseFontSize = compositionLocalOf { EditNoteDefaults.DEFAULT_FONT_SIZE }

/**
 * CompositionLocal for the font style
 */
val LocalFontStyle = compositionLocalOf { EditNoteDefaults.DEFAULT_FONT_STYLE }

/**
 * CompositionLocal for the simple editing mode
 */
val LocalSimpleEdit = compositionLocalOf { EditNoteDefaults.DEFAULT_SIMPLE_EDIT }

/**
 * CompositionLocal for precomputed font styles (optimization)
 */
val LocalFontStyles = compositionLocalOf<FontStyles?> { null }

/**
 * Simplified provider for font settings with error handling
 */
@Composable
fun EditNoteSettingsProvider(
    baseFontSize: TextUnit = EditNoteDefaults.DEFAULT_FONT_SIZE,
    fontStyle: FontStyleType = EditNoteDefaults.DEFAULT_FONT_STYLE,
    simpleEdit: Boolean = EditNoteDefaults.DEFAULT_SIMPLE_EDIT,
    content: @Composable () -> Unit,
) {
    // Pre-compute font styles for better performance
    val fontStyles = rememberFontStylesOptimized(
        baseFontSize = baseFontSize,
        fontStyle = fontStyle,
    )

    CompositionLocalProvider(
        LocalBaseFontSize provides baseFontSize,
        LocalFontStyle provides fontStyle,
        LocalSimpleEdit provides simpleEdit,
        LocalFontStyles provides fontStyles,
        content = content,
    )
}

/**
 * Optimized combined font styles provider with resource management
 */
@Composable
fun rememberFontStyles(): FontStyles {
    // Try to get pre-computed styles first
    LocalFontStyles.current?.let { return it }

    // Fallback to computing styles
    return rememberFontStylesOptimized()
}

/**
 * Internal optimized font styles computation
 */
@Composable
private fun rememberFontStylesOptimized(
    baseFontSize: TextUnit = LocalBaseFontSize.current,
    fontStyle: FontStyleType = LocalFontStyle.current,
): FontStyles = remember(baseFontSize, fontStyle) {
    try {
        val fontSizes = computeFontSizes(baseFontSize)
        val fontFamilies = computeFontFamilies(fontStyle)

        FontStyles(
            sizes = fontSizes,
            families = fontFamilies,
        )
    } catch (_: Exception) {
        createDefaultFontStyles()
    }
}

/**
 * Safe font sizes computation without Compose context
 */
private fun computeFontSizes(baseFontSize: TextUnit): FontSizes = try {
    val baseSize = baseFontSize.value.toInt().coerceIn(
        FontSizeUtils.MIN_FONT_SIZE,
        FontSizeUtils.MAX_FONT_SIZE,
    )

    FontSizes(
        textBlock = FontSizeUtils.getTextBlockFontSize(baseSize),
        h1 = FontSizeUtils.getHeaderFontSize(baseSize, BlockType.H1),
        h2 = FontSizeUtils.getHeaderFontSize(baseSize, BlockType.H2),
        h3 = FontSizeUtils.getHeaderFontSize(baseSize, BlockType.H3),
        h4 = FontSizeUtils.getHeaderFontSize(baseSize, BlockType.H4),
        quote = FontSizeUtils.getQuoteFontSize(baseSize),
        list = FontSizeUtils.getListFontSize(baseSize),
        noteTitle = FontSizeUtils.getNoteTitleFontSize(baseSize),
    )
} catch (_: Exception) {
    createDefaultFontSizes()
}

/**
 * Safe font families computation without Compose context
 */
private fun computeFontFamilies(fontStyle: FontStyleType): FontFamilies = try {
    fontStyle.fontFamilies()
} catch (_: Exception) {
    EditNoteDefaults.DEFAULT_FONT_FAMILIES
}

/** The editor uses the app-wide font set (Settings → Appearance → Font). */
private fun FontStyleType.fontFamilies(): FontFamilies = fontSet().let {
    FontFamilies(heading = it.display, body = it.body)
}

/**
 * Creates default font sizes for error scenarios
 */
private fun createDefaultFontSizes(): FontSizes {
    val defaultSize = EditNoteDefaults.DEFAULT_FONT_SIZE
    return FontSizes(
        textBlock = defaultSize,
        h1 = 28.sp,
        h2 = 24.sp,
        h3 = 22.sp,
        h4 = 20.sp,
        quote = 18.sp,
        list = defaultSize,
        noteTitle = 24.sp,
    )
}

/**
 * Creates default font styles for error scenarios
 */
private fun createDefaultFontStyles(): FontStyles = FontStyles(
    sizes = createDefaultFontSizes(),
    families = EditNoteDefaults.DEFAULT_FONT_FAMILIES,
)

/**
 * Class holding the font families
 */
data class FontFamilies(val heading: FontFamily, val body: FontFamily)

/**
 * Class holding font sizes for all block types
 */
data class FontSizes(
    val textBlock: TextUnit,
    val h1: TextUnit,
    val h2: TextUnit,
    val h3: TextUnit,
    val h4: TextUnit,
    val quote: TextUnit,
    val list: TextUnit,
    val noteTitle: TextUnit,
)

/**
 * Combined class for font styles
 */
data class FontStyles(val sizes: FontSizes, val families: FontFamilies)
