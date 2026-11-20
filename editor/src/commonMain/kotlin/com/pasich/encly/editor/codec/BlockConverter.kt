package com.pasich.encly.editor.codec

import com.pasich.encly.editor.EditorLogger
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.hasContent
import kotlinx.serialization.SerializationException

/**
 * Converter for transforming blocks to JSON and back.
 * The format is written and read by [BlockJson].
 */
public object BlockConverter {
    /**
     * Converts a list of blocks into a JSON representation. Blocks without content
     * ([hasContent]) are left out, except an empty paragraph between other blocks: it is a blank
     * line the user added on purpose and must survive closing and reopening the note. An empty
     * code block or table is kept wherever it is: the user made it to fill in, its shape is their
     * work. Blank blocks at the end (the placeholder paragraph a fresh note starts with) are dropped.
     * @param blocks the list of blocks to serialize
     * @return the JSON string
     */
    public fun blocksToJson(blocks: List<Block>): String {
        val kept = blocks.dropLastWhile { !it.isStored() }.filter { it.isStored() || it is Block.TextBlock }
        return BlockJson.encode(kept)
    }

    // A code block or table is the user's own making, empty or not.
    private fun Block.isStored(): Boolean = hasContent() || this is Block.CodeBlock || this is Block.TableBlock

    /**
     * Converts JSON into a list of blocks.
     * @param json the JSON string to deserialize
     * @return the list of blocks; empty for an empty string
     * @throws SerializationException for malformed JSON or a block this version does not know
     *   (for example one written by a newer version); use [jsonToBlocksOrNull] to get null instead
     */
    public fun jsonToBlocks(json: String): List<Block> {
        if (json.isEmpty()) return emptyList()
        return BlockJson.decode(json)
    }

    /**
     * Like [jsonToBlocks], but returns null when [json] cannot be read, so one unreadable note
     * cannot crash a screen that lists or searches many notes. The failure is reported to [logger]
     * as a fixed event, never with the note's content.
     */
    public fun jsonToBlocksOrNull(json: String, logger: EditorLogger = EditorLogger.None): List<Block>? = try {
        jsonToBlocks(json)
    } catch (e: IllegalArgumentException) {
        // SerializationException (malformed JSON, a wrong shape, an unknown block) is one; so are
        // BlockType.valueOf failures.
        logger.warn("BlockConverter", "Unreadable note content: ${e::class.simpleName}")
        null
    }
}
