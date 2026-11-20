package com.pasich.encly.editor.codec

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.TableData
import kotlinx.coroutines.flow.MutableStateFlow

/** Builders for the Encly blocks the parsers produce. */
public object ImportBlocks {
    private val PARAGRAPH_BREAK = Regex("""\n[ \t]*\n\s*""")
    private val HEADINGS = listOf(BlockType.H1, BlockType.H2, BlockType.H3, BlockType.H4)

    /** A paragraph (TEXT block) with [value] as its text. */
    public fun text(value: String): Block = Block.TextBlock(text = MutableStateFlow(value))

    /** [level] 1-4; deeper levels are H4. */
    public fun heading(value: String, level: Int): Block = Block.HBlock(
        text = MutableStateFlow(value),
        blockType = HEADINGS[level.coerceIn(1, HEADINGS.size) - 1],
    )

    /** A quote block with [value] as its text. */
    public fun quote(value: String): Block = Block.QuoteBlock(text = MutableStateFlow(value))

    /** A list block of [type] (bulleted, numbered or checklist) holding [items]. */
    public fun list(type: BlockType, items: List<ItemListBlock>): Block =
        Block.ListBlock(items = MutableStateFlow(items), blockType = type)

    /** A code block with [value] as its text, spaces and line breaks exactly as given. */
    public fun code(value: String): Block = Block.CodeBlock(text = MutableStateFlow(value))

    /** A table of [grid] (rows of cells; short rows are padded), its first row a header when [header]. */
    public fun table(grid: List<List<String>>, header: Boolean): Block =
        Block.TableBlock(table = MutableStateFlow(TableData.of(grid, header)))

    /** Blank-line separated paragraphs as TEXT blocks; line breaks stay inside their paragraph. */
    public fun paragraphs(text: String): List<Block> = text.trim().split(PARAGRAPH_BREAK)
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .map(::text)
}
