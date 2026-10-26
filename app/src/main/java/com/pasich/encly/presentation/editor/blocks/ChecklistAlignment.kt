package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

// Where a checklist box goes: on the middle of its item's first line of capitals, measured from the
// text itself and not from a line box (compact spacing makes that shorter than the font).

/**
 * Where a checklist item's first baseline is, counted from the top of the field with its padding
 * ([padding] above the text): the measured text's own, else the field's FirstBaseline line.
 */
@Composable
internal fun RowScope.checklistBaseline(highlight: FieldHighlight, padding: Dp): Modifier {
    val paddingPx = with(LocalDensity.current) { padding.toPx() }
    return Modifier.alignBy { measured ->
        val own = highlight.firstBaseline()?.let { (paddingPx + it).roundToInt() }
        own ?: measured[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: 0
    }
}

/** Half the height of a capital letter in the list's text style, in pixels. */
@Composable
internal fun rememberHalfCapHeight(): Float {
    val style = listTextStyle()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density) {
        capHeightPx(measurer.measure("H", style), with(density) { style.fontSize.toPx() }) / 2f
    }
}

/** The height of the capital in [layout] (an "H"), or a typical share of [fontSizePx] where no outline exists. */
internal fun capHeightPx(layout: TextLayoutResult, fontSizePx: Float): Float {
    val bounds = layout.getPathForRange(0, 1).getBounds()
    val cap = layout.getLineBaseline(0) - bounds.top
    return if (bounds.isEmpty || cap <= 0f) fontSizePx * CAP_HEIGHT_FALLBACK else cap
}

private const val CAP_HEIGHT_FALLBACK = 0.7f
