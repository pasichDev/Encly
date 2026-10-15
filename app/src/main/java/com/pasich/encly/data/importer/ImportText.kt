package com.pasich.encly.data.importer

import com.pasich.encly.dynamicBlocks.hasContent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Small readers shared by the parsers. Nothing here keeps or logs content. */
internal object ImportText {
    private const val BOM_CODE = 0xFEFF

    private val json = Json { isLenient = false }

    /** UTF-8 text of [bytes] without a byte order mark and with `\n` line ends; null if it is not valid UTF-8. */
    fun utf8(bytes: ByteArray): String? = try {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix(Char(BOM_CODE).toString()).replace("\r\n", "\n")
            .replace('\r', '\n')
    } catch (_: CharacterCodingException) {
        null
    }

    /** The JSON of [text], or null if it is malformed or nested deeper than [maxDepth]. */
    fun json(text: String, maxDepth: Int = ImportLimits.MAX_JSON_DEPTH): JsonElement? {
        if (nestingDepth(text) > maxDepth) return null
        return try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** The deepest `[` / `{` nesting in [text], brackets inside strings not counted. */
    private fun nestingDepth(text: String): Int {
        var depth = 0
        var deepest = 0
        var inString = false
        var escaped = false
        for (c in text) {
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

    fun string(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    fun obj(element: JsonElement?): JsonObject? = element as? JsonObject

    fun long(element: JsonElement?): Long? = (element as? JsonPrimitive)?.takeIf { !it.isString }?.contentOrNull
        ?.toLongOrNull()

    fun bool(element: JsonElement?): Boolean? = (element as? JsonPrimitive)?.takeIf { !it.isString }?.contentOrNull
        ?.toBooleanStrictOrNull()

    /** The first line of [text] without surrounding blanks. */
    fun firstLine(text: String): String = text.lineSequence().firstOrNull().orEmpty().trim()

    /** A list of names as distinct, trimmed, non-blank entries in order. */
    fun names(values: List<String>): List<String> = values.map { it.trim().removePrefix("#").trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
}

/** What a parser collects: notes, and the entries it had to leave out. */
internal class ParseContext(private val limits: ImportLimits) {
    private val notes = ArrayList<ImportedNote>()
    var unsupported = 0
    var unreadable = 0
    var empty = 0
    var extraLosses = ImportLosses()

    /** Decodes [entry] as UTF-8; an invalid file is counted as unreadable and skipped. */
    fun text(entry: ImportEntry): String? = ImportText.utf8(entry.bytes).also { if (it == null) unreadable++ }

    fun add(note: ImportedNote) {
        if (note.title.isBlank() && note.blocks.none { it.hasContent() }) {
            empty++
            return
        }
        if (notes.size >= limits.maxNotes) throw ImportException(ImportError.TOO_LARGE)
        notes += note
    }

    fun result() =
        ParsedNotes(notes, ImportSkips(unsupported = unsupported, unreadable = unreadable, empty = empty), extraLosses)
}
