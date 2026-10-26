package com.pasich.encly.presentation.editor.blocks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

// Where a checklist box goes: on the middle of its item's real first line, read from the text layout.

/**
 * What it takes to find the middle of a first line: the height the font needs ([naturalHeight]; a
 * line box shorter than it is compact spacing) and half the height of a capital letter, both in
 * pixels, measured once per text style.
 */
internal class LineMetrics(val naturalHeight: Float, val halfCap: Float)

@Composable
internal fun rememberLineMetrics(): LineMetrics {
    val style = listTextStyle()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density) {
        val plain = measurer.measure("H", style.copy(lineHeight = TextUnit.Unspecified, lineHeightStyle = null))
        val sized = measurer.measure("H", style)
        LineMetrics(
            naturalHeight = plain.size.height.toFloat(),
            halfCap = capHeightPx(sized, with(density) { style.fontSize.toPx() }) / 2f,
        )
    }
}

/**
 * The middle of the first line of text, in the field's own pixels (from its top, [paddingPx] of
 * padding included). A line box as tall as the font needs holds the glyphs centred (the text style
 * centres them), so its middle is the answer; a compact one is shorter, the glyphs overflow it, and
 * the middle of the capitals, [LineMetrics.halfCap] above the baseline, is. Before the text is
 * measured: [fallbackLine], a line height's worth, halved.
 */
internal fun firstLineCentre(
    layout: TextLayoutResult?,
    paddingPx: Float,
    metrics: LineMetrics,
    fallbackLine: Float,
): Float {
    if (layout == null || layout.lineCount == 0) return paddingPx + fallbackLine / 2f
    val top = layout.getLineTop(0)
    val bottom = layout.getLineBottom(0)
    val centre = if (bottom - top >=
        metrics.naturalHeight
    ) {
        (top + bottom) / 2f
    } else {
        layout.getLineBaseline(0) - metrics.halfCap
    }
    return paddingPx + centre
}

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
