package com.pasich.encly.data.importer

import com.pasich.encly.domain.model.ItemListBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * A Standard Notes Super note: Lexical JSON read for its text, in order. Paragraphs, headings,
 * quotes, rules and (flattened) lists become blocks; any other node contributes its text.
 */
internal class LexicalBlocks private constructor() {
    private val blocks = ArrayList<Block>()

    private fun add(text: String, build: (String) -> Block) {
        if (text.isNotBlank()) blocks += build(text)
    }

    private fun block(node: JsonObject, depth: Int) {
        if (depth > MAX_DEPTH) return
        when (ImportText.string(node["type"])) {
            "paragraph" -> add(inline(node), ImportBlocks::text)
            "heading" -> add(inline(node)) { ImportBlocks.heading(it, headingLevel(node)) }
            "quote" -> add(inline(node), ImportBlocks::quote)
            "horizontalrule" -> blocks += Block.SeparatorBlock()
            "list" -> list(node, depth)
            else -> other(node, depth)
        }
    }

    private fun other(node: JsonObject, depth: Int) {
        val children = children(node)
        if (children.isEmpty()) {
            add(ImportText.string(node["text"]).orEmpty(), ImportBlocks::text)
        } else {
            children.forEach { block(it, depth + 1) }
        }
    }

    private fun headingLevel(node: JsonObject): Int =
        ImportText.string(node["tag"])?.removePrefix("h")?.toIntOrNull() ?: DEFAULT_HEADING

    /** Nested lists are flattened in reading order. */
    private fun list(node: JsonObject, depth: Int) {
        val type = when (ImportText.string(node["listType"])) {
            "number" -> BlockType.LIST_NUMBER
            "check" -> BlockType.LIST_CHECK
            else -> BlockType.LIST_BULLET
        }
        val items = ArrayList<ItemListBlock>()
        collect(node, items, depth)
        if (items.isNotEmpty()) blocks += ImportBlocks.list(type, items)
    }

    private fun collect(list: JsonObject, into: MutableList<ItemListBlock>, depth: Int) {
        if (depth > MAX_DEPTH) return
        for (item in children(list)) {
            val value = inline(item, skipLists = true).trim()
            if (value.isNotEmpty()) into += ItemListBlock(value, isCheck = ImportText.bool(item["checked"]) == true)
            children(item).filter { ImportText.string(it["type"]) == "list" }.forEach { collect(it, into, depth + 1) }
        }
    }

    private fun inline(node: JsonObject, skipLists: Boolean = false): String = buildString {
        fun walk(current: JsonObject, depth: Int) {
            val skipped = depth > MAX_DEPTH
            when (ImportText.string(current["type"])) {
                "text" -> append(ImportText.string(current["text"]).orEmpty())
                "linebreak" -> append('\n')
                "list" -> if (!skipLists && !skipped) children(current).forEach { walk(it, depth + 1) }
                else -> if (!skipped) children(current).forEach { walk(it, depth + 1) }
            }
        }
        children(node).forEach { walk(it, 0) }
    }

    private fun children(node: JsonObject): List<JsonObject> =
        (node["children"] as? JsonArray).orEmpty().mapNotNull(ImportText::obj)

    companion object {
        private const val MAX_DEPTH = 40
        private const val DEFAULT_HEADING = 2

        /** [text] as blocks, or null if it is not a Lexical document. */
        fun parse(text: String): List<Block>? {
            val root = ImportText.obj(ImportText.json(text))?.get("root")?.let(ImportText::obj)
            return root?.takeIf { ImportText.string(it["type"]) == "root" }?.let { document ->
                LexicalBlocks().also { lexical -> lexical.children(document).forEach { lexical.block(it, 0) } }.blocks
            }
        }
    }
}
