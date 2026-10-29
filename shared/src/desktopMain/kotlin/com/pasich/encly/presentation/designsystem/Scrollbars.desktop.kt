package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.foundation.ScrollbarAdapter as ClassicScrollbarAdapter

private const val THUMB_ALPHA = 0.6f
private const val THUMB_HOVER_ALPHA = 0.9f
private const val HOVER_FADE_MS = 250

/** Thumb colours from the theme: `onSurfaceVariant` at rest and brighter under the pointer (3:1 or better). */
@Composable
private fun enclyScrollbarStyle(): ScrollbarStyle {
    val thumb = MaterialTheme.colorScheme.onSurfaceVariant
    return ScrollbarStyle(
        minimalHeight = 24.dp,
        thickness = 8.dp,
        shape = RoundedCornerShape(4.dp),
        hoverDurationMillis = HOVER_FADE_MS,
        unhoverColor = thumb.copy(alpha = THUMB_ALPHA),
        hoverColor = thumb.copy(alpha = THUMB_HOVER_ALPHA),
    )
}

@Suppress("DEPRECATION") // The ready-made adapters of lists and columns are still the classic ones.
@Composable
private fun BoxScope.Scrollbar(adapter: ClassicScrollbarAdapter, visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    VerticalScrollbar(
        adapter = adapter,
        modifier = modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight(),
        style = enclyScrollbarStyle(),
    )
}

@Composable
private fun BoxScope.Scrollbar(adapter: ScrollbarAdapter, visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    VerticalScrollbar(
        adapter = adapter,
        modifier = modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight(),
        style = enclyScrollbarStyle(),
    )
}

@Composable
actual fun BoxScope.EnclyHorizontalScrollbar(state: ScrollState, modifier: Modifier) {
    if (state.maxValue <= 0) return
    HorizontalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
        style = enclyScrollbarStyle(),
    )
}

@Composable
actual fun BoxScope.EnclyScrollbar(state: LazyListState, modifier: Modifier) {
    Scrollbar(rememberScrollbarAdapter(state), state.canScrollForward || state.canScrollBackward, modifier)
}

@Composable
actual fun BoxScope.EnclyScrollbar(state: ScrollState, modifier: Modifier) {
    Scrollbar(rememberScrollbarAdapter(state), state.maxValue > 0, modifier)
}

@Composable
actual fun BoxScope.EnclyScrollbar(state: LazyStaggeredGridState, modifier: Modifier) {
    val adapter = remember(state) { StaggeredGridScrollbarAdapter(state) }
    Scrollbar(adapter, state.canScrollForward || state.canScrollBackward, modifier)
}

/**
 * A staggered grid does not know its length, so this estimates it: the rows are taken to be as tall as
 * the cells in view on average, and the grid as many rows long as its items fill at the lanes it has.
 */
internal class StaggeredGridScrollbarAdapter(private val state: LazyStaggeredGridState) : ScrollbarAdapter {
    private val info get() = state.layoutInfo
    private val visible get() = info.visibleItemsInfo

    private val lanes get() = max(1, (visible.maxOfOrNull { it.lane } ?: 0) + 1)

    /** The height of one row of cells with the gap below it, from the cells in view. */
    private val rowHeight: Double
        get() {
            if (visible.isEmpty()) return 1.0
            val average = visible.sumOf { it.size.height }.toDouble() / visible.size
            return max(1.0, average + info.mainAxisItemSpacing)
        }

    override val scrollOffset: Double
        get() = state.firstVisibleItemIndex.toDouble() / lanes * rowHeight + state.firstVisibleItemScrollOffset

    override val contentSize: Double
        get() = max(
            viewportSize,
            info.totalItemsCount.toDouble() / lanes * rowHeight + info.beforeContentPadding + info.afterContentPadding,
        )

    override val viewportSize: Double
        get() = info.viewportSize.height.toDouble()

    override suspend fun scrollTo(scrollOffset: Double) {
        val row = (scrollOffset / rowHeight).toInt()
        val within = (scrollOffset - row * rowHeight).roundToInt()
        state.scrollToItem(index = row * lanes, scrollOffset = within)
    }
}
