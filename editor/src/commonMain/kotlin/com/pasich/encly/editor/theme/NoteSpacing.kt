package com.pasich.encly.editor.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// What each reading density means in the editor. Presentation only: the stored note is the same
// whatever is chosen. DEFAULT returns the exact values the editor always used.

private const val COMFORTABLE_LINE_FACTOR = 1.25f
private const val DEFAULT_LINE_FACTOR = 1.5f
private val FRAME_INSET_DEFAULT = 4.dp
private val FRAME_INSET_COMFORTABLE = 6.dp
private val BLOCK_GAP_EXTRA_COMFORTABLE = 4.dp
private val LIST_ITEM_GAP_COMFORTABLE = 8.dp

/** How tightly the editor sets its lines and blocks; the host maps its own setting onto it. */
public enum class EditorLineSpacing { COMPACT, DEFAULT, COMFORTABLE }

/** Whether lines sit as close as the font allows (no leading, no gaps between blocks). */
public val EditorLineSpacing.isCompact: Boolean get() = this == EditorLineSpacing.COMPACT

/**
 * Line height of a body text: the font size itself when compact, the theme's [normal] height by
 * default, and a quarter more when comfortable. A text larger than the theme's body grows its line
 * with it (1.5× the size, the theme's own ratio at 16sp), so big text never overruns its line.
 */
internal fun EditorLineSpacing.lineHeight(fontSize: TextUnit, normal: TextUnit): TextUnit {
    val default = maxOf(normal.value, fontSize.value * DEFAULT_LINE_FACTOR).sp
    return when (this) {
        EditorLineSpacing.COMPACT -> fontSize
        EditorLineSpacing.DEFAULT -> default
        EditorLineSpacing.COMFORTABLE -> default * COMFORTABLE_LINE_FACTOR
    }
}

/** Vertical padding inside the frame of a paragraph, list or quote. */
internal val EditorLineSpacing.frameInset: Dp
    get() = when (this) {
        EditorLineSpacing.COMPACT -> 0.dp
        EditorLineSpacing.DEFAULT -> FRAME_INSET_DEFAULT
        EditorLineSpacing.COMFORTABLE -> FRAME_INSET_COMFORTABLE
    }

/** The gap between the editor's blocks, given the default gap [base]. */
public fun EditorLineSpacing.blockGap(base: Dp): Dp = when (this) {
    EditorLineSpacing.COMPACT -> 0.dp
    EditorLineSpacing.DEFAULT -> base
    EditorLineSpacing.COMFORTABLE -> base + BLOCK_GAP_EXTRA_COMFORTABLE
}

/** The gap between the rows of a list; a checklist's rows are tall enough already. */
internal fun EditorLineSpacing.listItemGap(checklist: Boolean, base: Dp): Dp = when {
    this == EditorLineSpacing.COMPACT || (checklist && this == EditorLineSpacing.DEFAULT) -> 0.dp
    this == EditorLineSpacing.COMFORTABLE -> LIST_ITEM_GAP_COMFORTABLE
    else -> base
}

// A list's marker follows the text: the checkbox and the gap after it grow with the font size, and
// bullets and numbers use the same column, so every kind of list lines its text up the same way.
private const val MARKER_BOX_PER_FONT = 1.1f
private const val MARKER_GAP_PER_FONT = 0.375f
private const val LIST_INDENT_PER_FONT = 0.75f
private val MARKER_BOX_MIN = 16.dp
private val MARKER_BOX_MAX = 28.dp
private val MARKER_GAP_MIN = 6.dp
private val MARKER_GAP_MAX = 14.dp
private val LIST_INDENT_MIN = 10.dp
private val LIST_INDENT_MAX = 18.dp

/** The checkbox side for list text of [font] size: 1.1 times it, from 16 to 28 dp. */
internal fun markerBoxSize(font: Dp): Dp = (font * MARKER_BOX_PER_FONT).coerceIn(MARKER_BOX_MIN, MARKER_BOX_MAX)

/** The gap between the marker and the text for list text of [font] size: 3/8 of it, from 6 to 14 dp. */
internal fun markerGap(font: Dp): Dp = (font * MARKER_GAP_PER_FONT).coerceIn(MARKER_GAP_MIN, MARKER_GAP_MAX)

/** The marker column of a list: the box and the gap after it. */
internal fun markerColumn(font: Dp): Dp = markerBoxSize(font) + markerGap(font)

/**
 * How far a whole list (its markers and its text together) sits in from the text column, for list text of [font]
 * size: three quarters of it, from 10 to 18 dp. A list then reads as under the heading or paragraph above it, as
 * a rendered Markdown list does, instead of its marker standing under that text with a wide gap before its own.
 */
internal fun listIndent(font: Dp): Dp = (font * LIST_INDENT_PER_FONT).coerceIn(LIST_INDENT_MIN, LIST_INDENT_MAX)
