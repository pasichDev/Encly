package com.pasich.encly.editor.codec

import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import com.pasich.encly.editor.model.LinkDataBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Reads and writes the JSON of a note's blocks. The stored format is fixed by notes already on
 * users' devices and in backups, so it is written key by key here, not derived from the model
 * classes: `blockType` first, then the fields of that kind, with two-space indentation and the
 * HTML-sensitive characters escaped (see [escapeHtmlSensitive]), exactly as Gson wrote it.
 */
internal object BlockJson {
    private const val HEX = 16
    private const val UNICODE_ESCAPE_DIGITS = 4

    // Lenient, as the Gson reader was: unquoted keys, single quotes and the like still load.
    private val reader = Json { isLenient = true }
    private val writer = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun encode(blocks: List<Block>): String =
        writer.encodeToString(JsonElement.serializer(), JsonArray(blocks.map(::toJson))).escapeHtmlSensitive()

    /** Blocks of [json]; `null` entries are dropped and `null` itself is an empty note. */
    fun decode(json: String): List<Block> = when (val root = reader.parseToJsonElement(json)) {
        is JsonNull -> emptyList()
        is JsonArray -> root.filter { it !is JsonNull }.map(::fromJson)
        else -> throw SerializationException("Expected an array of blocks")
    }

    private fun toJson(block: Block): JsonObject = buildJsonObject {
        when (block) {
            is Block.TextBlock -> {
                put("blockType", "TEXT")
                put("text", block.text.value)
            }

            is Block.HBlock -> {
                // Preserve the heading level (H1..H4) instead of collapsing to H1.
                put("blockType", block.blockType.name)
                put("text", block.text.value)
            }

            is Block.QuoteBlock -> {
                put("blockType", "QUOTE")
                put("text", block.text.value)
            }

            is Block.LinkBlock -> {
                put("blockType", "LINK")
                put(
                    "block",
                    buildJsonObject {
                        put("url", block.block.value.url)
                        put("title", block.block.value.title)
                        put("imageUrl", block.block.value.imageUrl)
                    },
                )
            }

            is Block.SeparatorBlock -> put("blockType", "SEPARATOR")

            is Block.ListBlock -> {
                // Preserve the list kind (numbered, bulleted or checklist).
                put("blockType", block.blockType.name)
                // Empty list items are dropped.
                put(
                    "items",
                    JsonArray(
                        block.items.value.filter { it.value.isNotBlank() }.map { item ->
                            buildJsonObject {
                                put("value", item.value)
                                put("isCheck", item.isCheck)
                            }
                        },
                    ),
                )
                // Written only when set, so a note without a locked list is stored as before. An older
                // app reads the other keys and ignores this one: it opens the list unlocked and drops
                // the lock the next time it saves the note.
                if (block.locked.value) put("isLocked", true)
            }
        }
    }

    private fun fromJson(element: JsonElement): Block {
        val json = element.asObject()
        val blockType = json.string("blockType") ?: throw SerializationException("Missing blockType")

        return when (blockType) {
            "TEXT" -> Block.TextBlock(text = MutableStateFlow(json.string("text").orEmpty()))

            "H1", "H2", "H3", "H4" -> Block.HBlock(
                text = MutableStateFlow(json.string("text").orEmpty()),
                blockType = BlockType.valueOf(blockType),
            )

            "QUOTE" -> Block.QuoteBlock(text = MutableStateFlow(json.string("text").orEmpty()))

            "LINK" -> {
                val link = json.obj("block")
                Block.LinkBlock(
                    block = MutableStateFlow(
                        LinkDataBlock(
                            url = link?.string("url").orEmpty(),
                            title = link?.string("title").orEmpty(),
                            imageUrl = link?.string("imageUrl").orEmpty(),
                        ),
                    ),
                )
            }

            "SEPARATOR" -> Block.SeparatorBlock()

            // "LIST" kept as a legacy alias for checklists.
            "LIST", "LIST_CHECK", "LIST_NUMBER", "LIST_BULLET" -> Block.ListBlock(
                items = MutableStateFlow(
                    json.array("items")?.map { item ->
                        val itemObject = item.asObject()
                        ItemListBlock(
                            value = itemObject.string("value").orEmpty(),
                            isCheck = itemObject.string("isCheck").toBoolean(),
                        )
                    } ?: listOf(ItemListBlock("")),
                ),
                blockType = when (blockType) {
                    "LIST_NUMBER" -> BlockType.LIST_NUMBER
                    "LIST_BULLET" -> BlockType.LIST_BULLET
                    else -> BlockType.LIST_CHECK
                },
                // Optional, absent in notes written before checklists could be locked.
                locked = MutableStateFlow(json.string("isLocked").toBoolean()),
            )

            else -> throw SerializationException("Unknown block type: $blockType")
        }
    }

    private fun JsonElement.asObject(): JsonObject =
        this as? JsonObject ?: throw SerializationException("Expected an object")

    /** The member [key] as an object; absent is `null`, any other kind of value is unreadable. */
    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.asObject()

    private fun JsonObject.array(key: String): JsonArray? =
        get(key)?.let { it as? JsonArray ?: throw SerializationException("Expected an array at $key") }

    /** The text of a string, number or boolean member; absent is `null`, `null` or a container is unreadable. */
    private fun JsonObject.string(key: String): String? = get(key)?.let { value ->
        if (value is JsonPrimitive && value !is JsonNull) {
            value.content
        } else {
            throw SerializationException("Expected a plain value at $key")
        }
    }

    /**
     * Gson wrote `<`, `>`, `&`, `=` and `'` as `\u003c` and so on (so a note could sit inside HTML),
     * and the line separators U+2028/U+2029 too. Those characters occur only inside string
     * values here, so replacing them in the finished text is safe. Readers treat both forms alike.
     */
    private fun String.escapeHtmlSensitive(): String = buildString(length) {
        for (c in this@escapeHtmlSensitive) {
            when (c) {
                '<', '>', '&', '=', '\'', '\u2028', '\u2029' ->
                    append("\\u").append(c.code.toString(HEX).padStart(UNICODE_ESCAPE_DIGITS, '0'))

                else -> append(c)
            }
        }
    }
}
