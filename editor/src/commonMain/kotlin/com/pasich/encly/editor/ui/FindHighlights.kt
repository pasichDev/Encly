package com.pasich.encly.editor.ui

import androidx.compose.runtime.State
import com.pasich.encly.editor.state.FindMatch
import com.pasich.encly.editor.state.FindState
import com.pasich.encly.editor.state.fieldKey

/**
 * What the blocks tint for Find in this note. It reads the find state only while drawing, so a
 * new match redraws the fields that show it and recomposes nothing; the text itself is never
 * touched.
 */
public class FindHighlights(private val state: State<FindState>) {
    /** The matches in the field of block [blockId] (or of its list item [itemId]). */
    internal fun matchesOf(blockId: String, itemId: String?): List<FindMatch> =
        state.value.byField[fieldKey(blockId, itemId)].orEmpty()

    /** The match "n of m" refers to, if any. */
    internal val current: FindMatch? get() = state.value.currentMatch
}
