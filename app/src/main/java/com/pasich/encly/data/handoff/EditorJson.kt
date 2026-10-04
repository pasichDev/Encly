package com.pasich.encly.data.handoff

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** Reading an Editor.js document defensively: any shape it does not expect is "nothing". */
internal object EditorJson {
    private val json = Json { isLenient = false }

    /** Far deeper than any document the editor writes (a nested list adds two levels per level). */
    private const val MAX_JSON_DEPTH = 128

    /** The block array of an Editor.js document (a bare array, or an object with `blocks`). */
    fun blocks(valueJson: String): List<JsonObject>? {
        // The JSON tree parser recurses per level: a hostile document could overflow the stack.
        val root = if (nestingDepth(valueJson) > MAX_JSON_DEPTH) null else parse(valueJson)
        val blocks = when (root) {
            is JsonArray -> root
            is JsonObject -> root["blocks"] as? JsonArray
            else -> null
        }
        return blocks?.filterIsInstance<JsonObject>()
    }

    fun string(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    fun boolean(element: JsonElement?): Boolean? = (element as? JsonPrimitive)?.booleanOrNull

    private fun parse(valueJson: String): JsonElement? = try {
        json.parseToJsonElement(valueJson)
    } catch (_: SerializationException) {
        null
    }

    /** The deepest `[` / `{` nesting in [text], brackets inside strings not counted. */
    private fun nestingDepth(text: String): Int {
        var depth = 0
        var deepest = 0
        var inString = false
        var escaped = false
        text.forEach { c ->
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '[' || c == '{' -> deepest = maxOf(deepest, ++depth)
                c == ']' || c == '}' -> depth--
            }
        }
        return deepest
    }
}

/** Editor.js `list` (1.x strings, 2.x items with nesting and checklist style) and `checklist`. */
internal object EditorJsLists {
    private const val MAX_LIST_DEPTH = 32

    fun type(style: String?): BlockType = when (style) {
        "ordered" -> BlockType.LIST_NUMBER
        "checklist" -> BlockType.LIST_CHECK
        else -> BlockType.LIST_BULLET
    }

    /** The list block for [data], or null when it has no non-blank item. */
    fun block(data: JsonObject, type: BlockType): Block? {
        val items = mutableListOf<ItemListBlock>()
        flattenItems(data["items"] as? JsonArray, items, depth = 0)
        if (items.isEmpty()) return null
        val kept = if (type == BlockType.LIST_CHECK) items else items.map { it.copy(isCheck = false) }
        return Block.ListBlock(items = MutableStateFlow(kept), blockType = type)
    }

    /**
     * List items in reading order, nested ones after their parent. An item is a string (the
     * older list tool), `{content, meta: {checked}, items}` (list 2.x) or `{text, checked}`
     * (the checklist tool). Nesting deeper than [MAX_LIST_DEPTH] is dropped.
     */
    private fun flattenItems(items: JsonArray?, into: MutableList<ItemListBlock>, depth: Int) {
        if (depth > MAX_LIST_DEPTH) return
        items?.forEach { item ->
            when (item) {
                is JsonPrimitive -> addItem(into, EditorJson.string(item), checked = false)

                is JsonObject -> {
                    val meta = item["meta"] as? JsonObject
                    val checked = EditorJson.boolean(item["checked"]) ?: EditorJson.boolean(meta?.get("checked"))
                    val html = EditorJson.string(item["content"]) ?: EditorJson.string(item["text"])
                    addItem(into, html, checked == true)
                    flattenItems(item["items"] as? JsonArray, into, depth + 1)
                }

                else -> Unit
            }
        }
    }

    private fun addItem(into: MutableList<ItemListBlock>, html: String?, checked: Boolean) {
        val value = html?.let(InlineHtml::toPlainText)?.trim()
        if (!value.isNullOrBlank()) into += ItemListBlock(value = value, isCheck = checked)
    }
}
