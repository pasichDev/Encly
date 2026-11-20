package com.pasich.encly.editor.focus

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection

/**
 * How a text field's lines are laid out, as far as moving the cursor between fields needs: which
 * visual line an offset is on, how far from the field's start it stands, and which offset a
 * horizontal position on a line points at. Abstract so the rules are testable without a layout.
 */
internal interface CaretGeometry {
    val lineCount: Int

    /** The length of the text this layout was measured for, which can lag a moment behind the field. */
    val textLength: Int
    fun lineOf(offset: Int): Int
    fun xOf(offset: Int): Float
    fun offsetAt(line: Int, x: Float): Int

    /** Whether the paragraph at [offset] runs right to left. */
    fun isRtl(offset: Int): Boolean
}

/** [this] layout as a [CaretGeometry]. */
internal fun TextLayoutResult.geometry(): CaretGeometry = object : CaretGeometry {
    override val lineCount: Int get() = this@geometry.lineCount
    override val textLength: Int get() = layoutInput.text.length
    override fun isRtl(offset: Int): Boolean =
        getParagraphDirection(offset.coerceIn(0, textLength)) == ResolvedTextDirection.Rtl
    override fun lineOf(offset: Int): Int = getLineForOffset(offset)
    override fun xOf(offset: Int): Float = getCursorRect(offset).left
    override fun offsetAt(line: Int, x: Float): Int {
        val middle = (getLineTop(line) + getLineBottom(line)) / 2f
        return getOffsetForPosition(Offset(x, middle))
    }
}

/**
 * Where the cursor stands among a field's visual lines: [onFirst] and [onLast] say whether Up or
 * Down would leave the field, `x` is its horizontal position (null when unknown).
 */
internal data class CaretLine(val onFirst: Boolean, val onLast: Boolean, val x: Float?, val rtl: Boolean = false)

/**
 * The [CaretLine] of the cursor at [cursor] in a text of [length] characters laid out as
 * [geometry]. Without a layout (not measured yet, or measured for other text) only the ends of the
 * text count as the first and last line, as they did before layouts were consulted.
 */
internal fun caretLine(geometry: CaretGeometry?, length: Int, cursor: Int): CaretLine {
    // A layout measured for other text than the field holds now is stale: its lines say nothing.
    if (geometry == null || geometry.lineCount <= 0 || geometry.textLength != length) {
        return CaretLine(cursor <= 0, cursor >= length, null)
    }
    val at = cursor.coerceIn(0, length)
    val line = geometry.lineOf(at)
    return CaretLine(line <= 0, line >= geometry.lineCount - 1, geometry.xOf(at), geometry.isRtl(at))
}

/**
 * The offset to put the cursor at when it arrives in a field at horizontal position `x`: on the
 * field's last line when it comes from below ([fromBelow]), else on its first. Without a layout, the
 * end or the start of the text.
 */
internal fun caretOffsetFor(geometry: CaretGeometry?, x: Float, fromBelow: Boolean, length: Int): Int {
    if (geometry == null || geometry.lineCount <= 0) return if (fromBelow) length else 0
    val line = if (fromBelow) geometry.lineCount - 1 else 0
    return geometry.offsetAt(line, x).coerceIn(0, length)
}
