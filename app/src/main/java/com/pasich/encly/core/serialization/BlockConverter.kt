package com.pasich.encly.core.serialization

import com.pasich.encly.core.AppLogger
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.hasContent
import kotlinx.serialization.SerializationException

/**
 * Converter for transforming blocks to JSON and back.
 * The format is written and read by [BlockJson].
 */
object BlockConverter {
    /**
     * Converts a list of blocks into a JSON representation. Blocks without content
     * ([hasContent]) are left out, except an empty paragraph between other blocks: it is a blank
     * line the user added on purpose and must survive closing and reopening the note. Blank
     * blocks at the end (the placeholder paragraph a fresh note starts with) are dropped.
     * @param blocks the list of blocks to serialize
     * @return the JSON string
     */
    fun blocksToJson(blocks: List<Block>): String {
        val kept = blocks.dropLastWhile { !it.hasContent() }.filter { it.hasContent() || it is Block.TextBlock }
        return BlockJson.encode(kept)
    }

    /**
     * Converts JSON into a list of blocks.
     * @param json the JSON string to deserialize
     * @return the list of blocks; empty for an empty string
     * @throws SerializationException for malformed JSON or a block this version does not know
     *   (for example one written by a newer version); use [jsonToBlocksOrNull] to get null instead
     */
    fun jsonToBlocks(json: String): List<Block> {
        if (json.isEmpty()) return emptyList()
        return BlockJson.decode(json)
    }

    /**
     * Like [jsonToBlocks], but returns null when [json] cannot be read, so one unreadable note
     * cannot crash a screen that lists or searches many notes.
     */
    fun jsonToBlocksOrNull(json: String): List<Block>? = try {
        jsonToBlocks(json)
    } catch (e: IllegalArgumentException) {
        // SerializationException (malformed JSON, a wrong shape, an unknown block) is one; so are
        // BlockType.valueOf failures.
        logUnreadable(e)
    }

    private fun logUnreadable(e: Exception): List<Block>? {
        AppLogger.w("BlockConverter", "Unreadable note content: ${e.javaClass.simpleName}")
        return null
    }
}
