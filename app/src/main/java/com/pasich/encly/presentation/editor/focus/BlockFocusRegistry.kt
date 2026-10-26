package com.pasich.encly.presentation.editor.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import com.pasich.encly.core.AppLogger
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.editor.state.FocusRequest
import com.pasich.encly.presentation.editor.state.canTakeFocus
import kotlinx.coroutines.delay

/** How many frames' worth of retries a new block gets to compose before focusing it. */
private const val FOCUS_RETRIES = 5
private const val FOCUS_RETRY_DELAY_MS = 50L

/** Retries before scrolling: a new block on screen needs no scroll, only its first frames. */
private const val COMPOSE_WAITS = 1

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
    private val focusTargets = mutableMapOf<String, (FocusRequest) -> Unit>()
    private val caretCallbacks = mutableMapOf<String, (Int) -> Unit>()
    private val caretXCallbacks = mutableMapOf<String, (Float, Boolean) -> Unit>()

    /**
     * Registers how to focus the field of block [blockId].
     * @return a function that removes this registration (not a newer one for the same block)
     */
    fun registerFocusTarget(blockId: String, requestFocus: () -> Unit): () -> Unit =
        focusTargets.register(blockId) { request ->
            requestFocus()
            val offset = request.caretOffset
            val x = request.caretX
            when {
                x != null -> caretXCallbacks[blockId]?.invoke(x, request.fromBelow)
                offset != null -> caretCallbacks[blockId]?.invoke(offset)
            }
        }

    /**
     * Registers a block with several fields (a list): [requestFocus] focuses the item the
     * request names, else its last field with the cursor at the end when asked for the end,
     * else its first field.
     */
    fun registerFieldsFocusTarget(blockId: String, requestFocus: (FocusRequest) -> Unit): () -> Unit =
        focusTargets.register(blockId, requestFocus)

    /**
     * Registers how to put the cursor of block [blockId]'s field at an offset ([Int.MAX_VALUE]:
     * the end; the field clamps it to its text).
     * @return a function that removes this registration (not a newer one for the same block)
     */
    fun registerCaret(blockId: String, callback: (Int) -> Unit): () -> Unit = caretCallbacks.register(blockId, callback)

    /**
     * Registers how to put the cursor of block [blockId]'s field at a horizontal position, on its
     * last line when it arrives from below (the callback's second argument), else on its first.
     * @return a function that removes this registration (not a newer one for the same block)
     */
    fun registerCaretAtX(blockId: String, callback: (Float, Boolean) -> Unit): () -> Unit =
        caretXCallbacks.register(blockId, callback)

    /** Focuses the field of [blockId] now, the cursor at its end with [cursorToEnd]; see [focus]. */
    fun focus(blockId: String, cursorToEnd: Boolean = false): Boolean = focus(FocusRequest(blockId, cursorToEnd))

    /**
     * Carries out [request] now; always issues it, even for the field that has focus already.
     * Returns false when the field is not composed (yet).
     */
    fun focus(request: FocusRequest): Boolean {
        val target = focusTargets[request.blockId] ?: return false
        try {
            target(request)
        } catch (e: IllegalStateException) {
            // The FocusRequester is not attached to a focusable node (a saved link).
            AppLogger.w(TAG, "Focus request failed: ${e.javaClass.simpleName}")
        }
        return true
    }

    /**
     * Carries out [request] as soon as the block's field is composed; gives up after a few frames.
     * A block just added is composed within a frame or two where it is on screen; a block of a
     * lazy list off screen is not composed at all, so [bringIntoView] then scrolls to it.
     */
    suspend fun focusWhenComposed(request: FocusRequest, bringIntoView: suspend (String) -> Unit = {}) {
        if (focusWithin(request, attempts = 1 + COMPOSE_WAITS)) return
        bringIntoView(request.blockId)
        if (!focusWithin(request, attempts = FOCUS_RETRIES)) AppLogger.w(TAG, "Focus target never appeared")
    }

    /** Tries [request] up to [attempts] times, a short wait apart; whether it was carried out. */
    private suspend fun focusWithin(request: FocusRequest, attempts: Int): Boolean {
        repeat(attempts) { attempt ->
            if (attempt > 0) delay(FOCUS_RETRY_DELAY_MS)
            if (focus(request)) return true
        }
        return false
    }

    /**
     * The next block after [blockId] that has a field to put the cursor in (a separator or a saved
     * link card has none); null when there is none.
     */
    fun nextFocusableBlock(blockId: String): Block? {
        val list = blocks()
        val index = list.indexOfFirst { it.id == blockId }
        return if (index >= 0) list.drop(index + 1).firstOrNull { it.canTakeFocus() } else null
    }

    /** The focusable block above [blockId] (the nearest one with a field); null when there is none. */
    fun previousFocusableBlock(blockId: String): Block? {
        val list = blocks()
        val index = list.indexOfFirst { it.id == blockId }
        return if (index > 0) list.take(index).lastOrNull { it.canTakeFocus() } else null
    }
}

private fun <T : Any> MutableMap<String, T>.register(blockId: String, action: T): () -> Unit {
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
