package com.pasich.encly.presentation.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.pasich.encly.domain.model.NoteLineSpacing

// What each reading density means in the editor. Presentation only: the stored note is the same
// whatever is chosen. DEFAULT returns the exact values the editor always used.

private const val COMFORTABLE_LINE_FACTOR = 1.25f
private val FRAME_INSET_DEFAULT = 4.dp
private val FRAME_INSET_COMFORTABLE = 6.dp
private val BLOCK_GAP_EXTRA_COMFORTABLE = 4.dp
private val LIST_ITEM_GAP_COMFORTABLE = 8.dp

/** Whether lines sit as close as the font allows (no leading, no gaps between blocks). */
internal val NoteLineSpacing.isCompact: Boolean get() = this == NoteLineSpacing.COMPACT

/**
 * Line height of a body text: the font size itself when compact, the theme's [normal] height by
 * default, and a quarter more when comfortable.
 */
internal fun NoteLineSpacing.lineHeight(fontSize: TextUnit, normal: TextUnit): TextUnit = when (this) {
    NoteLineSpacing.COMPACT -> fontSize
    NoteLineSpacing.DEFAULT -> normal
    NoteLineSpacing.COMFORTABLE -> normal * COMFORTABLE_LINE_FACTOR
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
