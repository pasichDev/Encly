package com.pasich.encly.dynamicBlocks

import androidx.compose.ui.graphics.Color
import com.pasich.encly.R
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

enum class BlockType {
    TEXT,
    LIST_NUMBER,
    LIST_CHECK,
    LIST_BULLET,
    QUOTE,
    SEPARATOR,
    LINK,
    H1,
    H2,
    H3,
    H4,
}

/** A fresh editor-session identity for a block or list item. Never persisted. */
fun newBlockId(): String = UUID.randomUUID().toString()

/**
 * Every block carries [id], a stable identity for the editor session: Compose keys its
 * remembered state by it, undo and focus find their block by it, and two blocks with equal
 * content (two separators) are still different blocks. It is not serialized; loading a note
 * gives every block a new one.
 */
sealed class Block {
    abstract val id: String

    data class TextBlock(
        override val text: MutableStateFlow<String> = MutableStateFlow(""),
        val placeholder: Int = R.string.text,
        override val id: String = newBlockId(),
    ) : Block(),
        TextualBlock

    data class HBlock(
        override val text: MutableStateFlow<String> = MutableStateFlow(""),
        val blockType: BlockType,
        override val id: String = newBlockId(),
    ) : Block(),
        TextualBlock

    data class QuoteBlock(
        override val text: MutableStateFlow<String> = MutableStateFlow(""),
        override val id: String = newBlockId(),
    ) : Block(),
        TextualBlock

    data class LinkBlock(
        val block: MutableStateFlow<LinkDataBlock> = MutableStateFlow(
            LinkDataBlock(),
        ),
        override val id: String = newBlockId(),
    ) : Block()

    data class SeparatorBlock(@Transient val color: Color = Color.Gray, override val id: String = newBlockId()) :
        Block()

    data class ListBlock(
        val items: MutableStateFlow<List<ItemListBlock>> = MutableStateFlow(
            listOf(
                ItemListBlock(""),
            ),
        ),
        val blockType: BlockType,
        override val id: String = newBlockId(),
    ) : Block()
}

/**
 * Whether [this] block is stored. The single rule for "blank": blocks that carry no content
 * are left out when a note is serialized, and a draft of only such blocks is not saved.
 */
fun Block.hasContent(): Boolean = when (this) {
    is Block.TextBlock -> text.value.isNotBlank()
    is Block.HBlock -> text.value.isNotBlank()
    is Block.QuoteBlock -> text.value.isNotBlank()
    is Block.ListBlock -> items.value.any { item -> item.value.isNotBlank() }
    is Block.LinkBlock -> block.value.url.isNotBlank()
    is Block.SeparatorBlock -> true
}
