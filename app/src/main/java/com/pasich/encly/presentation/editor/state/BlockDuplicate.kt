package com.pasich.encly.presentation.editor.state

import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.newBlockId
import kotlinx.coroutines.flow.MutableStateFlow

/** Duplicates the block the user works on (see [duplicateBlockOf]). */
fun BlockEditorState.duplicateInteracted() {
    blocks.getOrNull(selection.interactedIndex)?.let { duplicateBlockOf(it.id) }
}

/**
 * Inserts a copy of block [blockId] right after it, as one undo step, and moves the user to the
 * copy (the cursor at its end). The copy shares nothing with the original: its content lives in
 * new state holders and its block and list item ids are new, so editing one never changes the
 * other. Returns false when the block is no longer there.
 */
fun BlockEditorState.duplicateBlockOf(blockId: String): Boolean {
    val index = indexOf(blockId)
    val copy = blocks.getOrNull(index)?.duplicate() ?: return false
    batch { addBlock(index + 1, copy) }
    if (copy.canTakeFocus()) selection.focusAt(index + 1, cursorToEnd = true) else selection.onInteraction(copy.id)
    return true
}

/** A copy of [this] block that shares no state or identity with it. */
internal fun Block.duplicate(): Block = when (this) {
    is Block.TextBlock -> copy(text = MutableStateFlow(text.value), id = newBlockId())

    is Block.HBlock -> copy(text = MutableStateFlow(text.value), id = newBlockId())

    is Block.QuoteBlock -> copy(text = MutableStateFlow(text.value), id = newBlockId())

    is Block.LinkBlock -> copy(block = MutableStateFlow(block.value.copy()), id = newBlockId())

    is Block.SeparatorBlock -> copy(id = newBlockId())

    is Block.ListBlock -> copy(
        items = MutableStateFlow(items.value.map { it.copy(id = newBlockId()) }),
        id = newBlockId(),
    )
}
