package com.pasich.encly.data.handoff

import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * A My Notes note's content as Encly blocks, serialized the way the notes table stores them.
 *
 * - Plain note (no `valueJson`): one TEXT block per paragraph, paragraphs being separated by a
 *   blank line; single line breaks stay inside their paragraph.
 * - Editor.js: `paragraph` -> TEXT; `header` / `Headers` level 1-4 -> H1-H4 (5 and 6 -> H4,
 *   missing -> H2, the tool's default); `list` -> LIST_BULLET, LIST_NUMBER or LIST_CHECK by its
 *   style, nested items flattened in reading order; `checklist` -> LIST_CHECK; `delimiter` ->
 *   SEPARATOR; `spacer` dropped; `image` and `attaches` dropped (the files stay in My Notes and
 *   the hand-off counts them). Any other tool with a `data.text` keeps that text as TEXT.
 * - Inline HTML becomes plain text ([InlineHtml]).
 *
 * `valueJson` that is not a readable Editor.js document falls back to the plain text, so a
 * damaged note still comes over as text instead of failing the whole hand-off.
 */
object MyNotesBlockMapper {
    private val PARAGRAPH_BREAK = Regex("""\r?\n[ \t]*\r?\n\s*""")
    private val HEADINGS = listOf(BlockType.H1, BlockType.H2, BlockType.H3, BlockType.H4)
    private const val DEFAULT_HEADING = 2

    fun toBlocksJson(note: HandoffNote): String = BlockConverter.blocksToJson(toBlocks(note))

    fun toBlocks(note: HandoffNote): List<Block> {
        val editorBlocks = note.valueJson?.takeIf { it.isNotBlank() }?.let(EditorJson::blocks)
        return editorBlocks?.mapNotNull(::toBlock) ?: plainBlocks(note.value)
    }

    private fun plainBlocks(value: String): List<Block> = value.split(PARAGRAPH_BREAK)
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .map(::text)

    private fun toBlock(block: JsonObject): Block? {
        val data = block["data"] as? JsonObject ?: JsonObject(emptyMap())
        return when (EditorJson.string(block["type"])) {
            "paragraph" -> textOf(data)?.let(::text)
            "header", "Headers" -> textOf(data)?.let { heading(it, (data["level"] as? JsonPrimitive)?.intOrNull) }
            "list" -> EditorJsLists.block(data, EditorJsLists.type(EditorJson.string(data["style"])))
            "checklist" -> EditorJsLists.block(data, BlockType.LIST_CHECK)
            "delimiter" -> Block.SeparatorBlock()
            "spacer", "image", "attaches" -> null
            else -> textOf(data)?.let(::text)
        }
    }

    /** Levels 5 and 6 become H4; a missing level is the tool's default, 2. */
    private fun heading(text: String, level: Int?): Block {
        val type = HEADINGS[(level ?: DEFAULT_HEADING).coerceIn(1, HEADINGS.size) - 1]
        return Block.HBlock(text = MutableStateFlow(text), blockType = type)
    }

    private fun text(value: String): Block = Block.TextBlock(text = MutableStateFlow(value))

    /** The block's `data.text` as plain text, or null when it has none. */
    private fun textOf(data: JsonObject): String? =
        EditorJson.string(data["text"])?.let(InlineHtml::toPlainText)?.trim()?.takeIf { it.isNotBlank() }
}
