package com.pasich.encly.dynamicBlocks

enum class BlockRemoveAction{REMOVE, REMOVE_BACKSPACE, REMOVE_BACKSPACE_LIST}

interface BlockActions {
    fun onAddParagraph()
    fun onRemoveBlock(blockRemoveAction: BlockRemoveAction)
    fun onReplaceBlock(type: Block)
    fun onTextChanged(oldText: String, newText: String)
    fun onContentChanged(oldBlock: Block, newBlock: Block)

    fun updateLastInteractionIndex(index: Int)
    fun navigateToNext(): Boolean
    fun navigateToPrevious(): Boolean
}
