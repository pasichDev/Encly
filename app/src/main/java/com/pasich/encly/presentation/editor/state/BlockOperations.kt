package com.pasich.encly.presentation.editor.state

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.pasich.encly.dynamicBlocks.Block
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Undo/redo for the block editor.
 *
 * Structural operations (add/remove/move/replace) are recorded by position: the stacks are
 * strictly LIFO, so every undo runs against exactly the list its operation produced.
 * Content edits are recorded against the block's own state holder, never an index, and
 * their old value is read from the model at the moment of the edit, so an edit can never
 * be applied to a different block or restore a stale value.
 *
 * Consecutive typing in one field within [MERGE_WINDOW_MS] becomes one step, and both
 * stacks keep at most [MAX_HISTORY] steps.
 */
class BlockOperations(
    private val blocks: SnapshotStateList<Block>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val undoStack = ArrayDeque<Operation>()
    private val redoStack = ArrayDeque<Operation>()

    // Set by undo/redo so the next edit starts a new step instead of merging into an
    // older one that is now on top of the stack.
    private var breakMerge = false

    /**
     * Adds a new block at the specified index.
     * @param index index at which to add the new block
     * @param block the new block
     */
    fun addBlock(index: Int, block: Block) {
        if (index !in 0..blocks.size) return
        record(AddOperation(index, block))
    }

    /**
     * Removes the block at the specified index.
     * @param index index of the block to remove
     */
    fun removeBlock(index: Int) {
        if (index !in blocks.indices) return
        record(RemoveOperation(index, blocks[index]))
    }

    /**
     * Moves a block from one index to another.
     * @param fromIndex the starting index
     * @param toIndex the destination index
     */
    fun moveBlock(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in blocks.indices || toIndex !in blocks.indices) return
        record(MoveOperation(fromIndex, toIndex))
    }

    /**
     * Replaces the block at the specified index with a new block.
     * @param index index of the block to replace
     * @param newBlock the new block
     */
    fun replaceBlock(index: Int, newBlock: Block) {
        if (index !in blocks.indices) return
        record(ReplaceOperation(index, blocks[index], newBlock))
    }

    /**
     * Sets [state] (a field of the block [blockId]) to [newValue] and records the edit.
     * [mergeable] edits (typing) that follow each other in the same field within
     * [MERGE_WINDOW_MS] collapse into one undo step.
     */
    fun <T> changeValue(blockId: String, state: MutableStateFlow<T>, newValue: T, mergeable: Boolean) {
        val oldValue = state.value
        if (oldValue == newValue) return
        state.value = newValue

        val now = clock()
        val apply = { state.value = newValue }
        val mergeTarget = undoStack.lastOrNull()
            ?.takeIf { mergeable && !breakMerge }
            as? ValueChangeOperation
        if (mergeTarget != null && mergeTarget.canMerge(blockId, state, now)) {
            // Same undo (back to the value before the first keystroke), newer redo.
            undoStack[undoStack.lastIndex] = mergeTarget.copy(applyChange = apply, timestamp = now)
        } else {
            val revert = { state.value = oldValue }
            pushUndo(ValueChangeOperation(blockId, state, apply, revert, mergeable, now))
        }
        redoStack.clear()
        breakMerge = false
    }

    /**
     * Undoes the last operation.
     * @return true if the operation was undone, false otherwise
     */
    fun undo(): Boolean {
        val operation = undoStack.removeLastOrNull() ?: return false
        execute(operation.createInverse())
        redoStack.addLast(operation)
        trim(redoStack)
        breakMerge = true
        return true
    }

    /**
     * Redoes a previously undone operation.
     * @return true if the operation was redone, false otherwise
     */
    fun redo(): Boolean {
        val operation = redoStack.removeLastOrNull() ?: return false
        execute(operation)
        pushUndo(operation)
        breakMerge = true
        return true
    }

    /**
     * Checks whether an operation can be undone.
     */
    fun canUndo(): Boolean = undoStack.isNotEmpty()

    /**
     * Checks whether an operation can be redone.
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    private fun record(operation: Operation) {
        execute(operation)
        pushUndo(operation)
        redoStack.clear()
        breakMerge = false
    }

    private fun pushUndo(operation: Operation) {
        undoStack.addLast(operation)
        trim(undoStack)
    }

    private fun trim(stack: ArrayDeque<Operation>) {
        while (stack.size > MAX_HISTORY) stack.removeFirst()
    }

    /**
     * Executes an operation on the block list.
     */
    private fun execute(operation: Operation) {
        when (operation) {
            is AddOperation -> blocks.add(operation.index, operation.block)

            is RemoveOperation -> blocks.removeAt(operation.index)

            is MoveOperation -> {
                val block = blocks.removeAt(operation.fromIndex)
                blocks.add(operation.toIndex, block)
            }

            is ReplaceOperation -> blocks[operation.index] = operation.newBlock

            is ValueChangeOperation -> operation.applyChange()
        }
    }

    // Internal operation classes

    /**
     * Base interface for all operations.
     */
    private sealed interface Operation {
        fun createInverse(): Operation
    }

    /**
     * Block-add operation.
     */
    private class AddOperation(val index: Int, val block: Block) : Operation {
        override fun createInverse(): Operation = RemoveOperation(index, block)
    }

    /**
     * Block-remove operation.
     */
    private class RemoveOperation(val index: Int, val block: Block) : Operation {
        override fun createInverse(): Operation = AddOperation(index, block)
    }

    /**
     * Block-move operation.
     */
    private class MoveOperation(val fromIndex: Int, val toIndex: Int) : Operation {
        override fun createInverse(): Operation = MoveOperation(toIndex, fromIndex)
    }

    /**
     * Block-replace operation.
     */
    private class ReplaceOperation(val index: Int, val oldBlock: Block, val newBlock: Block) : Operation {
        override fun createInverse(): Operation = ReplaceOperation(index, newBlock, oldBlock)
    }

    /**
     * A content edit of one block field ([state]): [applyChange] sets the new value,
     * [revertChange] the old one.
     */
    private data class ValueChangeOperation(
        val blockId: String,
        val state: MutableStateFlow<*>,
        val applyChange: () -> Unit,
        val revertChange: () -> Unit,
        val mergeable: Boolean,
        val timestamp: Long,
    ) : Operation {
        override fun createInverse(): Operation =
            ValueChangeOperation(blockId, state, revertChange, applyChange, mergeable = false, timestamp)

        fun canMerge(otherBlockId: String, otherState: MutableStateFlow<*>, now: Long): Boolean =
            mergeable && blockId == otherBlockId && state === otherState &&
                now - timestamp <= MERGE_WINDOW_MS
    }

    companion object {
        /** Typing pauses longer than this start a new undo step. */
        const val MERGE_WINDOW_MS = 1_000L

        /** Oldest steps are dropped beyond this many. */
        const val MAX_HISTORY = 100
    }
}
