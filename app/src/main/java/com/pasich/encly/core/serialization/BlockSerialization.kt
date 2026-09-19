package com.pasich.encly.core.serialization

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
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
    override fun deserialize(
        json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?
    ): Block {
        val jsonObject = json?.asJsonObject ?: throw JsonParseException("Invalid JSON")

        val blockType =
            jsonObject.get("blockType")?.asString ?: throw JsonParseException("Missing blockType")

        return when (blockType) {
            "TEXT" -> Block.TextBlock(
                text = MutableStateFlow(jsonObject.get("text")?.asString ?: "")
            )

            "H1", "H2", "H3", "H4" -> Block.HBlock(
                text = MutableStateFlow(jsonObject.get("text")?.asString ?: ""),
                blockType = BlockType.valueOf(blockType)
            )

            "QUOTE" -> Block.QuoteBlock(
                text = MutableStateFlow(jsonObject.get("text")?.asString ?: "")
            )

            "LINK" -> Block.LinkBlock(
                block = MutableStateFlow(
                    LinkDataBlock(
                        url = jsonObject.getAsJsonObject("block")?.get("url")?.asString ?: "",
                        title = jsonObject.getAsJsonObject("block")?.get("title")?.asString ?: "",
                        imageUrl = jsonObject.getAsJsonObject("block")?.get("imageUrl")?.asString
                            ?: ""
                    )
                )
            )

            "SEPARATOR" -> Block.SeparatorBlock()
            // "LIST" kept as a legacy alias for checklists.
            "LIST", "LIST_CHECK", "LIST_NUMBER" -> Block.ListBlock(
                items = MutableStateFlow(
                    jsonObject.getAsJsonArray("items")
                        ?.map { item ->
                            val itemObject = item.asJsonObject
                            ItemListBlock(
                                value = itemObject.get("value")?.asString ?: "",
                                isCheck = itemObject.get("isCheck")?.asBoolean ?: false,
                            )
                        } ?: listOf(ItemListBlock(""))),
                blockType = if (blockType == "LIST_NUMBER") BlockType.LIST_NUMBER else BlockType.LIST_CHECK)

            else -> throw JsonParseException("Unknown block type: $blockType")
        }
    }
}

class BlockSerializer : JsonSerializer<Block> {
    override fun serialize(
        src: Block?, typeOfSrc: Type?, context: JsonSerializationContext?
    ): JsonElement {
        if (src == null) throw IllegalArgumentException("Block cannot be null")

        val jsonObject = JsonObject()

        when (src) {
            is Block.TextBlock -> {
                if (src.text.value.isBlank()) return JsonNull.INSTANCE
                jsonObject.addProperty("blockType", "TEXT")
                jsonObject.addProperty("text", src.text.value)
            }

            is Block.HBlock -> {
                if (src.text.value.isBlank()) return JsonNull.INSTANCE
                // Preserve the heading level (H1..H4) instead of collapsing to H1.
                jsonObject.addProperty("blockType", src.blockType.name)
                jsonObject.addProperty("text", src.text.value)
            }

            is Block.QuoteBlock -> {
                if (src.text.value.isBlank()) return JsonNull.INSTANCE
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
                if (nonEmptyItems.isEmpty()) return JsonNull.INSTANCE

                // Preserve the list kind (numbered vs checklist).
                jsonObject.addProperty("blockType", src.blockType.name)
                val itemsArray = context?.serialize(nonEmptyItems)
                jsonObject.add("items", itemsArray)
            }
        }

        return jsonObject
    }
}
