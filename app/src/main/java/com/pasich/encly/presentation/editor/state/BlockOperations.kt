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
 * Consecutive typing in one field within [MERGE_WINDOW_MS] becomes one step per word (see
 * [TypingStep]), and both stacks keep at most [MAX_HISTORY] steps. An edit made of several
 * operations (Enter splitting a block, a paste becoming blocks) is recorded with [batch] and
 * undone as one step.
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
     * [MERGE_WINDOW_MS] collapse into one undo step while [step] continues the one before
     * (by default worked out for text; see [TypingStep]).
     */
    fun <T> changeValue(
        blockId: String,
        state: MutableStateFlow<T>,
        newValue: T,
        mergeable: Boolean,
        step: TypingStep? = null,
    ) {
        val oldValue = state.value
        if (oldValue == newValue) return
        state.value = newValue

        val now = clock()
        val typing = step ?: if (oldValue is String && newValue is String) typingStep("", oldValue, newValue) else null
        val apply = { state.value = newValue }
        val mergeTarget = undoStack.lastOrNull()
            ?.takeIf { mergeable && !breakMerge }
            as? ValueChangeOperation
        if (mergeTarget != null && mergeTarget.canMerge(blockId, state, now, typing)) {
            // Same undo (back to the value before the first keystroke), newer redo.
            undoStack[undoStack.lastIndex] = mergeTarget.copy(applyChange = apply, timestamp = now, step = typing)
        } else {
            val revert = { state.value = oldValue }
            pushUndo(ValueChangeOperation(blockId, state, apply, revert, mergeable, now, typing))
        }
        redoStack.clear()
        breakMerge = false
    }

    /**
     * Records the operations [build] makes as one undo step: undo takes them back in reverse
     * order, redo makes them again. Each runs at once, so a later one sees the list as the
     * earlier ones left it. Nothing is recorded when [build] changes nothing.
     */
    fun batch(build: Batch.() -> Unit) {
        val batch = Batch().apply(build)
        val operations = batch.operations
        if (operations.isEmpty()) return
        pushUndo(operations.singleOrNull() ?: CompositeOperation(operations))
        redoStack.clear()
        breakMerge = false
    }

    /** The operations of one [batch] step. */
    inner class Batch internal constructor() {
        internal val operations = mutableListOf<Operation>()

        fun addBlock(index: Int, block: Block) {
            if (index in 0..blocks.size) run(AddOperation(index, block))
        }

        fun removeBlock(index: Int) {
            if (index in blocks.indices) run(RemoveOperation(index, blocks[index]))
        }

        fun replaceBlock(index: Int, newBlock: Block) {
            if (index in blocks.indices) run(ReplaceOperation(index, blocks[index], newBlock))
        }

        /** Sets [state], a field of block [blockId], to [newValue]. */
        fun <T> setValue(blockId: String, state: MutableStateFlow<T>, newValue: T) {
            val oldValue = state.value
            if (oldValue == newValue) return
            run(
                ValueChangeOperation(
                    blockId = blockId,
                    state = state,
                    applyChange = { state.value = newValue },
                    revertChange = { state.value = oldValue },
                    mergeable = false,
                    timestamp = clock(),
                ),
            )
        }

        private fun run(operation: Operation) {
            execute(operation)
            operations += operation
        }
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

    /** Forgets every step: nothing can be undone or redone. */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        breakMerge = false
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

            is CompositeOperation -> operation.operations.forEach(::execute)
        }
    }

    // Internal operation classes

    /**
     * Base interface for all operations.
     */
    internal sealed interface Operation {
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
     * [revertChange] the old one. [step] is the typing it records, if any.
     */
    private data class ValueChangeOperation(
        val blockId: String,
        val state: MutableStateFlow<*>,
        val applyChange: () -> Unit,
        val revertChange: () -> Unit,
        val mergeable: Boolean,
        val timestamp: Long,
        val step: TypingStep? = null,
    ) : Operation {
        override fun createInverse(): Operation =
            ValueChangeOperation(blockId, state, revertChange, applyChange, mergeable = false, timestamp)

        fun canMerge(otherBlockId: String, otherState: MutableStateFlow<*>, now: Long, next: TypingStep?): Boolean {
            val sameField = mergeable && blockId == otherBlockId && state === otherState
            val sameTyping = step == null || next == null || next.continues(step)
            return sameField && sameTyping && now - timestamp <= MERGE_WINDOW_MS
        }
    }

    /** Several operations recorded as one step ([batch]). */
    private class CompositeOperation(val operations: List<Operation>) : Operation {
        override fun createInverse(): Operation = CompositeOperation(operations.asReversed().map { it.createInverse() })
    }

    companion object {
        /** Typing pauses longer than this start a new undo step. */
        const val MERGE_WINDOW_MS = 1_000L

        /** Oldest steps are dropped beyond this many. */
        const val MAX_HISTORY = 100
    }
}
