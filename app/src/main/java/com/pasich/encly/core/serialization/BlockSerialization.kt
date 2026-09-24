package com.pasich.encly.core.serialization

import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.reflect.Type

class BlockDeserializer : JsonDeserializer<Block> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Block {
        val jsonObject = json?.asJsonObject ?: throw JsonParseException("Invalid JSON")

        val blockType =
            jsonObject.get("blockType")?.asString ?: throw JsonParseException("Missing blockType")

        return when (blockType) {
            "TEXT" -> Block.TextBlock(
                text = MutableStateFlow(jsonObject.get("text")?.asString ?: ""),
            )

            "H1", "H2", "H3", "H4" -> Block.HBlock(
                text = MutableStateFlow(jsonObject.get("text")?.asString ?: ""),
                blockType = BlockType.valueOf(blockType),
            )

            "QUOTE" -> Block.QuoteBlock(
                text = MutableStateFlow(jsonObject.get("text")?.asString ?: ""),
            )

            "LINK" -> Block.LinkBlock(
                block = MutableStateFlow(
                    LinkDataBlock(
                        url = jsonObject.getAsJsonObject("block")?.get("url")?.asString ?: "",
                        title = jsonObject.getAsJsonObject("block")?.get("title")?.asString ?: "",
                        imageUrl = jsonObject.getAsJsonObject("block")?.get("imageUrl")?.asString
                            ?: "",
                    ),
                ),
            )

            "SEPARATOR" -> Block.SeparatorBlock()

            // "LIST" kept as a legacy alias for checklists.
            "LIST", "LIST_CHECK", "LIST_NUMBER", "LIST_BULLET" -> Block.ListBlock(
                items = MutableStateFlow(
                    jsonObject.getAsJsonArray("items")
                        ?.map { item ->
                            val itemObject = item.asJsonObject
                            ItemListBlock(
                                value = itemObject.get("value")?.asString ?: "",
                                isCheck = itemObject.get("isCheck")?.asBoolean ?: false,
                            )
                        } ?: listOf(ItemListBlock("")),
                ),
                blockType = when (blockType) {
                    "LIST_NUMBER" -> BlockType.LIST_NUMBER
                    "LIST_BULLET" -> BlockType.LIST_BULLET
                    else -> BlockType.LIST_CHECK
                },
            )

            else -> throw JsonParseException("Unknown block type: $blockType")
        }
    }
}

class BlockSerializer : JsonSerializer<Block> {
    override fun serialize(src: Block?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        if (src == null) throw IllegalArgumentException("Block cannot be null")

        val jsonObject = JsonObject()

        when (src) {
            is Block.TextBlock -> {
                jsonObject.addProperty("blockType", "TEXT")
                jsonObject.addProperty("text", src.text.value)
            }

            is Block.HBlock -> {
                // Preserve the heading level (H1..H4) instead of collapsing to H1.
                jsonObject.addProperty("blockType", src.blockType.name)
                jsonObject.addProperty("text", src.text.value)
            }

            is Block.QuoteBlock -> {
                jsonObject.addProperty("blockType", "QUOTE")
                jsonObject.addProperty("text", src.text.value)
            }

            is Block.LinkBlock -> {
                jsonObject.addProperty("blockType", "LINK")
                val linkObject = JsonObject()
                linkObject.addProperty("url", src.block.value.url)
                linkObject.addProperty("title", src.block.value.title)
                linkObject.addProperty("imageUrl", src.block.value.imageUrl)
                jsonObject.add("block", linkObject)
            }

            is Block.SeparatorBlock -> {
                jsonObject.addProperty("blockType", "SEPARATOR")
            }

            is Block.ListBlock -> {
                // Drop empty list items
                val nonEmptyItems = src.items.value.filter { it.value.isNotBlank() }

                // Preserve the list kind (numbered, bulleted or checklist).
                jsonObject.addProperty("blockType", src.blockType.name)
                // Written field by field, not through Gson reflection, so the stored key names
                // do not depend on R8 keeping ItemListBlock's field names.
                val itemsArray = JsonArray()
                nonEmptyItems.forEach { item ->
                    itemsArray.add(
                        JsonObject().apply {
                            addProperty("value", item.value)
                            addProperty("isCheck", item.isCheck)
                        },
                    )
                }
                jsonObject.add("items", itemsArray)
            }
        }

        return jsonObject
    }
}
