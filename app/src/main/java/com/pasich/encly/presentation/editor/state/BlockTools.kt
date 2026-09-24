package com.pasich.encly.presentation.editor.state

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import com.pasich.encly.dynamicBlocks.TextualBlock
import com.pasich.encly.dynamicBlocks.factory.BlockFactory
import kotlinx.coroutines.flow.MutableStateFlow

// The toolbar's block tools: they act on the block the user works on.

/** Focuses the first block that has a field (the title's "Next"); adds a paragraph when none has. */
fun BlockEditorState.focusFirstBlock() {
    val first = blocks.indexOfFirst { it.canTakeFocus() }
    if (first >= 0) selection.focusAt(first) else addBlockToEnd()
}

/**
 * Brings the keyboard back to where the user was writing: the block they worked on (or the
 * nearest one above that has a field), cursor at its end; the first block if none.
 */
fun BlockEditorState.focusWorkingBlock() {
    val index = selection.workingIndex
    val target = if (blocks.getOrNull(index)?.canTakeFocus() == true) index else blocks.previousFocusableIndex(index)
    if (blocks.getOrNull(target)?.canTakeFocus() ==
        true
    ) {
        selection.focusAt(target, cursorToEnd = true)
    } else {
        focusFirstBlock()
    }
}

/** The Heading tool: headings of every level show it active, and it makes an H2. */
val HEADING_TOOL = BlockType.H2

/** The toolbar tool shown active for [this] block; null for a paragraph. */
fun Block.toolType(): BlockType? = when (this) {
    is Block.TextBlock -> null
    is Block.HBlock -> HEADING_TOOL
    is Block.QuoteBlock -> BlockType.QUOTE
    is Block.ListBlock -> blockType
    is Block.LinkBlock -> BlockType.LINK
    is Block.SeparatorBlock -> BlockType.SEPARATOR
}

/**
 * Applies toolbar [tool] to the block the user works on: a text block turns into that type and
 * keeps its text, and the tool it already shows turns it back into a paragraph ([exact]: only
 * the very same type does, so "Heading 3" turns an H1 into an H3). Otherwise (a link or
 * separator, or a block that cannot change) a new block of [tool] is added after it.
 */
fun BlockEditorState.applyTool(tool: BlockType, exact: Boolean = false) {
    val index = selection.workingIndex
    val converted = blocks.getOrNull(index)?.let { convertBlock(it, tool, exact) }
    when {
        converted != null -> replaceBlock(index, converted)
        tool == BlockType.SEPARATOR -> addSeparator()
        else -> addBlock(BlockFactory.createBlock(tool))
    }
}

/** A separator, then a paragraph to go on writing in (the one already after it, if any). */
private fun BlockEditorState.addSeparator() {
    val separator = Block.SeparatorBlock()
    addBlock(separator)
    val at = blocks.indexOfFirst { it.id == separator.id }
    if (blocks.getOrNull(at + 1) is Block.TextBlock) selection.focusAt(at + 1) else addBlockAfter(at, Block.TextBlock())
}

/** [block] as [tool], keeping its text; null when it cannot be converted (see [applyTool]). */
internal fun convertBlock(block: Block, tool: BlockType, exact: Boolean = false): Block? {
    val text = block.plainText()
    val shown = if (exact) block.exactType() else block.toolType()
    return when {
        text == null || (block is Block.TextBlock && tool == BlockType.TEXT) -> null
        shown == tool -> Block.TextBlock(MutableStateFlow(text))
        else -> convertText(block, text, tool)
    }
}

private fun convertText(block: Block, text: String, tool: BlockType): Block? = when (tool) {
    BlockType.H1, BlockType.H2, BlockType.H3, BlockType.H4 ->
        Block.HBlock(MutableStateFlow(text.replace('\n', ' ')), blockType = tool)

    BlockType.QUOTE -> Block.QuoteBlock(MutableStateFlow(text))

    BlockType.LIST_CHECK, BlockType.LIST_BULLET, BlockType.LIST_NUMBER -> {
        val items = (block as? Block.ListBlock)?.items?.value ?: text.split('\n').map { ItemListBlock(it) }
        Block.ListBlock(MutableStateFlow(items), blockType = tool)
    }

    // Only an empty paragraph becomes a link; text is never thrown away.
    BlockType.LINK -> Block.LinkBlock().takeIf { block is Block.TextBlock && text.isEmpty() }

    BlockType.TEXT -> Block.TextBlock(MutableStateFlow(text))

    BlockType.SEPARATOR -> null
}

private fun Block.exactType(): BlockType? = when (this) {
    is Block.TextBlock -> BlockType.TEXT
    is Block.HBlock -> blockType
    else -> toolType()
}

private fun Block.plainText(): String? = when (this) {
    is Block.ListBlock -> items.value.joinToString("\n") { it.value }
    is TextualBlock -> text.value
    else -> null
}
