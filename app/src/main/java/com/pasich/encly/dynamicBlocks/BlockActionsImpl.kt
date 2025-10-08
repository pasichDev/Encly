package com.pasich.encly.dynamicBlocks

import android.util.Log
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel

class BlockActionsImpl(
    val block: Block,
    val index: Int,
    val viewModel: EditNoteViewModel,
) : BlockActions {
    override fun onAddParagraph() {
        Log.d("BlockActionsImpl", "onAddParagraph called: index=$index")
        viewModel.addBlockAfter(index, BlockType.TEXT)
    }

    override fun onRemoveBlock(blockRemoveAction: BlockRemoveAction) {
        Log.d("BlockActionsImpl", "onRemoveBlock called: action=$blockRemoveAction, blockType=${block::class.simpleName}, index=$index")

        if (blockRemoveAction == BlockRemoveAction.REMOVE_BACKSPACE_LIST) {
            if (viewModel.blocks.size > 1) {
                viewModel.removeBlock(block, BlockRemoveAction.REMOVE)
            }
            return
        }
        viewModel.removeBlock(block, blockRemoveAction)
    }

    override fun onReplaceBlock(newBlock: Block) {
        Log.d(
            "BlockActionsImpl",
            "onReplaceBlock called: oldType=${block::class.simpleName}, newType=${newBlock::class.simpleName}, index=$index",
        )
        viewModel.replaceBlock(index, newBlock)
    }

    override fun onTextChanged(
        oldText: String,
        newText: String,
    ) {
        viewModel.registerTextChange(index, oldText, newText)
    }

    override fun onContentChanged(
        oldBlock: Block,
        newBlock: Block,
    ) {
        viewModel.registerContentChange(index, oldBlock, newBlock)
    }

    override fun updateLastInteractionIndex(index: Int) {
        viewModel.setLastInteractionIndex(index)
    }

    /**
     * Обробляє навігацію між блоками
     */
    override fun navigateToNext(): Boolean {
        Log.d(
            "BlockActionsImpl",
            "navigateToNext called: current block index=$index, current focus=${viewModel.focusManager.currentFocusIndex.value}, blocks.size=${viewModel.blocks.size}",
        )
        val result = viewModel.focusManager.moveToNext(viewModel.blocks.size)
        Log.d("BlockActionsImpl", "navigateToNext result: $result")
        return result
    }

    /**
     * Обробляє навігацію до попереднього блока
     */
    override fun navigateToPrevious(): Boolean {
        Log.d("BlockActionsImpl", "navigateToPrevious called: current index=$index")
        val result = viewModel.focusManager.moveToPrevious(viewModel.blocks)
        Log.d("BlockActionsImpl", "navigateToPrevious result: $result")
        return result
    }

    /**
     * Встановлює фокус на поточний блок
     */
    fun requestFocus() {
        viewModel.setFocusedBlockIndex(index)
        viewModel.setLastInteractionIndex(index)
    }
}
