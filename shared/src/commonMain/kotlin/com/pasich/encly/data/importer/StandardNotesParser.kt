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

    /** A tag title is cut to this many characters: far above any real tag name, far below a megabyte. */
    internal const val MAX_TAG_TITLE = 256

    /** The tags one note keeps; a real export has a handful, and the cap bounds the sort of a hostile one. */
    internal const val MAX_TAGS_PER_NOTE = 100

    enum class Shape { DECRYPTED, ENCRYPTED }

    /** What the backup in [bytes] (valid UTF-8) is if it has the shape of a Standard Notes backup, else null. */
    fun shape(bytes: ByteArray, maxDepth: Int): Shape? = scan(bytes, maxDepth, maxNotes = null)?.shape

    /** What one pass over a backup file found. [notes] are in file order, with their tags. */
    private class Backup(val shape: Shape?, val notes: List<ImportedNote>, val empties: Int = 0)

    /**
     * One pass over [bytes], an item at a time: the tree of the whole `items` array is never built, only
     * that of the item being read. What decides the shape can come after `items` (`keyParams`), so it is
     * settled at the end. Null if the file is not a JSON object the scan can follow.
     *
     * With [maxNotes] the notes are mapped as they pass, without tags (a tag lists its notes and can
     * come later in the file); more than [maxNotes] with content stops the scan with
     * [ImportError.TOO_LARGE], so a file of endless tiny items cannot pile up notes before the
     * caller's own limit is applied. Without it only the shape is found.
     */
    private fun scan(bytes: ByteArray, maxDepth: Int, maxNotes: Int?, checkCancelled: () -> Unit = {}): Backup? {
        val pass = Pass(maxNotes, checkCancelled)
        val ok = JsonScan.root(bytes, maxDepth) { key -> pass.member(this, key) }
        val notes = pass.notes.map {
            checkCancelled()
            it.copy(tags = tagNames(pass.tagsByNote[it.sourceKey]))
        }
        return if (ok) Backup(pass.shape(), notes, pass.empties) else null
    }

    /** The state of one [scan]: what the members and items seen so far say about the file. */
    private class Pass(private val maxNotes: Int?, private val checkCancelled: () -> Unit) {
        private var version = false
        private var keyParams = false
        private var items = false
        private var encrypted = false
        private var typed = false
        private var kept = 0

        /** How many notes had no content: reported as skipped, not kept. */
        var empties = 0
            private set
        val notes = ArrayList<ImportedNote>()

        /** Note uuid -> titles of the tags that reference it. */
        val tagsByNote = HashMap<String?, MutableList<String>>()

        fun member(cursor: JsonCursor, key: String) {
            when {
                key == "version" -> {
                    version = true
                    cursor.skip()
                }

                key == "keyParams" || key == "auth_params" -> {
                    keyParams = true
                    cursor.skip()
                }

                key == "items" && cursor.isArray() -> {
                    items = true
                    cursor.forEachElement(::read)
                }

                else -> cursor.skip()
            }
        }

        private fun read(element: JsonElement) {
            checkCancelled()
            val item = ImportText.obj(element) ?: return
            val type = ImportText.string(item["content_type"])
            typed = typed || type != null
            encrypted = encrypted || hasEncryptedContent(item)
            if (maxNotes == null) return
            when (type) {
                NOTE -> note(item)?.let { add(it, maxNotes) }
                TAG -> tagReferences(item)
            }
        }

        /**
         * A note with content counts against [maxNotes] and is kept. An empty one is only counted ([empties]): a
         * file of endless empty items piles up nothing, however many there are.
         */
        private fun add(note: ImportedNote, maxNotes: Int) {
            if (ParseContext.isEmpty(note)) {
                empties++
            } else {
                if (++kept > maxNotes) throw ImportException(ImportError.TOO_LARGE)
                notes += note
            }
        }

        private fun hasEncryptedContent(item: JsonObject): Boolean {
            val type = ImportText.string(item["content_type"])
            return (type == NOTE || type == TAG) && ImportText.string(item["content"]) != null
        }

        /** Adds to [tagsByNote] what a `Tag` item says. */
        private fun tagReferences(item: JsonObject) {
            val content = ImportText.obj(item["content"])
            val title = ImportText.string(content?.get("title"))?.trim()?.take(MAX_TAG_TITLE).orEmpty()
            if (title.isEmpty()) return
            for (element in content?.get("references") as? JsonArray ?: return) {
                checkCancelled()
                val ref = ImportText.obj(element)
                if (ref != null && ImportText.string(ref["content_type"]) == NOTE) {
                    tag(ImportText.string(ref["uuid"]), title)
                }
            }
        }

        /**
         * Gives the note [uuid] the tag [title]. A hostile file can list one note under one tag endlessly: the same tag
         * counts once, and a note keeps no more tags than any real export has.
         */
        private fun tag(uuid: String?, title: String) {
            val titles = tagsByNote.getOrPut(uuid) { ArrayList() }
            if (titles.size < MAX_TAGS_PER_NOTE && title !in titles) titles += title
        }

        fun shape(): Shape? = when {
            !items || !version -> null
            keyParams || encrypted -> Shape.ENCRYPTED
            typed -> Shape.DECRYPTED
            else -> null
        }
    }

    /** Alphabetical, so the tags' order does not depend on the order of the file. */
    private fun tagNames(titles: List<String>?): List<String> = ImportText.names(
        titles.orEmpty().map { it to it.lowercase() }.sortedBy { it.second }.map { it.first },
    )

    fun parse(entries: List<ImportEntry>, limits: ImportLimits, checkCancelled: () -> Unit = {}): ParsedNotes {
        val context = ParseContext(limits)
        for (entry in entries.filter { it.name.startsWith(BACKUP_FILE_PREFIX) || it.extension == "json" }) {
            val readable = context.isText(entry)
            val backup = if (readable) scan(entry.bytes, limits.maxJsonDepth, limits.maxNotes, checkCancelled) else null
            when {
                !readable -> Unit

                backup != null && backup.shape == Shape.DECRYPTED -> {
                    backup.notes.forEach(context::add)
                    context.empty += backup.empties
                }

                else -> context.unsupported++
            }
        }
        return context.result()
    }

    /** The note of a `Note` item, without its tags; null if it has no content object. */
    private fun note(item: JsonObject): ImportedNote? {
        val content = ImportText.obj(item["content"]) ?: return null
        return note(ImportText.string(item["uuid"]), item, content, emptyList())
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
