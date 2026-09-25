package com.pasich.encly.presentation.screen.editnote

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pasich.encly.domain.enums.BottomSheetsOpenType
import com.pasich.encly.presentation.editor.focus.BlockFocusRegistry
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import kotlinx.coroutines.flow.collectLatest

private const val INITIAL_FOCUS_FRAMES = 5

/**
 * Flushes the editor as soon as it leaves the foreground: ProcessLifecycleOwner re-locks and
 * closes SQLCipher later in the background transition, so this closes the autosave window first.
 */
@Composable
internal fun SaveWhenPaused(viewModel: EditNoteViewModel = hiltViewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.saveForBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * The ViewModel says which block to focus; [registry], which holds the fields' FocusRequesters,
 * does it once that block is composed, scrolling a block off screen into view first
 * ([blocksStart]: the list items above the blocks). A newer request replaces an older one.
 */
@Composable
internal fun CarryOutFocusRequests(
    registry: BlockFocusRegistry,
    listState: LazyListState,
    blocksStart: Int,
    viewModel: EditNoteViewModel = hiltViewModel(),
) {
    val currentBlocksStart by rememberUpdatedState(blocksStart)
    LaunchedEffect(viewModel, registry) {
        viewModel.focusRequests.collectLatest { request ->
            registry.focusWhenComposed(request) { blockId ->
                val index = viewModel.blocks.indexOfFirst { it.id == blockId }
                if (index >= 0) listState.reveal(currentBlocksStart + index)
            }
        }
    }
}

/**
 * Scrolls item [index] into view. An item just below the screen (the paragraph Enter adds under
 * the last line, with the keyboard up) is scrolled up from the bottom by a little, as typing
 * would, instead of jumping it to the top; its field then keeps its cursor in view itself.
 */
private suspend fun LazyListState.reveal(index: Int) {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull()
    if (last != null && index > last.index && index - last.index <= NEAR_ITEMS) {
        val hidden = (last.offset + last.size - info.viewportEndOffset).coerceAtLeast(0)
        animateScrollBy((hidden + info.viewportSize.height / REVEAL_FRACTION).toFloat())
    } else if (info.visibleItemsInfo.none { it.index == index }) {
        animateScrollToItem(index)
    }
}

/** How far past the last item on screen an item counts as just below it. */
private const val NEAR_ITEMS = 2

/** An item just below the screen is revealed by this fraction of the screen's height. */
private const val REVEAL_FRACTION = 4

/** A new note starts in its title with the keyboard up: once, not again after a restore. */
@Composable
internal fun FocusNewNoteTitle(titleFocus: FocusRequester, viewModel: EditNoteViewModel = hiltViewModel()) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var done by rememberSaveable { mutableStateOf(!viewModel.isNewNote) }
    LaunchedEffect(done) {
        if (done) return@LaunchedEffect
        repeat(INITIAL_FOCUS_FRAMES) {
            withFrameNanos { }
            if (runCatching { titleFocus.requestFocus() }.isSuccess) {
                keyboardController?.show()
                done = true
                return@LaunchedEffect
            }
        }
        done = true
    }
}

/** Which block sheet is open, and for which block (by id). */
internal class BlockSheetState(type: MutableState<BottomSheetsOpenType>, blockId: MutableState<String?>) {
    var type by type
    var blockId by blockId

    fun open(sheet: BottomSheetsOpenType, id: String) {
        type = sheet
        blockId = id
    }

    fun close() {
        type = BottomSheetsOpenType.NONE
        blockId = null
    }
}

@Composable
internal fun rememberBlockSheetState(): BlockSheetState {
    val type = rememberSaveable { mutableStateOf(BottomSheetsOpenType.NONE) }
    val blockId = rememberSaveable { mutableStateOf<String?>(null) }
    return remember { BlockSheetState(type, blockId) }
}
