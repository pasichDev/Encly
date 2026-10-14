package com.pasich.encly.data.importer

/**
 * Joplin RAW / JEX: one `<id>.md` per item, `title`, a blank line, the body, a blank line, then
 * `key: value` metadata ending with `type_`. Types: 1 note, 2 notebook, 4 resource, 5 tag,
 * 6 note-tag link. Joplin's own *Markdown* exports have no metadata block and are read as
 * generic Markdown instead.
 *
 * A note gets its explicit Joplin tags (alphabetical, the first is kept), else its notebook's
 * name. To-do notes arrive as ordinary notes and are counted. Resources (attachments) stay behind.
 */
internal object JoplinParser {
    private const val TYPE_NOTE = 1
    private const val TYPE_FOLDER = 2
    private const val TYPE_RESOURCE = 4
    private const val TYPE_TAG = 5
    private const val TYPE_NOTE_TAG = 6
    private val ID = Regex("""[0-9a-f]{32}""")
    private val PROPERTY = Regex("""^([A-Za-z_]+):(?: (.*))?$""")

    class Item(val id: String, val type: Int, val head: String, val props: Map<String, String>) {
        val title: String get() = head.lineSequence().firstOrNull().orEmpty().trim()
        val body: String get() = head.substringAfter('\n', "").trim('\n')
    }

    /** [text] as a Joplin item, or null if it has no metadata block with an `id` and a `type_`. */
    fun item(text: String): Item? {
        val trimmed = text.trimEnd()
        val split = trimmed.lastIndexOf("\n\n")
        val meta = if (split < 0) trimmed else trimmed.substring(split + 2)
        val head = if (split < 0) "" else trimmed.substring(0, split)
        val props = properties(meta)
        val id = props?.get("id")?.takeIf { ID.matches(it) }
        val type = props?.get("type_")?.toIntOrNull()
        return if (props != null && id != null && type != null) Item(id, type, head, props) else null
    }

    /** The `key: value` lines of [meta], or null if any line is something else. */
    private fun properties(meta: String): Map<String, String>? {
        val lines = meta.lines().map { PROPERTY.matchEntire(it) }
        return if (lines.any { it == null }) {
            null
        } else {
            lines.filterNotNull().associate {
                it.groupValues[1] to
                    it.groupValues[2]
            }
        }
    }

    fun parse(entries: List<ImportEntry>, limits: ImportLimits): ParsedNotes {
        val context = ParseContext(limits)
        val items = ArrayList<Item>()
        for (entry in entries.filter { it.extension == "md" }) {
            val text = context.text(entry)
            val item = text?.let(::item)
            when {
                text == null -> Unit
                item == null || item.props["encryption_applied"] == "1" -> context.unsupported++
                else -> items += item
            }
        }
        val folders = items.filter { it.type == TYPE_FOLDER }.associate { it.id to it.title }
        val tagsByNote = tagsByNote(items)
        context.extraLosses = ImportLosses(attachments = items.count { it.type == TYPE_RESOURCE })
        for (note in items.filter { it.type == TYPE_NOTE }) {
            val explicit = ImportText.names(tagsByNote[note.id].orEmpty().sortedBy { it.lowercase() })
            val notebook = folders[note.props["parent_id"]]?.let { ImportText.names(listOf(it)) }.orEmpty()
            context.add(note(note, explicit.ifEmpty { notebook }, explicit.size))
        }
        return context.result()
    }

    /** Note id -> names of the tags linked to it by `note_tag` items. */
    private fun tagsByNote(items: List<Item>): Map<String, List<String>> {
        val tags = items.filter { it.type == TYPE_TAG }.associate { it.id to it.title }
        return items.filter { it.type == TYPE_NOTE_TAG }
            .mapNotNull { link -> tags[link.props["tag_id"]]?.let { link.props["note_id"].orEmpty() to it } }
            .groupBy({ it.first }, { it.second })
    }

    private fun note(note: Item, tags: List<String>, explicitCount: Int) = ImportedNote(
        sourceKey = note.id,
        title = note.title,
        blocks = MarkdownBlockMapper.toBlocks(note.body),
        tags = tags,
        created = time(note, "user_created_time", "created_time"),
        edited = time(note, "user_updated_time", "updated_time"),
        trashed = time(note, "deleted_time") != null,
        losses = ImportLosses(
            extraTags = (explicitCount - 1).coerceAtLeast(0),
            todos = if (note.props["is_todo"] == "1") 1 else 0,
        ),
    )

    private fun time(item: Item, vararg keys: String): Long? =
        keys.firstNotNullOfOrNull { ImportTime.parse(item.props[it]) }
}
