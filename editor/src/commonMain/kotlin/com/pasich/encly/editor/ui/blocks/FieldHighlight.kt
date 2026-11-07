package com.pasich.encly.editor.ui.blocks

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.pasich.encly.editor.focus.CaretGeometry
import com.pasich.encly.editor.focus.geometry
import com.pasich.encly.editor.ui.FindHighlights

private const val MATCH_ALPHA = 0.4f
private const val CURRENT_MATCH_ALPHA = 0.65f

/**
 * Tints the Find matches of one text field behind its text. Pass [onTextLayout] to the field and
 * add [modifier] to it; both are inert while Find has no match in this field.
 */
internal class FieldHighlight(
    private val highlights: FindHighlights,
    private val blockId: String,
    private val itemId: String?,
    private val match: Color,
    private val currentMatch: Color,
) {
    private var layout by mutableStateOf<(() -> TextLayoutResult?)?>(null)

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

    val modifier: Modifier = Modifier.bringIntoViewRequester(bringIntoView).drawBehind {
        val matches = highlights.matchesOf(blockId, itemId)
        if (matches.isEmpty()) return@drawBehind
        val result = layout?.invoke() ?: return@drawBehind
        val length = result.layoutInput.text.length
        val current = highlights.current
        for (found in matches) {
            if (found.start >= length) continue
            val path = result.getPathForRange(found.start, minOf(found.end, length))
            drawPath(path, if (found == current) currentMatch else match)
        }
    }
}

@Composable
internal fun rememberFieldHighlight(
    highlights: FindHighlights,
    blockId: String,
    itemId: String? = null,
): FieldHighlight {
    val primary = MaterialTheme.colorScheme.primary
    val field = remember(highlights, blockId, itemId, primary) {
        FieldHighlight(
            highlights = highlights,
            blockId = blockId,
            itemId = itemId,
            match = primary.copy(alpha = MATCH_ALPHA),
            currentMatch = primary.copy(alpha = CURRENT_MATCH_ALPHA),
        )
    }
    DisposableEffect(field) {
        highlights.register(blockId, itemId, field)
        onDispose { highlights.unregister(blockId, itemId, field) }
    }
    return field
}

/** The rectangle holding [from] and [to]; for a wrapped or right-to-left match, [to] is left of [from]. */
internal fun spanning(from: Rect, to: Rect): Rect =
    Rect(minOf(from.left, to.left), minOf(from.top, to.top), maxOf(from.right, to.right), maxOf(from.bottom, to.bottom))
