package com.pasich.encly.editor.ui.blocks

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import com.pasich.encly.editor.focus.CaretGeometry
import com.pasich.encly.editor.focus.geometry
import com.pasich.encly.editor.state.TextPoint
import com.pasich.encly.editor.state.TextRangeSelection
import com.pasich.encly.editor.ui.FindHighlights

private const val MATCH_ALPHA = 0.4f
private const val CURRENT_MATCH_ALPHA = 0.65f

/**
 * What one text field shows and tells beyond its text: the Find matches and the part a text selection across
 * blocks covers, tinted behind the text; where it is in the window and what its lines are, so a pointer position
 * can be turned into a place in the text; and the mouse gestures that start such a selection. Pass [onTextLayout] to
 * the field and add [modifier] to it; the tint is inert while there is nothing to show in this field.
 */
@Suppress("TooManyFunctions") // What a field tells about itself, and the mouse gestures that start a selection.
internal class FieldHighlight(
    private val highlights: FindHighlights,
    val blockId: String,
    val itemId: String?,
    private val match: Color,
    private val currentMatch: Color,
    private val selected: Color,
) {
    private var layout by mutableStateOf<(() -> TextLayoutResult?)?>(null)

    private var coordinates: LayoutCoordinates? = null

    /** The field's own text state, once it is composed: a selection that ends inside one field is also the field's. */
    var state: TextFieldState? = null

    // Where a mouse drag began, in this field or as the anchor a Shift+click extends; null while there is none.
    private var mouseAnchor: TextPoint? = null

    /** The field's lines as the cursor moves between fields need them; null until it is measured. */
    fun geometry(): CaretGeometry? = layout?.invoke()?.geometry()

    /** The field's measured text; null until it has been measured. */
    fun layoutResult(): TextLayoutResult? = layout?.invoke()

    val onTextLayout: Density.(() -> TextLayoutResult?) -> Unit = { getResult ->
        if (layout == null) layout = getResult
    }

    private val bringIntoView = BringIntoViewRequester()

    /**
     * Scrolls the text from [start] to [end] into view; false while the field has no text layout (or no
     * text that long) to place it with.
     */
    suspend fun reveal(start: Int, end: Int): Boolean {
        val result = layout?.invoke()?.takeIf { start < it.layoutInput.text.length } ?: return false
        // From the first character's box to the last one's; a match that wraps spans both lines.
        val from = result.getBoundingBox(start)
        val to = result.getBoundingBox(minOf(end, result.layoutInput.text.length) - 1)
        bringIntoView.bringIntoView(spanning(from, to))
        return true
    }

    /** Where the field is in the window; null while it is not placed. */
    fun rootBounds(): Rect? = coordinates?.takeIf { it.isAttached }?.boundsInRoot()

    /** The place in the text that [local], a position in the field, points at; null before the text is measured. */
    fun pointAt(local: Offset): TextPoint? =
        layout?.invoke()?.let { TextPoint(blockId, it.getOffsetForPosition(local), itemId) }

    /** Where the cursor at [offset] stands in the window (the foot of the cursor); null before it is measured. */
    fun cursorRoot(offset: Int): Offset? {
        val result = layout?.invoke()
        return rootBounds()?.let { bounds ->
            result?.let {
                bounds.topLeft + it.getCursorRect(offset.coerceIn(0, it.layoutInput.text.length)).bottomCenter
            }
        }
    }

    /**
     * The field's own selection changed to [selection]: while no selection runs across fields it is the whole
     * selection, and a cursor alone ends it and is where Shift+click extends from.
     */
    fun onSelection(selection: TextRange, range: TextRangeSelection) {
        if (range.isCrossField) return
        if (selection.collapsed) {
            range.clear()
            highlights.caret = TextPoint(blockId, selection.start, itemId)
        } else {
            range.select(TextPoint(blockId, selection.start, itemId), TextPoint(blockId, selection.end, itemId))
        }
    }

    /** A mouse button went down at [local] in this field; with [shift] the press extends the selection instead. */
    fun beginMouse(local: Offset, shift: Boolean) {
        val range = highlights.textRange ?: return
        val here = pointAt(local)
        val kept = range.ends.value?.anchor ?: highlights.caret
        mouseAnchor = if (shift) kept else here
        if (shift) extend(range, mouseAnchor, here, always = false)
    }

    /** The mouse moved to [local] with a button down: past this field's edge the selection runs on into the others. */
    fun dragMouse(local: Offset) {
        val root = coordinates?.localToRoot(local) ?: return
        highlights.drag.update = ::dragTo
        highlights.drag.pointer = root
        dragTo(root)
    }

    private fun dragTo(root: Offset) {
        val range = highlights.textRange ?: return
        extend(range, mouseAnchor, highlights.pointAt(root), always = range.isCrossField)
    }

    // Selects from [anchor] to [point] when they are in different fields (or [always]): the selection has left a field.
    private fun extend(range: TextRangeSelection, anchor: TextPoint?, point: TextPoint?, always: Boolean) {
        if (anchor == null || point == null) return
        if (always || !anchor.sameField(point)) range.select(anchor, point)
    }

    /** The mouse button went up. */
    fun endMouse() {
        highlights.drag.pointer = null
        mouseAnchor = null
    }

    val modifier: Modifier = Modifier
        .bringIntoViewRequester(bringIntoView)
        .onGloballyPositioned { coordinates = it }
        .drawBehind {
            val result = layout?.invoke() ?: return@drawBehind
            val length = result.layoutInput.text.length
            highlights.textRange?.coverOf(blockId, itemId, length)?.let { (from, to) ->
                if (to > from) drawPath(result.getPathForRange(from, to), selected)
            }
            val current = highlights.current
            for (found in highlights.matchesOf(blockId, itemId)) {
                if (found.start >= length) continue
                val path = result.getPathForRange(found.start, minOf(found.end, length))
                drawPath(path, if (found == current) currentMatch else match)
            }
        }
        .rangeMouse(this)
}

@Composable
internal fun rememberFieldHighlight(
    highlights: FindHighlights,
    blockId: String,
    itemId: String? = null,
): FieldHighlight {
    val primary = MaterialTheme.colorScheme.primary
    val selected = LocalTextSelectionColors.current.backgroundColor
    val field = remember(highlights, blockId, itemId, primary, selected) {
        FieldHighlight(
            highlights = highlights,
            blockId = blockId,
            itemId = itemId,
            match = primary.copy(alpha = MATCH_ALPHA),
            currentMatch = primary.copy(alpha = CURRENT_MATCH_ALPHA),
            selected = selected,
        )
    }
    DisposableEffect(field) {
        highlights.register(blockId, itemId, field)
        onDispose { highlights.unregister(blockId, itemId, field) }
    }
    return field
}

/** Whether [this] and [other] are in the same field. */
internal fun TextPoint.sameField(other: TextPoint): Boolean = blockId == other.blockId && itemId == other.itemId

/** The rectangle holding [from] and [to]; for a wrapped or right-to-left match, [to] is left of [from]. */
internal fun spanning(from: Rect, to: Rect): Rect =
    Rect(minOf(from.left, to.left), minOf(from.top, to.top), maxOf(from.right, to.right), maxOf(from.bottom, to.bottom))
