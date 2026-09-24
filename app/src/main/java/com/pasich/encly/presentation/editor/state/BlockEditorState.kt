package com.pasich.encly.presentation.editor.state

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.utils.BlockUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

enum class BlockRemoveAction { REMOVE, REMOVE_BACKSPACE, REMOVE_BACKSPACE_LIST }

/**
 * The block editor's content: the blocks, their undo/redo history ([BlockOperations]) and
 * which block the user works on ([selection]). Plain state without coroutines or UI objects,
 * so it is testable on the JVM. The ViewModel decides *whether* an edit is allowed (a locked or
 * read-only editor); this class decides *how* it is applied.
 *
 * Invariant: the note always has at least one block.
 */
class BlockEditorState(clock: () -> Long = System::currentTimeMillis) {
    private val _blocks = SnapshotStateList<Block>().apply { add(emptyNoteBlock()) }
    val blocks: List<Block> get() = _blocks

    private val history = BlockOperations(_blocks, clock)

    val selection = BlockSelection(_blocks)

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /** Emits when blocks are added, removed or moved, or any block's content changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val contentChanges: Flow<Unit> = snapshotFlow { _blocks.toList() }.flatMapLatest { current ->
        val contentFlows = current.mapNotNull { it.contentFlow() }
        // combine() of no flows never emits; a note of only separators still changes.
        if (contentFlows.isEmpty()) flowOf(Unit) else combine(contentFlows) { }
    }

    /** Shows a stored note's [loaded] blocks in place of the current ones. */
    fun load(loaded: List<Block>) {
        _blocks.clear()
        _blocks.addAll(loaded)
        afterChange()
    }

    /**
     * Adds [block] after the focused block (or the one the user last worked on) and focuses it.
     * An empty first text block at that point is replaced instead, in one undo step, so undo can
     * never leave the note without blocks.
     */
    fun addBlock(block: Block) {
        val anchor = selection.workingIndex.coerceIn(-1, _blocks.lastIndex)
        val replacesEmptyFirst = block !is Block.TextBlock && _blocks.isEmptyFirstTextBlock(anchor)

        val target = if (replacesEmptyFirst) anchor else anchor + 1
        if (replacesEmptyFirst) history.replaceBlock(anchor, block) else history.addBlock(target, block)
        afterChange()

        selection.focusAt(target)
    }

    /** Adds [block] after position [afterIndex] and focuses it. */
    fun addBlockAfter(afterIndex: Int, block: Block) {
        val target = (afterIndex + 1).coerceIn(0, _blocks.size)
        history.addBlock(target, block)
        afterChange()
        selection.focusAt(target)
    }

    /**
     * Removes [block] if [action] allows it: one block always remains, and Backspace removes
     * only an empty block. With [refocus], the block above (or the first focusable) takes focus.
     */
    fun removeBlock(block: Block, action: BlockRemoveAction, refocus: Boolean = true) {
        val index = _blocks.indexOfFirst { it.id == block.id }
        val allowed = when (action) {
            BlockRemoveAction.REMOVE, BlockRemoveAction.REMOVE_BACKSPACE_LIST -> _blocks.size > 1
            BlockRemoveAction.REMOVE_BACKSPACE -> _blocks.size > 1 && BlockUtils.isBlockEmpty(block)
        }
        if (index == -1 || !allowed) return

        history.removeBlock(index)
        afterChange()
        if (refocus) selection.focusAt(_blocks.previousFocusableIndex(index), cursorToEnd = true)
    }

    /** Replaces the block at [index] with [newBlock] and focuses the new field. */
    fun replaceBlock(index: Int, newBlock: Block): Boolean {
        if (index !in _blocks.indices) return false
        history.replaceBlock(index, newBlock)
        afterChange()
        selection.focusAt(index)
        return true
    }

    /** Moves the block at [fromIndex] to [toIndex]; it keeps focus. */
    fun moveBlock(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _blocks.indices || toIndex !in _blocks.indices) return
        history.moveBlock(fromIndex, toIndex)
        afterChange()
        selection.focusAt(toIndex)
    }

    /**
     * Sets [state], a field of block [blockId], to [newValue] as one undoable edit. [mergeable]
     * edits (typing) in quick succession collapse into one undo step.
     */
    fun <T> changeValue(blockId: String, state: MutableStateFlow<T>, newValue: T, mergeable: Boolean) {
        history.changeValue(blockId, state, newValue, mergeable)
        afterChange()
    }

    fun undo() {
        if (history.undo()) afterChange()
    }

    fun redo() {
        if (history.redo()) afterChange()
    }

    private fun afterChange() {
        if (_blocks.isEmpty()) _blocks.add(emptyNoteBlock())
        _canUndo.value = history.canUndo()
        _canRedo.value = history.canRedo()
    }
}

/** The block a note without content shows. */
private fun emptyNoteBlock(): Block = Block.TextBlock(placeholder = R.string.press_to_edit)

private fun List<Block>.isEmptyFirstTextBlock(index: Int): Boolean {
    val block = getOrNull(index)
    return index == 0 && block is Block.TextBlock && block.text.value.isEmpty()
}

/** The content of [this] block, as a flow; null for a block without content (a separator). */
private fun Block.contentFlow(): Flow<Any>? = when (this) {
    is Block.TextBlock -> text
    is Block.HBlock -> text
    is Block.QuoteBlock -> text
    is Block.LinkBlock -> block.map { it.url }
    is Block.ListBlock -> items
    is Block.SeparatorBlock -> null
}
