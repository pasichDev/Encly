package com.pasich.encly.presentation.editor.state

import com.pasich.encly.dynamicBlocks.Block

// Editor toolbar actions: they act on the block the user last worked on.

/** Whether the block the user works on can move one place [up] (or down). */
fun BlockEditorState.canMoveInteracted(up: Boolean): Boolean {
    val from = selection.interactedIndex
    val to = if (up) from - 1 else from + 1
    return from in blocks.indices && to in blocks.indices
}

/** Moves the block the user works on one place [up] (or down). */
fun BlockEditorState.moveInteracted(up: Boolean) {
    if (!canMoveInteracted(up)) return
    val from = selection.interactedIndex
    moveBlock(from, if (up) from - 1 else from + 1)
}

/** Position of block [blockId]; -1 when it is not in the note (any more). */
fun BlockEditorState.indexOf(blockId: String): Int = blocks.indexOfFirst { it.id == blockId }

/** Adds [block] after position [afterIndex] and focuses it. */
fun BlockEditorState.addBlockAfter(afterIndex: Int, block: Block) {
    val target = (afterIndex + 1).coerceIn(0, blocks.size)
    batch { addBlock(target, block) }
    selection.focusAt(target)
}

/** Moves block [blockId] one place [up] (or down), if there is a place to go. */
fun BlockEditorState.moveBlockOf(blockId: String, up: Boolean) {
    val from = indexOf(blockId)
    val to = if (up) from - 1 else from + 1
    if (from in blocks.indices && to in blocks.indices) moveBlock(from, to)
}

/** Whether the block the user works on can be removed: one block always remains. */
fun BlockEditorState.canRemoveInteracted(): Boolean = blocks.size > 1 && selection.interactedIndex in blocks.indices

/** Removes the block the user works on. */
fun BlockEditorState.removeInteracted() {
    if (!canRemoveInteracted()) return
    removeBlock(blocks[selection.interactedIndex], BlockRemoveAction.REMOVE)
}

/** Where the block the user works on is: [BLOCK_AT_TOP], [BLOCK_AT_BOTTOM] or [BLOCK_IN_MIDDLE]. */
fun BlockEditorState.interactedBlockPosition(): Int = when (selection.interactedIndex) {
    0 -> BLOCK_AT_TOP
    blocks.lastIndex -> BLOCK_AT_BOTTOM
    else -> BLOCK_IN_MIDDLE
}

/** Focuses the trailing text block, adding one first unless the note already ends in one. */
fun BlockEditorState.addBlockToEnd() {
    if (blocks.lastOrNull() is Block.TextBlock) {
        selection.focusAt(blocks.lastIndex)
    } else {
        addBlockAfter(blocks.lastIndex, Block.TextBlock())
    }
}

const val BLOCK_AT_TOP = 1
const val BLOCK_AT_BOTTOM = 2
const val BLOCK_IN_MIDDLE = -1
