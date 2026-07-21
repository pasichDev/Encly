package com.pasich.encly.dynamicBlocks

import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Class for managing block operations with undo/redo support.
 * Supports undo/redo of block structure changes and content changes within blocks.
 */
class BlockOperations(
    private val blocks: SnapshotStateList<Block>,
) {
    private val undoStack = mutableListOf<Operation>()
    private val redoStack = mutableListOf<Operation>()

    /**
     * Adds a new block at the specified index.
     * @param index index at which to add the new block
     * @param block the new block
     */
    fun addBlock(
        index: Int,
        block: Block,
    ) {
        val operation = AddOperation(index, block)
        executeOperation(operation)
        undoStack.add(operation)
        redoStack.clear() // Clear the redo stack after a new operation
    }

    /**
     * Removes the block at the specified index.
     * @param index index of the block to remove
     */
    fun removeBlock(index: Int) {
        if (index !in blocks.indices) return

        val removedBlock = blocks[index]
        val operation = RemoveOperation(index, removedBlock)
        executeOperation(operation)
        undoStack.add(operation)
        redoStack.clear()
    }

    /**
     * Moves a block from one index to another.
     * @param fromIndex the starting index
     * @param toIndex the destination index
     */
    fun moveBlock(
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (fromIndex !in blocks.indices || toIndex !in blocks.indices) return

        val operation = MoveOperation(fromIndex, toIndex)
        executeOperation(operation)
        undoStack.add(operation)
        redoStack.clear()
    }

    /**
     * Replaces the block at the specified index with a new block.
     * @param index index of the block to replace
     * @param newBlock the new block
     */
    fun replaceBlock(
        index: Int,
        newBlock: Block,
    ) {
        if (index !in blocks.indices) return

        val oldBlock = blocks[index]
        val operation = ReplaceOperation(index, oldBlock, newBlock)
        executeOperation(operation)
        undoStack.add(operation)
        redoStack.clear()
    }

    /**
     * Undoes the last operation.
     * @return true if the operation was undone, false otherwise
     */
    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false

        val operation = undoStack.removeAt(undoStack.size - 1)
        val inverseOperation = operation.createInverse()
        executeOperation(inverseOperation)
        redoStack.add(operation)
        return true
    }

    /**
     * Redoes a previously undone operation.
     * @return true if the operation was redone, false otherwise
     */
    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false

        val operation = redoStack.removeAt(redoStack.size - 1)
        executeOperation(operation)
        undoStack.add(operation)
        return true
    }

    /**
     * Executes an operation on the block list.
     */
    private fun executeOperation(operation: Operation) {
        when (operation) {
            is AddOperation -> blocks.add(operation.index, operation.block)
            is RemoveOperation -> blocks.removeAt(operation.index)
            is MoveOperation -> {
                val block = blocks.removeAt(operation.fromIndex)
                blocks.add(operation.toIndex, block)
            }
            is ReplaceOperation -> blocks[operation.index] = operation.newBlock
            is TextChangeOperation -> operation.apply()
            is ContentChangeOperation -> operation.apply()
        }
    }

    /**
     * Returns the current state of the blocks.
     */
    fun getCurrentBlocks(): List<Block> = blocks.toList()

    /**
     * Replaces all blocks with a new list.
     */
    fun setBlocks(newBlocks: List<Block>) {
        blocks.clear()
        blocks.addAll(newBlocks)
        undoStack.clear()
        redoStack.clear()
    }

    /**
     * Checks whether an operation can be undone.
     */
    fun canUndo(): Boolean = undoStack.isNotEmpty()

    /**
     * Checks whether an operation can be redone.
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * Registers a text change in a block.
     * @param index index of the block
     * @param oldText the old text
     * @param newText the new text
     */
    fun registerTextChange(
        index: Int,
        oldText: String,
        newText: String,
    ) {
        if (index !in blocks.indices || oldText == newText) return

        val operation = TextChangeOperation(index, oldText, newText)
        undoStack.add(operation)
        redoStack.clear()
    }

    /**
     * Registers changes in a block of any type.
     * @param index index of the block
     * @param oldBlock the previous state of the block
     * @param newBlock the new state of the block
     */
    fun registerContentChange(
        index: Int,
        oldBlock: Block,
        newBlock: Block,
    ) {
        if (index !in blocks.indices) return

        val operation = ContentChangeOperation(index, oldBlock, newBlock)
        undoStack.add(operation)
        redoStack.clear()
    }

    // Internal operation classes

    /**
     * Base interface for all operations.
     */
    private interface Operation {
        fun createInverse(): Operation
    }

    /**
     * Block-add operation.
     */
    private inner class AddOperation(
        val index: Int,
        val block: Block,
    ) : Operation {
        override fun createInverse(): Operation = RemoveOperation(index, block)
    }

    /**
     * Block-remove operation.
     */
    private inner class RemoveOperation(
        val index: Int,
        val block: Block,
    ) : Operation {
        override fun createInverse(): Operation = AddOperation(index, block)
    }

    /**
     * Block-move operation.
     */
    private inner class MoveOperation(
        val fromIndex: Int,
        val toIndex: Int,
    ) : Operation {
        override fun createInverse(): Operation = MoveOperation(toIndex, fromIndex)
    }

    /**
     * Block-replace operation.
     */
    private inner class ReplaceOperation(
        val index: Int,
        val oldBlock: Block,
        val newBlock: Block,
    ) : Operation {
        override fun createInverse(): Operation = ReplaceOperation(index, newBlock, oldBlock)
    }

    /**
     * Text-change operation within a block.
     */
    private inner class TextChangeOperation(
        val index: Int,
        val oldText: String,
        val newText: String,
    ) : Operation {
        override fun createInverse(): Operation = TextChangeOperation(index, newText, oldText)

        /**
         * Executes the text-change operation.
         * Uses the TextualBlock interface for unified text handling.
         */
        fun apply() {
            if (index !in blocks.indices) return

            val block = blocks[index]
            if (block is TextualBlock) {
                block.text.value = newText
            }
        }
    }

    /**
     * Block content-change operation.
     * Used for more complex blocks where the entire state needs to be preserved.
     */
    private inner class ContentChangeOperation(
        val index: Int,
        val oldBlock: Block,
        val newBlock: Block,
    ) : Operation {
        override fun createInverse(): Operation = ContentChangeOperation(index, newBlock, oldBlock)

        /**
         * Executes the content-change operation.
         * Fully replaces the block with a new one.
         */
        fun apply() {
            if (index !in blocks.indices) return
            blocks[index] = newBlock
        }
    }
}

/**
 * Extension for creating BlockOperations from a list of blocks.
 */
fun SnapshotStateList<Block>.toBlockOperations(): BlockOperations = BlockOperations(this)
