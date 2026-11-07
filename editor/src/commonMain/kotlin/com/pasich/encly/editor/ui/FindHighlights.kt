package com.pasich.encly.editor.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pasich.encly.editor.state.FindMatch
import com.pasich.encly.editor.state.FindState
import com.pasich.encly.editor.state.TextPoint
import com.pasich.encly.editor.state.TextRangeSelection
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

    /** The text selection across blocks that the fields tint; set by [editorBlocks] for the editor it shows. */
    internal var textRange: TextRangeSelection? = null

    /** Where the cursor of the field that has focus stands, when it selects nothing; Shift+click extends from it. */
    internal var caret: TextPoint? = null

    /** A drag that extends the selection: where the pointer is, and what it does with a new pointer position. */
    internal val drag = RangeDrag()

    /**
     * The place in the text at [root], a position in the window: in the field whose lines are nearest to it
     * vertically, at the character the position points at. Null when no field has been measured.
     */
    internal fun pointAt(root: Offset): TextPoint? {
        val measured = fields.values.mapNotNull { field -> field.rootBounds()?.let { field to it } }
        val nearest = measured.minByOrNull { (_, bounds) -> bounds.verticalDistance(root.y) } ?: return null
        return nearest.first.pointAt(root - nearest.second.topLeft)
    }

    /** Where, in the window, the cursor of [point] is drawn; null while its field is not on screen. */
    internal fun cursorRoot(point: TextPoint): Offset? =
        fields[fieldKey(point.blockId, point.itemId)]?.cursorRoot(point.offset)

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

/** How far [y] is above or below these bounds; 0 inside them. */
private fun Rect.verticalDistance(y: Float): Float = maxOf(top - y, y - bottom, 0f)

/** The drag that extends a text selection: the pointer's window position (null when none) and what it moves. */
internal class RangeDrag {
    /** The pointer's position in the window while it drags; null otherwise. */
    var pointer by mutableStateOf<Offset?>(null)

    /** Applies a new pointer position to the selection; set for the drag that is going on. */
    var update: (Offset) -> Unit = {}
}
