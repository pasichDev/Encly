package com.pasich.encly.presentation.editor.blocks

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult

/** One struck-through line: a horizontal rule from [left] to [right] at height [y], in the text's pixels. */
internal class StrikeSegment(val left: Float, val right: Float, val y: Float)

/**
 * The rules that strike [layout] through, one per non-empty line, a [heightRatio] of the font size
 * above each baseline. Drawn by the field instead of a `TextDecoration`: the keyboard's composing
 * word is drawn with an underline decoration that would replace a strike-through in the style, so a
 * checked item lost its strike while the cursor sat at the end of its last word.
 */
internal fun strikeSegments(
    layout: TextLayoutResult,
    fontSizePx: Float,
    heightRatio: Float = STRIKE_HEIGHT,
): List<StrikeSegment> = (0 until layout.lineCount).mapNotNull { line ->
    val left = layout.getLineLeft(line)
    val right = layout.getHorizontalPosition(layout.getLineEnd(line, visibleEnd = true), usePrimaryDirection = true)
    StrikeSegment(minOf(left, right), maxOf(left, right), layout.getLineBaseline(line) - fontSizePx * heightRatio)
        .takeIf { it.right > it.left }
}

/** Where a strike sits above the baseline, as a share of the font size (about the middle of the lower-case letters). */
private const val STRIKE_HEIGHT = 0.3f

/** Strikes the text of [field] through in [color] while it is [checked]. */
internal fun Modifier.strikeThrough(
    field: FieldHighlight,
    checked: Boolean,
    color: Color,
    fontSizePx: Float,
): Modifier = if (!checked) {
    this
} else {
    drawWithContent {
        drawContent()
        val layout = field.layoutResult() ?: return@drawWithContent
        val stroke = maxOf(1f, fontSizePx / STROKE_DIVISOR)
        strikeSegments(layout, fontSizePx).forEach {
            drawLine(color, Offset(it.left, it.y), Offset(it.right, it.y), stroke)
        }
    }
}

private const val STROKE_DIVISOR = 14f
