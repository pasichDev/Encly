package com.pasich.encly.presentation.editor.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import com.pasich.encly.core.AppLogger
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.editor.state.FocusRequest
import com.pasich.encly.presentation.editor.state.previousFocusableIndex
import kotlinx.coroutines.delay

/** How many frames' worth of retries a new block gets to compose before focusing it. */
private const val FOCUS_RETRIES = 5
private const val FOCUS_RETRY_DELAY_MS = 50L

private const val TAG = "BlockFocusRegistry"

/**
 * The editor's focus targets, by block id: how to focus each block's field and how to put its
 * cursor at the end. It lives in composition ([rememberBlockFocusRegistry]); the ViewModel only
 * says *which* block to focus ([FocusRequest]), so no UI object outlives the screen.
 *
 * Keyed by block id, a request made for a block reaches that block even after blocks around it
 * were inserted, removed, moved or replaced. [blocks] is the editor's live block list.
 */
class BlockFocusRegistry(private val blocks: () -> List<Block>) {
    private val focusTargets = mutableMapOf<String, () -> Unit>()
    private val cursorToEndCallbacks = mutableMapOf<String, () -> Unit>()

    /**
     * Registers how to focus the field of block [blockId].
     * @return a function that removes this registration (not a newer one for the same block)
     */
    fun registerFocusTarget(blockId: String, requestFocus: () -> Unit): () -> Unit =
        focusTargets.register(blockId, requestFocus)

    /**
     * Registers how to put the cursor at the end of block [blockId]'s text.
     * @return a function that removes this registration (not a newer one for the same block)
     */
    fun registerCursorToEnd(blockId: String, callback: () -> Unit): () -> Unit =
        cursorToEndCallbacks.register(blockId, callback)

    /**
     * Focuses the field of [blockId] now; always issues the request, even for the field that
     * has focus already. Returns false when the field is not composed (yet).
     */
    fun focus(blockId: String, cursorToEnd: Boolean = false): Boolean {
        val target = focusTargets[blockId] ?: return false
        try {
            target()
        } catch (e: IllegalStateException) {
            // The FocusRequester is not attached to a focusable node (a list, a saved link).
            AppLogger.e(TAG, "Focus request failed", e)
        }
        if (cursorToEnd) cursorToEndCallbacks[blockId]?.invoke()
        return true
    }

    /** Carries out [request] as soon as the block's field is composed; gives up after a few frames. */
    suspend fun focusWhenComposed(request: FocusRequest) {
        repeat(FOCUS_RETRIES) {
            if (focus(request.blockId, request.cursorToEnd)) return
            delay(FOCUS_RETRY_DELAY_MS)
        }
        AppLogger.w(TAG, "Focus target never appeared")
    }

    /** The block after [blockId]; null when it is the last one. */
    fun nextBlock(blockId: String): Block? {
        val list = blocks()
        val index = list.indexOfFirst { it.id == blockId }
        return if (index >= 0) list.getOrNull(index + 1) else null
    }

    /** The focusable block above [blockId] (see [previousFocusableIndex]); null when there is none. */
    fun previousFocusableBlock(blockId: String): Block? {
        val list = blocks()
        val index = list.indexOfFirst { it.id == blockId }
        val previous = list.previousFocusableIndex(index)
        return if (index >= 0 && previous != index) list.getOrNull(previous) else null
    }
}

private fun MutableMap<String, () -> Unit>.register(blockId: String, action: () -> Unit): () -> Unit {
    this[blockId] = action
    return { if (this[blockId] === action) remove(blockId) }
}

/** The focus registry of one editor showing [blocks] (its live block list). */
@Composable
fun rememberBlockFocusRegistry(blocks: List<Block>): BlockFocusRegistry =
    remember(blocks) { BlockFocusRegistry { blocks } }

/** Registers [focusRequester], the field of block [blockId], while it is composed. */
@Composable
fun RegisterFocusRequester(blockId: String, registry: BlockFocusRegistry, focusRequester: FocusRequester) {
    DisposableEffect(blockId, registry, focusRequester) {
        val unregister = registry.registerFocusTarget(blockId) { focusRequester.requestFocus() }
        onDispose { unregister() }
    }
}
