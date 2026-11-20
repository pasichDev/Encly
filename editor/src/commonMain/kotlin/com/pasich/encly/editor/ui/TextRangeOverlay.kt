package com.pasich.encly.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pasich.encly.editor.state.TextPoint
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** The test tag of the handle at the start of a text selection. */
internal const val RANGE_START_TAG = "text-range-start"

/** The test tag of the handle at the end of a text selection. */
internal const val RANGE_END_TAG = "text-range-end"

private val HandleSize = 22.dp
private val EdgeZone = 56.dp
private const val SCROLL_PER_FRAME = 24f

/**
 * What goes over the list of blocks while a text selection exists: a handle at each end of it, which a finger drags
 * into other blocks, and the list scrolling by itself while a drag (a handle's or the mouse's) is held near its top
 * or bottom edge. Place it in a box over [listState]'s list, filling the same area. It takes no touch except on its
 * handles.
 */
@Composable
public fun TextRangeOverlay(host: EditorBlocksHost, listState: LazyListState, modifier: Modifier = Modifier) {
    val highlights = host.highlights
    val range = host.controller.textRange
    val ends by range.ends.collectAsState()
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val zone = with(LocalDensity.current) { EdgeZone.toPx() }
    var handles by remember { mutableStateOf<Pair<Offset?, Offset?>>(null to null) }
    // The fields move when the list scrolls: the handles are placed again once the list has settled in its new place.
    LaunchedEffect(ends, listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collectLatest {
            withFrameNanos { }
            handles =
                range.orderedPoints()?.let { (from, to) -> highlights.cursorRoot(from) to highlights.cursorRoot(to) }
                    ?: (null to null)
        }
    }
    // A drag held in the top or bottom strip of the list scrolls it, and the selection follows the text under it.
    LaunchedEffect(highlights, listState) {
        snapshotFlow { highlights.drag.pointer }.collectLatest { pointer ->
            while (pointer != null) {
                val speed = autoScrollSpeed(pointer.y - origin.y, size.height.toFloat(), zone)
                if (speed != 0f) listState.scrollBy(speed)
                highlights.drag.update(pointer)
                withFrameNanos { }
            }
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInRoot()
                size = it.size
            },
    ) {
        RangeHandle(RANGE_START_TAG, handles.first, origin, isStart = true, host)
        RangeHandle(RANGE_END_TAG, handles.second, origin, isStart = false, host)
    }
}

/** How far to scroll per frame for a pointer at [y] in a list [height] tall: up in the top or bottom [zone]. */
internal fun autoScrollSpeed(y: Float, height: Float, zone: Float): Float = when {
    y < zone -> -SCROLL_PER_FRAME
    y > height - zone -> SCROLL_PER_FRAME
    else -> 0f
}

/** A handle at [position] (in the window) on one end of the selection; dragging it moves that end. */
@Composable
private fun RangeHandle(tag: String, position: Offset?, origin: Offset, isStart: Boolean, host: EditorBlocksHost) {
    if (position == null) return
    val range = host.controller.textRange
    val highlights = host.highlights
    val radius = with(LocalDensity.current) { HandleSize.toPx() / 2 }
    var handle by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        Modifier
            .offset { IntOffset((position.x - origin.x - radius).roundToInt(), (position.y - origin.y).roundToInt()) }
            .size(HandleSize)
            .testTag(tag)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .onGloballyPositioned { handle = it }
            .pointerInput(isStart) {
                var held: TextPoint? = null
                detectDragGestures(
                    onDragStart = {
                        // The other end stays where it is whichever way this one is dragged.
                        held = range.orderedPoints()?.let { if (isStart) it.second else it.first }
                        highlights.drag.update = { root -> dragEnd(host, held, root) }
                    },
                    onDragEnd = { highlights.drag.pointer = null },
                    onDragCancel = { highlights.drag.pointer = null },
                ) { change, _ ->
                    change.consume()
                    // Where the finger is in the window, wherever the handle has got to under it.
                    val pointer = handle?.localToRoot(change.position) ?: return@detectDragGestures
                    highlights.drag.pointer = pointer
                    highlights.drag.update(pointer)
                }
            },
    )
}

private fun dragEnd(host: EditorBlocksHost, held: TextPoint?, root: Offset) {
    val moved = host.highlights.pointAt(root)
    if (held != null && moved != null) host.controller.textRange.select(held, moved)
}
