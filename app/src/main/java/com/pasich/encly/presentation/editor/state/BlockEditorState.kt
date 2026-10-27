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
@Suppress("TooManyFunctions") // The editor's single entry point: one function per kind of edit.
class BlockEditorState(clock: () -> Long = System::currentTimeMillis) {
    private val _blocks = SnapshotStateList<Block>().apply { add(emptyNoteBlock()) }
    val blocks: List<Block> get() = _blocks

    private val history = BlockOperations(_blocks, clock)

    val selection = BlockSelection(_blocks)

    /** The slash picker: closes at any structural change, so it never outlives its paragraph. */
    val slash = SlashPicker()

    /** Find in this note: transient, read-only, forgotten when the note is loaded or the vault locks. */
    val find = NoteFind()

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

    /**
     * Shows a stored note's [loaded] blocks in place of the current ones. The history starts
     * over: undo never reaches back into what was shown before (nor keeps it in memory).
     */
    fun load(loaded: List<Block>) {
        slash.dismiss()
        find.close()
        _blocks.clear()
        _blocks.addAll(loaded)
        history.clear()
        afterChange()
    }

    /**
     * Adds [block] after the focused block (or the one the user last worked on) and focuses it.
     * An empty first text block at that point is replaced instead, in one undo step, so undo can
     * never leave the note without blocks.
     */
    fun addBlock(block: Block) {
        slash.dismiss()
        val anchor = selection.workingIndex.coerceIn(-1, _blocks.lastIndex)
        val replacesEmptyFirst = block !is Block.TextBlock && _blocks.isEmptyFirstTextBlock(anchor)

        val target = if (replacesEmptyFirst) anchor else anchor + 1
        if (replacesEmptyFirst) history.replaceBlock(anchor, block) else history.addBlock(target, block)
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

        slash.dismiss()
        history.removeBlock(index)
        afterChange()
        if (refocus) selection.focusAt(_blocks.previousFocusableIndex(index), cursorToEnd = true)
    }

    /**
     * Replaces the block at [index] with [newBlock] and focuses the new field. A converted block keeps
     * its text, so its cursor stays where it was: in the same list item, or on the same line.
     */
    fun replaceBlock(index: Int, newBlock: Block): Boolean {
        if (index !in _blocks.indices) return false
        slash.dismiss()
        val old = _blocks[index]
        val caret = selection.caretOf(old.id)
        val item = selection.itemOf(old.id)
        val target = carriedCaret(old, newBlock, caret, item)
        history.replaceBlock(index, newBlock)
        // Undone, the old block comes back with the cursor where it was; redone, where it was carried.
        if (target != null && caret != null) attachCaretToLastStep(CaretTarget(old.id, item, caret), target)
        afterChange()
        selection.focusAt(index, caret = target?.caret, itemId = target?.itemId)
        return true
    }

    /** Moves the block at [fromIndex] to [toIndex]; it keeps focus. */
    fun moveBlock(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _blocks.indices || toIndex !in _blocks.indices) return
        slash.dismiss()
        history.moveBlock(fromIndex, toIndex)
        afterChange()
        selection.focusAt(toIndex)
    }

    /**
     * Sets [state], a field of block [blockId], to [newValue] as one undoable edit. [mergeable]
     * edits (typing) in quick succession collapse into one undo step.
     */
    fun <T> changeValue(
        blockId: String,
        state: MutableStateFlow<T>,
        newValue: T,
        mergeable: Boolean,
        step: TypingStep? = null,
    ) {
        history.changeValue(blockId, state, newValue, mergeable, step)
        afterChange()
    }

    /** Makes the operations of [build] as one undo step (see [BlockOperations.batch]). */
    fun batch(build: BlockOperations.Batch.() -> Unit) {
        slash.dismiss()
        history.batch(build)
        afterChange()
    }

    fun undo() = moveHistory(undo = true)

    fun redo() = moveHistory(undo = false)

    /**
     * Undo or redo. The cursor goes where the step itself says ([CaretTarget], from what its fields
     * held before and after, or the merge's own record); a step with no text in it leaves the cursor
     * alone, and a block that is gone sends it to the nearest one above.
     */
    private fun moveHistory(undo: Boolean) {
        slash.dismiss()
        val focused = selection.focusedBlockId.value
        val fields = history.fieldsOfNext(undo)
        val before = fields.map { it.second.value }
        if (!(if (undo) history.undo() else history.redo())) return
        afterChange()
        if (focused == null) return
        val step = history.lastMovedCaret()
        val target = if (step != null) {
            if (undo) step.before else step.after
        } else {
            fields.indices.firstNotNullOfOrNull { caretTargetOf(fields[it].first, before[it], fields[it].second.value) }
        }
        val index = target?.let { t -> blocks.indexOfFirst { it.id == t.blockId } } ?: -1
        if (target != null && index >= 0 && blocks[index].canTakeFocus()) {
            selection.focusAt(index, caret = target.caret, itemId = target.itemId)
        } else {
            selection.keepFocus(blocks, focused)
        }
    }

    /** The step recorded last puts the cursor at [before] when undone and at [after] when redone. */
    internal fun attachCaretToLastStep(before: CaretTarget, after: CaretTarget) =
        history.attachCaret(BlockOperations.CaretStep(before, after))

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

    // The lock is content too: locking a list must be saved like typing in it.
    is Block.ListBlock -> combine(items, locked) { list, locked -> list to locked }

    is Block.SeparatorBlock -> null
}

/**
 * Undo and redo may rebuild the field the cursor was in (a list item, a block that was replaced),
 * and a field that leaves composition takes the keyboard with it. When a field had focus
 * ([blockId]), it is asked for again: the same block if it is still there, else the nearest one above.
 */
private fun BlockSelection.keepFocus(blocks: List<Block>, blockId: String?) {
    if (blockId == null) return
    val index = blocks.indexOfFirst { it.id == blockId }
    if (index >= 0 && blocks[index].canTakeFocus()) {
        // Same block: the cursor goes back where it was (and into the same list item); the field
        // clamps it if the undone text is shorter. Only a cursor never seen means "the end".
        // An item that has gone (undone away) is not asked for: the list falls back to its last item.
        val list = blocks[index] as? Block.ListBlock
        val item = itemOf(blockId)?.takeIf { id -> list == null || list.items.value.any { it.id == id } }
        val caret = caretOf(blockId)?.takeIf { list == null || item != null }
        focusAt(index, cursorToEnd = caret == null, caret = caret, itemId = item)
    } else {
        // The block is gone: the nearest one above, the cursor at its end.
        focusAt(blocks.previousFocusableIndex(interactedIndex + 1), cursorToEnd = true)
    }
}
