package com.pasich.encly.data.importer

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * A Markdown file split into its front matter (a safe subset of YAML) and its body. [listKeys] are the keys written
 * as a list (`[a, b]` or `- item` lines), whose items are taken whole.
 */
class MarkdownDocument(
    private val meta: Map<String, List<String>>,
    val body: String,
    private val listKeys: Set<String> = emptySet(),
) {
    val hasFrontmatter: Boolean get() = meta.isNotEmpty()

    fun has(key: String): Boolean = key in meta

    /** The first value of [key], or null. */
    fun value(key: String): String? = meta[key]?.firstOrNull()?.takeIf { it.isNotBlank() }

    /** The first of [keys] that has a value. */
    fun firstValue(vararg keys: String): String? = keys.firstNotNullOfOrNull(::value)

    /** All values of [key]; a comma separated scalar counts as a list, an item of a real list stays whole. */
    fun list(key: String): List<String> = if (key in listKeys) {
        meta[key].orEmpty()
    } else {
        meta[key].orEmpty().flatMap { it.split(',') }
    }

    fun flag(key: String): Boolean = value(key)?.lowercase() in TRUE_VALUES

    private companion object {
        val TRUE_VALUES = setOf("true", "yes", "1")
    }
}

/**
 * Front matter read by hand, with no YAML library and nothing executed: `key: value` lines, a
 * `[a, b]` inline list or `- item` lines under a key, quoted values unquoted. Anything else
 * (nested maps, block scalars, anchors, tags) is ignored. Without a closing `---` within
 * [MAX_LINES] lines the file has no front matter and is all body; nor has a block with no
 * `key:` line in it (a horizontal rule, some text, another rule).
 */
object Frontmatter {
    private const val MAX_LINES = 200
    private val KEY_VALUE = Regex("""^([A-Za-z0-9_][A-Za-z0-9_ .-]*):(?:[ \t]+(.*))?$""")
    private val LIST_ITEM = Regex("""^[ \t]*-[ \t]+(.*)$""")
    private val json = Json { isLenient = false }

    fun split(text: String): MarkdownDocument {
        val lines = text.lines()
        val end = if (lines.firstOrNull()?.trimEnd() == "---") {
            (1 until minOf(lines.size, MAX_LINES)).firstOrNull {
                lines[it].trimEnd() == "---" || lines[it].trimEnd() == "..."
            }
        } else {
            null
        }
        return if (end == null || !isFrontmatter(lines.subList(1, end))) {
            MarkdownDocument(emptyMap(), text)
        } else {
            val listKeys = HashSet<String>()
            MarkdownDocument(parse(lines.subList(1, end), listKeys), lines.drop(end + 1).joinToString("\n"), listKeys)
        }
    }

    /** A block is front matter if it is empty or has a `key:` line (list lines only mean something under one). */
    private fun isFrontmatter(block: List<String>): Boolean =
        block.all { it.isBlank() } || block.any { KEY_VALUE.matches(it) && !it.first().isWhitespace() }

    /** The values by key; the keys written as a list go to [listKeys]. */
    private fun parse(lines: List<String>, listKeys: MutableSet<String>): Map<String, List<String>> {
        val meta = LinkedHashMap<String, MutableList<String>>()
        var current: String? = null
        for (line in lines) {
            val pair = KEY_VALUE.matchEntire(line)?.takeUnless { line.first().isWhitespace() }
            if (pair != null) {
                current = pair.groupValues[1].trim().lowercase()
                val raw = pair.groupValues[2].trim()
                if (raw.isInlineList()) listKeys += current
                meta.getOrPut(current) { ArrayList() } += scalars(raw)
            } else {
                val item = LIST_ITEM.matchEntire(line)
                if (item != null && current != null) {
                    listKeys += current
                    meta.getValue(current) += unquote(item.groupValues[1].trim())
                }
            }
        }
        return meta.filterValues { values -> values.any { it.isNotBlank() } }
    }

    /** The values on the `key:` line itself: nothing for a block scalar, several for `[a, b]`. */
    private fun scalars(raw: String): List<String> = when {
        raw.isInlineList() -> items(raw.removeSurrounding("[", "]")).map { unquote(it.trim()) }
        raw.isNotEmpty() && raw != "|" && raw != ">" -> listOf(unquote(raw))
        else -> emptyList()
    }

    private fun String.isInlineList() = startsWith("[") && endsWith("]")

    /** The items of an inline list split at its commas, but not at a comma inside a double-quoted item. */
    private fun items(raw: String): List<String> {
        val items = ArrayList<String>()
        val item = StringBuilder()
        var quoted = false
        var escaped = false
        for (c in raw) {
            when {
                escaped -> escaped = false

                quoted && c == '\\' -> escaped = true

                c == '"' -> quoted = !quoted

                c == ',' && !quoted -> {
                    items += item.toString()
                    item.clear()
                    continue
                }
            }
            item.append(c)
        }
        items += item.toString()
        return items
    }

    /** A JSON-quoted string is decoded (Notesnook writes titles that way); a single-quoted one is unwrapped. */
    private fun unquote(raw: String): String = when {
        raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"") -> try {
            (json.parseToJsonElement(raw) as? JsonPrimitive)?.content ?: raw.drop(1).dropLast(1)
        } catch (_: SerializationException) {
            raw.drop(1).dropLast(1)
        }

        raw.length >= 2 && raw.startsWith("'") && raw.endsWith("'") -> raw.drop(1).dropLast(1).replace("''", "'")

        else -> raw
    }
}
