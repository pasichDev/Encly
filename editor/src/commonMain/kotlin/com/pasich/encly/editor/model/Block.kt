package com.pasich.encly.editor.model

import com.pasich.encly.editor.EditorString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

/** What a block is. Stored as the `blockType` key in the JSON of a note, so the names must not change. */
public enum class BlockType {
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
internal fun newBlockId(): String = Uuid.random().toString()

/**
 * Every block carries [id], a stable identity for the editor session: Compose keys its
 * remembered state by it, undo and focus find their block by it, and two blocks with equal
 * content (two separators) are still different blocks. It is not serialized; loading a note
 * gives every block a new one.
 */
public sealed class Block {
    /** The identity of the block for the editor session; see [Block]. */
    public abstract val id: String

    /** A paragraph. [placeholder] is the hint shown while it is empty. */
    public data class TextBlock(
        override val text: MutableStateFlow<String> = MutableStateFlow(""),
        val placeholder: EditorString = EditorString.TEXT,
        override val id: String = newBlockId(),
    ) : Block(),
        TextualBlock

    /** A heading; [blockType] is H1 to H4. */
    public data class HBlock(
        override val text: MutableStateFlow<String> = MutableStateFlow(""),
        val blockType: BlockType,
        override val id: String = newBlockId(),
    ) : Block(),
        TextualBlock

    /** A quotation. */
    public data class QuoteBlock(
        override val text: MutableStateFlow<String> = MutableStateFlow(""),
        override val id: String = newBlockId(),
    ) : Block(),
        TextualBlock

    /** A link card; [block] holds the link. */
    public data class LinkBlock(
        val block: MutableStateFlow<LinkDataBlock> = MutableStateFlow(
            LinkDataBlock(),
        ),
        override val id: String = newBlockId(),
    ) : Block()

    /** A horizontal rule. */
    public data class SeparatorBlock(override val id: String = newBlockId()) : Block()

    /** A bulleted list, numbered list or checklist (by [blockType]) of [items]; a checklist may be [locked]. */
    public data class ListBlock(
        val items: MutableStateFlow<List<ItemListBlock>> = MutableStateFlow(
            listOf(
                ItemListBlock(""),
            ),
        ),
        val blockType: BlockType,
        override val id: String = newBlockId(),
        /**
         * A locked checklist keeps its text out of reach (no focus, no keyboard) while its boxes
         * still toggle. Stored as an optional `isLocked` key, only when true.
         */
        val locked: MutableStateFlow<Boolean> = MutableStateFlow(false),
    ) : Block()
}

/**
 * Whether [this] block is stored. The single rule for "blank": blocks that carry no content
 * are left out when a note is serialized, and a draft of only such blocks is not saved.
 */
public fun Block.hasContent(): Boolean = when (this) {
    is Block.TextBlock -> text.value.isNotBlank()
    is Block.HBlock -> text.value.isNotBlank()
    is Block.QuoteBlock -> text.value.isNotBlank()
    is Block.ListBlock -> items.value.any { item -> item.value.isNotBlank() }
    is Block.LinkBlock -> block.value.url.isNotBlank()
    is Block.SeparatorBlock -> true
}
