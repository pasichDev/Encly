package com.pasich.encly.presentation.editor.blocks

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.pasich.encly.presentation.editor.FindHighlights

private const val MATCH_ALPHA = 0.18f
private const val CURRENT_MATCH_ALPHA = 0.4f

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

    val onTextLayout: Density.(() -> TextLayoutResult?) -> Unit = { getResult ->
        if (layout == null) layout = getResult
    }

    val modifier: Modifier = Modifier.drawBehind {
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
    return remember(highlights, blockId, itemId, primary) {
        FieldHighlight(
            highlights = highlights,
            blockId = blockId,
            itemId = itemId,
            match = primary.copy(alpha = MATCH_ALPHA),
            currentMatch = primary.copy(alpha = CURRENT_MATCH_ALPHA),
        )
    }
}
