package com.pasich.encly.presentation.editor

import androidx.compose.runtime.State
import com.pasich.encly.presentation.editor.state.FindMatch
import com.pasich.encly.presentation.editor.state.FindState
import com.pasich.encly.presentation.editor.state.fieldKey

/**
 * What the blocks tint for Find in this note. It reads the find state only while drawing, so a
 * new match redraws the fields that show it and recomposes nothing; the text itself is never
 * touched.
 */
class FindHighlights(private val state: State<FindState>) {
    /** The matches in the field of block [blockId] (or of its list item [itemId]). */
    fun matchesOf(blockId: String, itemId: String? = null): List<FindMatch> =
        state.value.byField[fieldKey(blockId, itemId)].orEmpty()

    /** The match "n of m" refers to, if any. */
    val current: FindMatch? get() = state.value.currentMatch
}
