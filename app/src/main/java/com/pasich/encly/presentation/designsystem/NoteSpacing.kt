package com.pasich.encly.presentation.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pasich.encly.domain.model.NoteLineSpacing

// What each reading density means in the editor. Presentation only: the stored note is the same
// whatever is chosen. DEFAULT returns the exact values the editor always used.

private const val COMFORTABLE_LINE_FACTOR = 1.25f
private const val DEFAULT_LINE_FACTOR = 1.5f
private val FRAME_INSET_DEFAULT = 4.dp
private val FRAME_INSET_COMFORTABLE = 6.dp
private val BLOCK_GAP_EXTRA_COMFORTABLE = 4.dp
private val LIST_ITEM_GAP_COMFORTABLE = 8.dp

/** Whether lines sit as close as the font allows (no leading, no gaps between blocks). */
internal val NoteLineSpacing.isCompact: Boolean get() = this == NoteLineSpacing.COMPACT

/**
 * Line height of a body text: the font size itself when compact, the theme's [normal] height by
 * default, and a quarter more when comfortable. A text larger than the theme's body grows its line
 * with it (1.5× the size, the theme's own ratio at 16sp), so big text never overruns its line.
 */
internal fun NoteLineSpacing.lineHeight(fontSize: TextUnit, normal: TextUnit): TextUnit {
    val default = maxOf(normal.value, fontSize.value * DEFAULT_LINE_FACTOR).sp
    return when (this) {
        NoteLineSpacing.COMPACT -> fontSize
        NoteLineSpacing.DEFAULT -> default
        NoteLineSpacing.COMFORTABLE -> default * COMFORTABLE_LINE_FACTOR
    }
}

/** Vertical padding inside the frame of a paragraph, list or quote. */
internal val NoteLineSpacing.frameInset: Dp
    get() = when (this) {
        NoteLineSpacing.COMPACT -> 0.dp
        NoteLineSpacing.DEFAULT -> FRAME_INSET_DEFAULT
        NoteLineSpacing.COMFORTABLE -> FRAME_INSET_COMFORTABLE
    }

/** The gap between the editor's blocks, given the default gap [base]. */
internal fun NoteLineSpacing.blockGap(base: Dp): Dp = when (this) {
    NoteLineSpacing.COMPACT -> 0.dp
    NoteLineSpacing.DEFAULT -> base
    NoteLineSpacing.COMFORTABLE -> base + BLOCK_GAP_EXTRA_COMFORTABLE
}

/** The gap between the rows of a list; a checklist's rows are tall enough already. */
internal fun NoteLineSpacing.listItemGap(checklist: Boolean, base: Dp): Dp = when {
    this == NoteLineSpacing.COMPACT || (checklist && this == NoteLineSpacing.DEFAULT) -> 0.dp
    this == NoteLineSpacing.COMFORTABLE -> LIST_ITEM_GAP_COMFORTABLE
    else -> base
}

// A list's marker follows the text: the checkbox and the gap after it grow with the font size, and
// bullets and numbers use the same column, so every kind of list lines its text up the same way.
private const val MARKER_BOX_PER_FONT = 1.1f
private const val MARKER_GAP_PER_FONT = 0.5f
private val MARKER_BOX_MIN = 16.dp
private val MARKER_BOX_MAX = 28.dp
private val MARKER_GAP_MIN = 6.dp
private val MARKER_GAP_MAX = 14.dp

/** The checkbox side for list text of [font] size: 1.1 times it, from 16 to 28 dp. */
internal fun markerBoxSize(font: Dp): Dp = (font * MARKER_BOX_PER_FONT).coerceIn(MARKER_BOX_MIN, MARKER_BOX_MAX)

/** The gap between the marker and the text for list text of [font] size: half of it, from 6 to 14 dp. */
internal fun markerGap(font: Dp): Dp = (font * MARKER_GAP_PER_FONT).coerceIn(MARKER_GAP_MIN, MARKER_GAP_MAX)

/** The marker column of a list: the box and the gap after it. */
internal fun markerColumn(font: Dp): Dp = markerBoxSize(font) + markerGap(font)
