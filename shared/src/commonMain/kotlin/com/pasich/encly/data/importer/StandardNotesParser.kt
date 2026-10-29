package com.pasich.encly.data.importer

import com.pasich.encly.editor.codec.ImportBlocks
import com.pasich.encly.editor.codec.InlineHtml
import com.pasich.encly.editor.codec.MarkdownBlockMapper
import com.pasich.encly.editor.model.Block
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Standard Notes, decrypted backup: `{version, items: [...]}` (the ZIP of a decrypted download
 * holds this file plus a plain `Items/` folder, which is ignored).
 *
 * Only `Note` and `Tag` items are read; `SN|*` items are the app's own settings. A tag's
 * `references` give the notes their tag. The text depends on `noteType`: plain text and markdown
 * through [MarkdownBlockMapper] (so `- [x]` lines become checklists), Super (Lexical JSON) by its
 * text nodes ([LexicalBlocks]), rich text without its HTML, every other type as plain paragraphs.
 * Pinned, archived and the client's edit time live in `appData["org.standardnotes.sn"]`. An
 * encrypted backup is never read ([Shape.ENCRYPTED]).
 */
internal object StandardNotesParser {
    const val BACKUP_FILE_PREFIX = "Standard Notes Backup and Import File"
    private const val NOTE = "Note"
    private const val TAG = "Tag"
    private const val APP_DATA = "org.standardnotes.sn"

    enum class Shape { DECRYPTED, ENCRYPTED }

    /** What [json] is if it has the shape of a Standard Notes backup, else null. */
    fun shape(json: JsonElement?): Shape? {
        val root = ImportText.obj(json)
        val items = (root?.get("items") as? JsonArray)?.mapNotNull(ImportText::obj).orEmpty()
        return when {
            root == null || root["items"] !is JsonArray || root["version"] == null -> null

            root["keyParams"] != null || root["auth_params"] != null || items.any(::hasEncryptedContent) ->
                Shape.ENCRYPTED

            items.any { ImportText.string(it["content_type"]) != null } -> Shape.DECRYPTED

            else -> null
        }
    }

    private fun hasEncryptedContent(item: JsonObject): Boolean {
        val type = ImportText.string(item["content_type"])
        return (type == NOTE || type == TAG) && ImportText.string(item["content"]) != null
    }

    fun parse(entries: List<ImportEntry>, limits: ImportLimits): ParsedNotes {
        val context = ParseContext(limits)
        for (entry in entries.filter { it.name.startsWith(BACKUP_FILE_PREFIX) || it.extension == "json" }) {
            val text = context.text(entry)
            val json = text?.let { ImportText.json(it, limits.maxJsonDepth) }
            val root = ImportText.obj(json)
            when {
                text == null -> Unit
                root != null && shape(json) == Shape.DECRYPTED -> readBackup(root, context)
                else -> context.unsupported++
            }
        }
        return context.result()
    }

    private fun readBackup(root: JsonObject, context: ParseContext) {
        val items = (root["items"] as? JsonArray).orEmpty().mapNotNull(ImportText::obj)
        val tagsByNote = tagsByNote(items)
        for (item in items.filter { ImportText.string(it["content_type"]) == NOTE }) {
            val content = ImportText.obj(item["content"])
            val uuid = ImportText.string(item["uuid"])
            if (content != null) {
                // Alphabetical, so the tag Encly keeps does not depend on the order of the file.
                val tags = ImportText.names(tagsByNote[uuid].orEmpty().sortedBy { it.lowercase() })
                context.add(note(uuid, item, content, tags))
            }
        }
    }

    /** Note uuid -> titles of the tags that reference it. */
    private fun tagsByNote(items: List<JsonObject>): Map<String?, List<String>> {
        val result = HashMap<String?, MutableList<String>>()
        for (item in items.filter { ImportText.string(it["content_type"]) == TAG }) {
            val content = ImportText.obj(item["content"])
            val title = ImportText.string(content?.get("title"))?.trim().orEmpty()
            val references = (content?.get("references") as? JsonArray).orEmpty().mapNotNull(ImportText::obj)
            for (ref in references.filter { ImportText.string(it["content_type"]) == NOTE && title.isNotEmpty() }) {
                result.getOrPut(ImportText.string(ref["uuid"])) { ArrayList() } += title
            }
        }
        return result
    }

    /** `content.appData["org.standardnotes.sn"]`, where the app keeps pinned, archived and the edit time. */
    private fun appData(content: JsonObject): JsonObject =
        ImportText.obj(ImportText.obj(content["appData"])?.get(APP_DATA)) ?: JsonObject(emptyMap())

    private fun flag(app: JsonObject, content: JsonObject, key: String): Boolean =
        (ImportText.bool(app[key]) ?: ImportText.bool(content[key])) == true

    private fun note(uuid: String?, item: JsonObject, content: JsonObject, tags: List<String>): ImportedNote {
        val app = appData(content)
        return ImportedNote(
            sourceKey = uuid,
            title = ImportText.string(content["title"]).orEmpty().trim(),
            blocks = blocks(content),
            tags = tags,
            created = ImportTime.parse(ImportText.string(item["created_at"])),
            // An offline export has updated_at = 1970 (rejected by parse()); the edit time is the client's.
            edited = ImportTime.parse(ImportText.string(app["client_updated_at"]))
                ?: ImportTime.parse(ImportText.string(item["updated_at"])),
            trashed = ImportText.bool(content["trashed"]) == true,
            losses = ImportLosses(
                pinned = if (flag(app, content, "pinned")) 1 else 0,
                archived = if (flag(app, content, "archived")) 1 else 0,
                extraTags = (tags.size - 1).coerceAtLeast(0),
            ),
        )
    }

    private fun blocks(content: JsonObject): List<Block> {
        val text = ImportText.string(content["text"]).orEmpty().replace("\r\n", "\n")
        return when (ImportText.string(content["noteType"])) {
            "markdown", "plain-text" -> MarkdownBlockMapper.toBlocks(text)
            "rich-text" -> ImportBlocks.paragraphs(InlineHtml.toPlainText(text))
            "super" -> LexicalBlocks.parse(text) ?: fallback(content, text)
            else -> ImportBlocks.paragraphs(text)
        }
    }

    private fun fallback(content: JsonObject, text: String): List<Block> =
        ImportText.string(content["preview_plain"])?.takeIf { it.isNotBlank() }?.let(ImportBlocks::paragraphs)
            ?: ImportBlocks.paragraphs(text)
}
