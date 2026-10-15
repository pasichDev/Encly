package com.pasich.encly.data.importer

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow

/** Builders for the Encly blocks the parsers produce. */
internal object ImportBlocks {
    private val PARAGRAPH_BREAK = Regex("""\n[ \t]*\n\s*""")
    private val HEADINGS = listOf(BlockType.H1, BlockType.H2, BlockType.H3, BlockType.H4)

    fun text(value: String): Block = Block.TextBlock(text = MutableStateFlow(value))

    /** [level] 1-4; deeper levels are H4. */
    fun heading(value: String, level: Int): Block = Block.HBlock(
        text = MutableStateFlow(value),
        blockType = HEADINGS[level.coerceIn(1, HEADINGS.size) - 1],
    )

    fun quote(value: String): Block = Block.QuoteBlock(text = MutableStateFlow(value))

    fun list(type: BlockType, items: List<ItemListBlock>): Block =
        Block.ListBlock(items = MutableStateFlow(items), blockType = type)

    /** Blank-line separated paragraphs as TEXT blocks; line breaks stay inside their paragraph. */
    fun paragraphs(text: String): List<Block> = text.trim().split(PARAGRAPH_BREAK)
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .map(::text)
}
