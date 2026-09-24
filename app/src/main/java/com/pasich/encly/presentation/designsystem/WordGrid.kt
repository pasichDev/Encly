package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.EnclyTheme

/** What a hidden recovery word shows instead of the word. */
private const val HIDDEN_WORD = "••••••"

/**
 * The 12 recovery words, two columns filled row by row (1 2 / 3 4 / …). A secret is never cut:
 * when the longest word would not fit a half-width cell (a narrow screen, a large font scale),
 * the grid falls back to one column, and a word that still does not fit wraps instead of clipping.
 */
@Composable
fun WordGrid(words: List<String>, hidden: Boolean, modifier: Modifier = Modifier) {
    val style = EnclyTheme.typography.dataLarge
    // No layout cache: the measured strings are recovery words.
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val longestWord = remember(words, style, LocalDensity.current, LocalLayoutDirection.current) {
        words.maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
    }
    WordGridLayout(longestWordPx = longestWord, modifier = modifier) {
        words.forEachIndexed { index, word -> WordCell(number = index + 1, word = word, hidden = hidden) }
    }
}

/**
 * Lays out 12 word cells two columns wide, row by row, or one column when [longestWordPx] would
 * not fit a half-width cell. A plain Layout, not BoxWithConstraints, so dialogs that ask for
 * intrinsics can host it.
 */
@Composable
internal fun WordGridLayout(longestWordPx: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val gap = EnclyTheme.spacing.xs
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val chromePx = WordCellChrome.roundToPx()
        // Unbounded (an intrinsic query): as wide as two cells that fit the longest word.
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else (longestWordPx + chromePx) * 2 + gapPx
        val columns = wordGridColumns(width, gapPx, chromePx, longestWordPx)
        val cellWidth = ((width - gapPx * (columns - 1)) / columns).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(cellWidth)) }
        val rows = placeables.chunked(columns)
        val rowHeights = rows.map { row -> row.maxOf { it.height } }
        val height = rowHeights.sum() + gapPx * (rows.size - 1).coerceAtLeast(0)
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, cell -> cell.placeRelative(c * (cellWidth + gapPx), y) }
                y += rowHeights[r] + gapPx
            }
        }
    }
}

/**
 * Two columns when the widest word fits a half-width cell next to its number, else one.
 * [cellChromePx] is everything in a cell that is not the word: padding, the number and the gap.
 */
internal fun wordGridColumns(availableWidthPx: Int, gapPx: Int, cellChromePx: Int, longestWordPx: Int): Int {
    val wordRoom = (availableWidthPx - gapPx) / 2 - cellChromePx
    return if (longestWordPx <= wordRoom) 2 else 1
}

private val WordCellPadding = 14.dp
private val WordIndexWidth = 18.dp
private val WordIndexGap = 10.dp

/** The part of a word cell's width that is not the word: both paddings, the number and the gap. */
private val WordCellChrome = WordCellPadding * 2 + WordIndexWidth + WordIndexGap

@Composable
private fun WordCell(number: Int, word: String, hidden: Boolean) {
    WordCellFrame(number = number) {
        // No maxLines: at an extreme font scale a word wraps onto a second line, never clips.
        Text(
            text = if (hidden) HIDDEN_WORD else word,
            style = EnclyTheme.typography.dataLarge,
            color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The chrome every word cell shares, shown or typed: `surfaceContainer`, radius 12, min 48 dp,
 * the number right-aligned in an 18 dp column, then [content]. [border] outlines an input cell.
 */
@Composable
internal fun WordCellFrame(
    number: Int,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    numberColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = border,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WordIndexGap),
            modifier = Modifier
                .heightIn(min = EnclyTheme.spacing.minTouchTarget)
                .padding(horizontal = WordCellPadding),
        ) {
            Text(
                text = number.toString(),
                style = EnclyTheme.typography.dataIndex,
                color = numberColor,
                textAlign = TextAlign.End,
                modifier = Modifier.width(WordIndexWidth),
            )
            content()
        }
    }
}
