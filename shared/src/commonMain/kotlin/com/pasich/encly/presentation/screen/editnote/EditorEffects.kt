package com.pasich.encly.presentation.screen.editnote

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pasich.encly.editor.focus.BlockFocusRegistry
import com.pasich.encly.editor.ui.sheets.BlockSheetKind
import com.pasich.encly.presentation.pane.ActiveEditor
import com.pasich.encly.presentation.pane.EditorPane
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val INITIAL_FOCUS_FRAMES = 5

/**
 * Flushes the editor as soon as it leaves the foreground: ProcessLifecycleOwner re-locks and
 * closes SQLCipher later in the background transition, so this closes the autosave window first.
 */
@Composable
internal fun SaveWhenPaused(viewModel: EditNoteViewModel = metroViewModel()) {
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
    viewModel: EditNoteViewModel = metroViewModel(),
) {
    val currentBlocksStart by rememberUpdatedState(blocksStart)
    DisposableEffect(viewModel, registry) {
        viewModel.immediateFocus = { request -> registry.focus(request) }
        onDispose { viewModel.immediateFocus = null }
    }
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
internal fun FocusNewNoteTitle(titleFocus: FocusRequester, viewModel: EditNoteViewModel = metroViewModel()) {
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
internal class BlockSheetState(kind: MutableState<BlockSheetKind?>, blockId: MutableState<String?>) {
    var kind by kind
    var blockId by blockId

    fun open(sheet: BlockSheetKind, id: String) {
        kind = sheet
        blockId = id
    }

    fun close() {
        kind = null
        blockId = null
    }
}

@Composable
internal fun rememberBlockSheetState(): BlockSheetState {
    val kind = rememberSaveable { mutableStateOf<BlockSheetKind?>(null) }
    val blockId = rememberSaveable { mutableStateOf<String?>(null) }
    return remember { BlockSheetState(kind, blockId) }
}

/** Whether the find bar is open. */
@Composable
internal fun rememberFindOpen(viewModel: EditNoteViewModel = metroViewModel()): State<Boolean> {
    val open = remember(viewModel) { viewModel.find.map { it.isOpen }.distinctUntilChanged() }
    return open.collectAsState(initial = false)
}

/**
 * Scrolls the list to the block of the match Find is on, when that block is off screen. Nothing
 * gets focus and nothing is edited.
 */
@Composable
internal fun ScrollToFindMatch(
    listState: LazyListState,
    blocksStart: Int,
    viewModel: EditNoteViewModel = metroViewModel(),
) {
    val current = remember(viewModel) { viewModel.find.map { it.currentMatch }.distinctUntilChanged() }
    val match by current.collectAsState(initial = null)
    val currentBlocksStart by rememberUpdatedState(blocksStart)
    LaunchedEffect(match) {
        val blockId = match?.blockId ?: return@LaunchedEffect
        val index = viewModel.blocks.indexOfFirst { it.id == blockId }
        if (index >= 0) listState.reveal(currentBlocksStart + index)
    }
}

/** Back closes the find bar, else an open slash picker, before it leaves the note. */
@Composable
internal fun CloseFindOrSlashOnBack(viewModel: EditNoteViewModel = metroViewModel()) {
    val findOpen by rememberFindOpen()
    val slashMenu by viewModel.slashPicker.menu.collectAsState()
    val slashShown = slashMenu?.isShown == true
    BackHandler(enabled = findOpen || slashShown) {
        if (findOpen) viewModel.closeFind() else viewModel.slashPicker.dismiss()
    }
}

/**
 * Ties an editor in the detail pane to its tab: the tab shows the note's title and, once the note is
 * stored, its id; and the tabs can save the editor before showing another note, or close it the way its
 * Back does.
 */
@Composable
internal fun PaneTabBinding(pane: EditorPane, exits: EditorExits, viewModel: EditNoteViewModel = metroViewModel()) {
    val noteState by viewModel.state.collectAsState()
    val note = noteState.note
    LaunchedEffect(pane, note.id, note.title) { pane.tabs.report(pane.tabKey, note.id, note.title) }
    val currentExits by rememberUpdatedState(exits)
    DisposableEffect(pane, viewModel) {
        val editor = ActiveEditor(
            save = {
                val saved = viewModel.saveNote()
                // The stored id (a new note gets one now) before the tabs decide whether this one is blank.
                val stored = viewModel.state.value.note
                if (saved) pane.tabs.report(pane.tabKey, stored.id, stored.title)
                saved
            },
            requestClose = { currentExits.back() },
        )
        pane.tabs.attach(editor)
        onDispose { pane.tabs.detach(editor) }
    }
}
