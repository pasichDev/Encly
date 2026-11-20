package com.pasich.encly.editor.ui.blocks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

// Where a checklist box goes: on the middle of its item's real first line, read from the text layout.

/**
 * Where the middle of a list item's first line is, from the text STYLE alone (measured once on a
 * sample letter): the text style fixes the line height and centres the glyphs in it, so every
 * non-empty first line is laid out the same way and nothing per item (no layout that lags, no
 * state that changes with the text) can move a checklist box.
 *
 * [centre] is that middle, from the top of the text, in pixels: the middle of a line box as tall
 * as the font needs, or, where the line is shorter (compact spacing) and the glyphs overflow it,
 * the middle of the capitals. [emptyShift] is how far an EMPTY field's line sits above where a
 * typed line does (Compose lays an empty field out without the line height): it lowers the empty
 * line so a new item does not jump when its first character is typed. [lineHeight] is the style's
 * line height.
 */
internal class LineMetrics(val centre: Float, val emptyShift: Float, val lineHeight: Float)

@Composable
internal fun rememberLineMetrics(): LineMetrics {
    val style = listTextStyle()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density) {
        val natural = measurer.measure("H", style.copy(lineHeight = TextUnit.Unspecified, lineHeightStyle = null))
            .size.height.toFloat()
        val typed = measurer.measure("H", style)
        val empty = measurer.measure("", style)
        val fontPx = with(density) { style.fontSize.toPx() }
        val top = typed.getLineTop(0)
        val bottom = typed.getLineBottom(0)
        val centre = firstLineCentre(top, bottom, natural, typed.getLineBaseline(0), capHeightPx(typed, fontPx))
        val emptyCentre = (empty.getLineTop(0) + empty.getLineBottom(0)) / 2f
        val lineHeight = bottom - top
        LineMetrics(centre = centre, emptyShift = (centre - emptyCentre), lineHeight = lineHeight)
    }
}

/**
 * The middle of a first line whose box runs from [top] to [bottom]: the box's own middle when it is
 * at least as tall as the font's [natural] line, else (compact spacing, where the glyphs overflow
 * it) the middle of the capitals, [capHeight] above the [baseline].
 */
internal fun firstLineCentre(top: Float, bottom: Float, natural: Float, baseline: Float, capHeight: Float): Float =
    if (bottom - top >= natural) (top + bottom) / 2f else baseline - capHeight / 2f

/** The height of the capital in [layout] (an "H"), or a typical share of [fontSizePx] where no outline exists. */
internal fun capHeightPx(layout: TextLayoutResult, fontSizePx: Float): Float {
    val bounds = layout.getPathForRange(0, 1).getBounds()
    val cap = layout.getLineBaseline(0) - bounds.top
    return if (bounds.isEmpty || cap <= 0f) fontSizePx * CAP_HEIGHT_FALLBACK else cap
}

private const val CAP_HEIGHT_FALLBACK = 0.7f

/** The font size of the list's text style as a [Dp]. */
@Composable
internal fun listFontDp(): Dp = with(LocalDensity.current) { listTextStyle().fontSize.toDp() }
