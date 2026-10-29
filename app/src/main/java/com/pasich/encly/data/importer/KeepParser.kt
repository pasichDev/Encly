package com.pasich.encly.data.importer

import com.pasich.encly.editor.codec.ImportBlocks
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.model.BlockType
import com.pasich.encly.editor.model.ItemListBlock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Google Keep (Takeout): one JSON object per note, the `.json` files of `Takeout/Keep` in the ZIP, or loose
 * files. Keep has no stable id, so a note is identified by its file name and creation time (not its
 * folder, so the Takeout ZIP and the loose files give the same note).
 *
 * Brought over: title, text (paragraphs), checklist items with their state (`listContent`),
 * the first label as the tag, trash state, timestamps (microseconds). Counted as losses:
 * attachments, pinned, archived, colors, further labels. HTML duplicates and media files are
 * ignored.
 */
internal object KeepParser {
    private const val CREATED = "createdTimestampUsec"
    private const val EDITED = "userEditedTimestampUsec"
    private const val DEFAULT_COLOR = "DEFAULT"

    fun isNote(element: JsonElement?): JsonObject? {
        val note = ImportText.obj(element) ?: return null
        return note.takeIf { ImportText.long(it[CREATED]) != null && ImportText.long(it[EDITED]) != null }
    }

    fun parse(entries: List<ImportEntry>, limits: ImportLimits): ParsedNotes {
        val context = ParseContext(limits)
        for (entry in entries.filter { it.extension.equals("json", ignoreCase = true) }) {
            val text = context.text(entry)
            val note = text?.let { isNote(ImportText.json(it, limits.maxJsonDepth)) }
            when {
                text == null -> Unit
                note == null -> context.unsupported++
                else -> context.add(note(entry.name, note))
            }
        }
        return context.result()
    }

    private fun note(path: String, json: JsonObject): ImportedNote {
        val created = ImportText.long(json[CREATED])
        val labels = (json["labels"] as? JsonArray).orEmpty()
            .mapNotNull { ImportText.string(ImportText.obj(it)?.get("name")) }
        val tags = ImportText.names(labels)
        val color = ImportText.string(json["color"])
        return ImportedNote(
            sourceKey = "$path|$created",
            title = ImportText.string(json["title"]).orEmpty().trim(),
            blocks = blocks(json),
            tags = tags,
            created = ImportTime.microsToMillis(created),
            edited = ImportTime.microsToMillis(ImportText.long(json[EDITED])),
            trashed = ImportText.bool(json["isTrashed"]) == true,
            losses = ImportLosses(
                attachments = (json["attachments"] as? JsonArray)?.size ?: 0,
                pinned = if (ImportText.bool(json["isPinned"]) == true) 1 else 0,
                archived = if (ImportText.bool(json["isArchived"]) == true) 1 else 0,
                colors = if (color != null && !color.equals(DEFAULT_COLOR, ignoreCase = true)) 1 else 0,
                extraTags = (tags.size - 1).coerceAtLeast(0),
            ),
        )
    }

    private fun blocks(json: JsonObject): List<Block> {
        val blocks = ArrayList<Block>()
        ImportText.string(json["textContent"])?.let { blocks += ImportBlocks.paragraphs(it.replace("\r\n", "\n")) }
        val items = (json["listContent"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = ImportText.obj(element)
            val value = ImportText.string(item?.get("text"))?.trim().orEmpty()
            if (value.isEmpty()) {
                null
            } else {
                ItemListBlock(
                    value,
                    isCheck =
                    ImportText.bool(item?.get("isChecked")) == true,
                )
            }
        }
        if (items.isNotEmpty()) blocks += ImportBlocks.list(BlockType.LIST_CHECK, items)
        return blocks
    }
}
