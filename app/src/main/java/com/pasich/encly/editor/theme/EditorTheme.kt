package com.pasich.encly.editor.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What the editor looks like beyond the host's Material theme (colors, shapes and the base type
 * scale come from `MaterialTheme`): sizes, the writing fonts, the line spacing, two text styles and
 * the icons. The host maps its design system onto it and provides it as [LocalEditorTheme]; an
 * editor without a provided theme uses the defaults, which are what the Encly app ships.
 */
@Immutable
data class EditorTheme(
    val spacing: EditorSpacing = EditorSpacing(),
    val fontSizes: EditorFontSizes = EditorFontSizes(),
    val fontFamilies: EditorFontFamilies = EditorFontFamilies(),
    val lineSpacing: EditorLineSpacing = EditorLineSpacing.DEFAULT,
    /** The text style of a quote block. */
    val quoteStyle: TextStyle = TextStyle(fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 27.sp),
    /** The small figures style (a numbered list's numbers). */
    val dataStyle: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    val icons: EditorIcons = EditorIcons(),
)

/** The editor's theme; see [EditorTheme]. */
val LocalEditorTheme = staticCompositionLocalOf { EditorTheme() }

/** Spacing and sizes on a 4 dp base. */
@Immutable
@Suppress("LongParameterList") // One named token each.
data class EditorSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val s: Dp = 12.dp,
    val m: Dp = 16.dp,
    val l: Dp = 24.dp,
    /** Gap between a title and its supporting line. */
    val textGap: Dp = 2.dp,
    /** Gap between a label and its field. */
    val labelGap: Dp = 6.dp,
    /** Gap between a leading icon and the text of a row. */
    val rowGap: Dp = 14.dp,
    /** Gap between stacked form fields. */
    val fieldGap: Dp = 18.dp,
    /** Hairlines. */
    val hairline: Dp = 1.dp,
    /** Focus rings and checkbox borders. */
    val stroke: Dp = 2.dp,
    val minTouchTarget: Dp = 48.dp,
    val buttonHeight: Dp = 52.dp,
    /** Minimum height of a sheet row. */
    val rowHeight: Dp = 56.dp,
    val icon: Dp = 24.dp,
    val iconMedium: Dp = 22.dp,
    val iconSmall: Dp = 20.dp,
    /** A link card's globe tile. */
    val tileSmall: Dp = 40.dp,
    /** Side padding of a block sheet. */
    val gutter: Dp = 24.dp,
    /** The empty area under the last block; a tap there adds a block. */
    val tapArea: Dp = 200.dp,
)

/** Font sizes of the block kinds, already scaled by the host's text size setting. */
@Immutable
data class EditorFontSizes(
    val textBlock: TextUnit = 16.sp,
    val h1: TextUnit = 28.sp,
    val h2: TextUnit = 24.sp,
    val h3: TextUnit = 22.sp,
    val h4: TextUnit = 20.sp,
    val quote: TextUnit = 18.sp,
    val list: TextUnit = 16.sp,
)

/** The fonts of headings and of body text. */
@Immutable
data class EditorFontFamilies(val heading: FontFamily = FontFamily.Default, val body: FontFamily = FontFamily.Default)

private val BlockBleed = 8.dp
private val BlockInset = 4.dp

/**
 * The inner padding of one editor block, 4/8. The editor column sits [BlockBleed] inside the
 * gutter, so text lines up with the title. No outline while editing: the caret is the focus mark.
 */
fun Modifier.editorBlockFrame(verticalInset: Dp?): Modifier =
    padding(horizontal = BlockBleed, vertical = verticalInset ?: BlockInset)

/** Room the host's toolbar takes above the keyboard; floating menus keep clear of it. */
val EditorToolbarReserve = 72.dp

/** How faint a placeholder is drawn. */
internal const val EDITOR_PLACEHOLDER_ALPHA = 0.38f
