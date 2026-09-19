package com.pasich.encly.core.serialization

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.pasich.encly.dynamicBlocks.Block
import kotlinx.coroutines.flow.MutableStateFlow


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
            .registerTypeAdapter(MutableStateFlow::class.java, MutableStateFlowAdapter())
            .setPrettyPrinting()
            .create()
    }

    /**
     * Converts a list of blocks into a JSON representation.
     * @param blocks the list of blocks to serialize
     * @return the JSON string
     */
    fun blocksToJson(blocks: List<Block>): String {
        val type = object : TypeToken<List<Block>>() {}.type
        return gson.toJson(blocks, type)
    }

    /**
     * Converts JSON into a list of blocks.
     * @param json the JSON string to deserialize
     * @return the list of blocks, or an empty list on error
     */
    fun jsonToBlocks(json: String): List<Block> {
        if (json.isEmpty()) return emptyList()
        val type = object : TypeToken<List<Block>>() {}.type
        return gson.fromJson<List<Block>>(json, type).orEmpty()
    }
}
