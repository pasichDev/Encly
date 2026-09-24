package com.pasich.encly.presentation.editor.state

import com.pasich.encly.dynamicBlocks.Block
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** Asks the editor UI to focus the field of block [blockId], optionally with the cursor at the end. */
data class FocusRequest(val blockId: String, val cursorToEnd: Boolean = false)

/**
 * Which block the user works on, by block id, and the focus requests the editor UI carries out.
 *
 * Plain state: the FocusRequesters themselves live in composition (BlockFocusRegistry), so
 * nothing here outlives the screen or needs a UI thread. [blocks] is the editor's live list.
 */
class BlockSelection(private val blocks: List<Block>) {
    private val focusedBlockId = MutableStateFlow<String?>(null)

    private val _interactedBlockId = MutableStateFlow<String?>(null)

    /** The block the user last worked on: focused, tapped, or opened the block sheet of. */
    val interactedBlockId: StateFlow<String?> = _interactedBlockId.asStateFlow()

    // Where the interacted block was. If it disappears (undo, delete), the block now at that
    // position stands in for it, so toolbar actions never read a position past the end.
    private var interactedIndexHint = 0

    private val focusRequests = Channel<FocusRequest>(Channel.CONFLATED)

    /** Focus requests for the editor UI; only the latest one matters. Collect from one place. */
    val requests: Flow<FocusRequest> = focusRequests.receiveAsFlow()

    /** Position of the focused block, or -1 when no block field has focus. */
    val focusedIndex: Int get() = blocks.indexOfFirst { it.id == focusedBlockId.value }

    /** Position of the block the user works on, always inside the list (0 for an empty one). */
    val interactedIndex: Int
        get() {
            val index = blocks.indexOfFirst { it.id == _interactedBlockId.value }
            return if (index >= 0) index else interactedIndexHint.coerceIn(0, blocks.lastIndex.coerceAtLeast(0))
        }

    /** The focused block, else the one the user last worked on: where toolbar actions apply. */
    val workingIndex: Int get() = focusedIndex.takeIf { it >= 0 } ?: interactedIndex

    /** The field of [blockId] took focus. */
    fun onFocused(blockId: String) {
        focusedBlockId.value = blockId
        onInteraction(blockId)
    }

    /**
     * The field of [blockId] lost focus. It no longer decides where a new block goes; the block
     * the user last worked on ([interactedIndex]) does, until another field takes focus.
     */
    fun onFocusLost(blockId: String) {
        if (focusedBlockId.value == blockId) focusedBlockId.value = null
    }

    /** The user works on [blockId] without its field taking focus (a block sheet). */
    fun onInteraction(blockId: String) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        _interactedBlockId.value = blockId
        interactedIndexHint = index
    }

    /** Focuses the block now at [index] once its field is composed. */
    fun focusAt(index: Int, cursorToEnd: Boolean = false) {
        val blockId = blocks.getOrNull(index)?.id ?: return
        onFocused(blockId)
        focusRequests.trySend(FocusRequest(blockId, cursorToEnd))
    }
}

/** Whether [this] block has a field that can take focus. */
fun Block.canTakeFocus(): Boolean = when (this) {
    is Block.TextBlock, is Block.HBlock, is Block.QuoteBlock -> true

    // A list focuses its own items.
    is Block.ListBlock -> true

    // A link has a field only while its URL is being entered.
    is Block.LinkBlock -> block.value.url.isBlank()

    is Block.SeparatorBlock -> false
}

/**
 * The block to focus instead of the one at [index]: the nearest focusable block above it,
 * else the first focusable block of the note (0 when there is none).
 */
fun List<Block>.previousFocusableIndex(index: Int): Int {
    val above = (index - 1 downTo 0).firstOrNull { it in indices && this[it].canTakeFocus() }
    return above ?: indexOfFirst { it.canTakeFocus() }.coerceAtLeast(0)
}
