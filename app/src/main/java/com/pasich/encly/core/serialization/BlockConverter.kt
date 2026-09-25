package com.pasich.encly.core.serialization

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.pasich.encly.core.AppLogger
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.hasContent

/**
 * Converter for transforming blocks to JSON and back.
 * Uses custom serializers and deserializers.
 */
object BlockConverter {
    // Create a Gson instance with custom serialization adapters
    private val gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(Block::class.java, BlockSerializer())
            .registerTypeAdapter(Block::class.java, BlockDeserializer())
            .setPrettyPrinting()
            .create()
    }

    /**
     * Converts a list of blocks into a JSON representation. Blocks without content
     * ([hasContent]) are left out.
     * @param blocks the list of blocks to serialize
     * @return the JSON string
     */
    fun blocksToJson(blocks: List<Block>): String {
        val type = object : TypeToken<List<Block>>() {}.type
        return gson.toJson(blocks.filter { it.hasContent() }, type)
    }

    /**
     * Converts JSON into a list of blocks.
     * @param json the JSON string to deserialize
     * @return the list of blocks; empty for an empty string
     * @throws JsonParseException for malformed JSON or a block this version does not know
     *   (for example one written by a newer version); use [jsonToBlocksOrNull] to get null instead
     */
    fun jsonToBlocks(json: String): List<Block> {
        if (json.isEmpty()) return emptyList()
        val type = object : TypeToken<List<Block>>() {}.type
        // `null` array entries deserialize to null elements; drop them.
        return gson.fromJson<List<Block>>(json, type).orEmpty().filterIsInstance<Block>()
    }

    /**
     * Like [jsonToBlocks], but returns null when [json] cannot be read, so one unreadable note
     * cannot crash a screen that lists or searches many notes.
     */
    fun jsonToBlocksOrNull(json: String): List<Block>? = try {
        jsonToBlocks(json)
    } catch (e: JsonParseException) {
        logUnreadable(e)
    } catch (e: IllegalStateException) {
        // Gson's JsonElement.getAsJsonObject/getAsString on the wrong element type
        logUnreadable(e)
    } catch (e: UnsupportedOperationException) {
        logUnreadable(e)
    } catch (e: IllegalArgumentException) {
        // BlockType.valueOf, number parsing
        logUnreadable(e)
    } catch (e: ClassCastException) {
        logUnreadable(e)
    }

    private fun logUnreadable(e: Exception): List<Block>? {
        AppLogger.w("BlockConverter", "Unreadable note content: ${e.javaClass.simpleName}")
        return null
    }
}
