package com.pasich.encly.data.importer

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Walks the root object of a JSON file in memory one member (and one array element) at a time, so a
 * parser holds the tree of a single note instead of the tree of the whole export.
 *
 * - Nesting is counted while scanning: a `[` or `{` deeper than [maxDepth] stops the scan at once,
 *   brackets inside strings not counted ([JsonScan.root] then reports a file it cannot use).
 * - Only what a caller asks for is parsed ([element], [forEachElement]), and it is parsed strictly
 *   by kotlinx.serialization. A member that is [skip]ped is checked for balanced brackets and depth
 *   only, so what no parser reads is never built.
 * - Text is not decoded here: callers check [Utf8.isValid] first, and every range handed to the
 *   JSON parser is then valid UTF-8.
 *
 * The scan is one pass over the bytes and never looks back, in line with every other reader of
 * this package; nothing is written anywhere.
 */
@Suppress("TooManyFunctions") // One small step of the scan each; none is useful alone.
internal class JsonCursor(private val bytes: ByteArray, private val maxDepth: Int) {
    /** The bytes are not JSON this scan can use: truncated, out of order, too deep or not parseable. */
    class Malformed : Exception()

    private var pos = if (hasBom()) BOM.size else 0
    private var depth = 0
    private var first = true
    private var pending = false

    private fun hasBom() = bytes.size >= BOM.size && BOM.indices.all { bytes[it] == BOM[it] }

    /** Opens the root object; false if the first thing in the file is not `{`. */
    fun openRoot(): Boolean {
        if (peek() != OPEN_OBJECT) return false
        pos++
        enter()
        return true
    }

    /**
     * The key of the next member of the root object, or null at its end. The caller then reads the
     * value with [element], [scalar], [forEachElement] or [skip]; a value left unread is skipped.
     */
    fun nextKey(): String? {
        if (pending) skip()
        val next = separator()
        return if (next == CLOSE_OBJECT) close() else readKey(next)
    }

    /** Passes the comma between members (none before the first) and returns the next byte. */
    private fun separator(): Int {
        var next = peek()
        if (first) {
            first = false
        } else if (next == COMMA) {
            pos++
            next = peek()
            if (next == CLOSE_OBJECT) fail()
        } else if (next != CLOSE_OBJECT) {
            fail()
        }
        return next
    }

    private fun readKey(next: Int): String {
        if (next != QUOTE) fail()
        val key = key(scanString())
        if (peek() != COLON) fail()
        pos++
        pending = true
        return key
    }

    /** Whether the next value is an array. */
    fun isArray(): Boolean = peek() == OPEN_ARRAY

    /** The next value, parsed. */
    fun element(): JsonElement {
        pending = false
        return parse(scanValue())
    }

    /** The next value if it is a string, number, boolean or null; an object or array is skipped and gives null. */
    fun scalar(): JsonElement? {
        val next = peek()
        return if (next == OPEN_OBJECT || next == OPEN_ARRAY) {
            skip()
            null
        } else {
            element()
        }
    }

    /** Passes over the next value: brackets and depth are checked, nothing is built. */
    fun skip() {
        pending = false
        scanValue()
    }

    /** Parses the elements of the next value, which must be an array, one at a time; [action] keeps what it needs. */
    fun forEachElement(action: (JsonElement) -> Unit) {
        pending = false
        if (peek() != OPEN_ARRAY) fail()
        pos++
        enter()
        if (peek() == CLOSE_ARRAY) {
            close()
            return
        }
        var more = true
        while (more) {
            action(parse(scanValue()))
            more = when (peek()) {
                COMMA -> {
                    pos++
                    true
                }

                CLOSE_ARRAY -> false

                else -> fail()
            }
        }
        close()
    }

    /** Nothing but blanks may follow the root object. */
    fun end() {
        if (peek() != END) fail()
    }

    private fun close(): String? {
        pos++
        depth--
        return null
    }

    private fun enter() {
        if (++depth > maxDepth) fail()
    }

    private fun fail(): Nothing = throw Malformed()

    /** The next byte that is not JSON white space, or [END]. Bytes of a multi-byte character are negative. */
    private fun peek(): Int {
        while (pos < bytes.size && isBlank(bytes[pos].toInt())) pos++
        return if (pos < bytes.size) bytes[pos].toInt() else END
    }

    /** Moves over the value at the cursor and returns where it started. */
    private fun scanValue(): Int {
        val next = peek()
        val start = pos
        when (next) {
            QUOTE -> scanString()
            OPEN_OBJECT, OPEN_ARRAY -> scanContainer()
            END, COMMA, COLON, CLOSE_OBJECT, CLOSE_ARRAY -> fail()
            else -> scanToken()
        }
        return start
    }

    /** A bare value: `true`, `false`, `null` or a number, as RFC 8259 has them and nothing else. */
    private fun scanToken() {
        val start = pos
        while (pos < bytes.size && !isDelimiter(bytes[pos].toInt())) pos++
        if (!TOKEN.matches(bytes.decodeToString(start, pos))) fail()
    }

    /** From the opening quote to just after the closing one; returns the position of the opening quote. */
    private fun scanString(): Int {
        val start = pos
        pos++
        while (pos < bytes.size) {
            when (bytes[pos++].toInt()) {
                BACKSLASH -> pos++
                QUOTE -> return start
            }
        }
        fail()
    }

    /** From an opening bracket to just after its match; nesting is counted against [maxDepth] on the way. */
    private fun scanContainer() {
        val base = depth
        do {
            when (bytes[pos].toInt()) {
                QUOTE -> {
                    scanString()
                    continue
                }

                OPEN_OBJECT, OPEN_ARRAY -> {
                    pos++
                    enter()
                }

                CLOSE_OBJECT, CLOSE_ARRAY -> {
                    pos++
                    depth--
                }

                else -> if (isDelimiter(bytes[pos].toInt())) pos++ else scanToken()
            }
        } while (depth > base && pos < bytes.size)
        if (depth != base) fail()
    }

    private fun isBlank(b: Int) = b == SPACE || b == TAB || b == LF || b == CR

    private fun isDelimiter(b: Int) = isBlank(b) || b == COMMA || b == CLOSE_OBJECT || b == CLOSE_ARRAY || b == COLON

    private fun parse(start: Int): JsonElement = try {
        JSON.parseToJsonElement(bytes.decodeToString(start, pos))
    } catch (_: SerializationException) {
        fail()
    } catch (_: IllegalArgumentException) {
        fail()
    }

    /** The key at [quote], with escapes (`items`) resolved, as a full parse would give it. */
    private fun key(quote: Int): String {
        val raw = bytes.decodeToString(quote + 1, pos - 1)
        return if ('\\' !in raw) {
            raw
        } else {
            (parse(quote) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fail()
        }
    }

    private companion object {
        val JSON = Json { isLenient = false }
        val TOKEN = Regex("""true|false|null|-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?""")
        val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        const val END = -1
        const val SPACE = 0x20
        const val TAB = 0x09
        const val LF = 0x0A
        const val CR = 0x0D
        const val QUOTE = '"'.code
        const val BACKSLASH = '\\'.code
        const val COMMA = ','.code
        const val COLON = ':'.code
        const val OPEN_OBJECT = '{'.code
        const val CLOSE_OBJECT = '}'.code
        const val OPEN_ARRAY = '['.code
        const val CLOSE_ARRAY = ']'.code
    }
}

/** What the detector needs from the root object of a JSON file. */
internal class RootInfo(
    /** Which of `items`, `hash_type`, `compressed` and `encrypted` are keys of the root. */
    val keys: Set<String>,
    /** The numeric values of Google Keep's two timestamps, or null if a key is missing or not a number. */
    val created: Long?,
    val edited: Long?,
)

internal object JsonScan {
    private val WATCHED = setOf("items", "hash_type", "compressed", "encrypted")

    /**
     * Calls [visit] for each member of the root object of [bytes] (UTF-8, already checked). It must
     * read or skip the member's value through the cursor. False if [bytes] is not a JSON object the
     * scan can follow to its end, or nests deeper than [maxDepth]: nothing it visited counts then.
     */
    fun root(bytes: ByteArray, maxDepth: Int, visit: JsonCursor.(key: String) -> Unit): Boolean = try {
        val cursor = JsonCursor(bytes, maxDepth)
        val isObject = cursor.openRoot()
        if (isObject) {
            while (true) cursor.visit(cursor.nextKey() ?: break)
            cursor.end()
        }
        isObject
    } catch (_: JsonCursor.Malformed) {
        false
    }

    /** The facts [FormatDetector] needs about a JSON file, or null if it is not a JSON object. */
    fun info(bytes: ByteArray, maxDepth: Int): RootInfo? {
        val keys = HashSet<String>()
        var created: Long? = null
        var edited: Long? = null
        val ok = root(bytes, maxDepth) { key ->
            when (key) {
                KeepParser.CREATED -> created = ImportText.long(scalar())

                KeepParser.EDITED -> edited = ImportText.long(scalar())

                else -> {
                    if (key in WATCHED) keys += key
                    skip()
                }
            }
        }
        return if (ok) RootInfo(keys, created, edited) else null
    }
}
