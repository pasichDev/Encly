package com.pasich.encly.editor.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.withFrameNanos
import com.pasich.encly.editor.state.FindMatch
import com.pasich.encly.editor.state.FindState
import com.pasich.encly.editor.state.fieldKey
import com.pasich.encly.editor.ui.blocks.FieldHighlight

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

    // The fields on screen by field key; a field is here from its composition until it leaves it.
    private val fields = mutableMapOf<String, FieldHighlight>()

    internal fun register(blockId: String, itemId: String?, field: FieldHighlight) {
        fields[fieldKey(blockId, itemId)] = field
    }

    internal fun unregister(blockId: String, itemId: String?, field: FieldHighlight) {
        val key = fieldKey(blockId, itemId)
        if (fields[key] === field) fields.remove(key)
    }

    /**
     * Scrolls [match] itself into view, not only its block: a paragraph or a list can be taller than the
     * screen, and then part of it is on screen while the match is not. The match's field may still be
     * being composed (the list was just scrolled to its block), so this waits a few frames for it. False
     * when the field never appears or has no text layout to place the match with.
     */
    public suspend fun reveal(match: FindMatch): Boolean {
        repeat(REVEAL_FRAMES) {
            if (fields[match.field]?.reveal(match.start, match.end) == true) return true
            withFrameNanos { }
        }
        return false
    }
}

/** How many frames [FindHighlights.reveal] waits for the field of a match to be composed and measured. */
private const val REVEAL_FRAMES = 5
