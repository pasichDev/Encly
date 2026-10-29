package com.pasich.encly.data.importer

import com.pasich.encly.editor.codec.MarkdownBlockMapper

/**
 * Markdown / TXT files: generic (Obsidian, Markor, Joplin's Markdown export, ...) and the
 * Notesnook export, which differs only in its extras ([notesnook]).
 *
 * - Title: front matter `title`, else the file name. A first line that repeats the file name
 *   (`# Meeting notes` in `Meeting-notes.md`, as Notesnook and some other exporters write it) is
 *   the real title and is not repeated in the text; any other heading stays in the text.
 * - Front matter (a safe subset, see [Frontmatter]): `tags` (list or comma separated),
 *   `created` / `created_at` / `date`, `updated` / `updated_at` / `modified`. Nothing else is
 *   read and nothing is executed. Inline `#tags` in the text stay text.
 * - Tag: the front matter tags, else the first folder of the path (`_archive` is no tag).
 * - Notesnook: `pinned`, `archived`, `color` are counted as losses, `_archive/` marks archived
 *   notes, `attachments/` files stay behind, and a `.txt` export starts with the title line.
 *   These rules apply to the files that are Notesnook's ([parse]'s `notesnookFile`), not to every
 *   file of a mixed selection.
 * - Identity: a note has no id of its own, so its key is its path plus a digest of its title and
 *   text. Two `todo.md` of different folders stay two notes, an edited re-export is a new note,
 *   and the same file imported again is not imported twice.
 * - Image and media files next to the notes are not imported and are counted as attachments.
 */
internal object MarkdownNotesParser {
    private val TEXT_EXTENSIONS = setOf("md", "markdown", "mdown", "txt", "text")
    private val PLAIN_EXTENSIONS = setOf("txt", "text")
    private val SEPARATORS = Regex("""[\s_-]+""")
    private const val ARCHIVE_DIR = "_archive"
    private const val ATTACHMENTS_DIR = "attachments"

    fun isTextFile(entry: ImportEntry): Boolean = entry.extension in TEXT_EXTENSIONS

    /**
     * [notesnook] is the selection's source; [notesnookFile] says which of its text files carry
     * Notesnook's extras (all of them, by default).
     */
    fun parse(
        entries: List<ImportEntry>,
        limits: ImportLimits,
        notesnook: Boolean,
        notesnookFile: (ImportEntry) -> Boolean = { notesnook },
    ): ParsedNotes {
        val context = ParseContext(limits)
        var attachments = 0
        for (entry in entries) {
            val dirs = entry.path.split('/').dropLast(1)
            val nn = notesnook && notesnookFile(entry)
            when {
                (ATTACHMENTS_DIR in dirs && notesnook && (nn || !isTextFile(entry))) || ImportKinds.isMedia(entry) ->
                    attachments++

                isTextFile(entry) -> context.text(entry)?.let { context.add(note(entry, it, dirs, nn)) }

                ImportKinds.isIgnorable(entry) -> Unit

                else -> context.unsupported++
            }
        }
        context.extraLosses = ImportLosses(attachments = attachments)
        return context.result()
    }

    private class Titled(val title: String, val body: String)

    private fun note(entry: ImportEntry, text: String, dirs: List<String>, notesnook: Boolean): ImportedNote {
        val plain = entry.extension in PLAIN_EXTENSIONS
        val doc = if (plain) MarkdownDocument(emptyMap(), text) else Frontmatter.split(text)
        val titled = titled(doc, entry.name.substringBeforeLast('.'), plain, firstLineIsTitle = notesnook && plain)
        val folder = dirs.firstOrNull { it != ARCHIVE_DIR }
        val tags = ImportText.names(doc.list("tags")).ifEmpty { ImportText.names(listOfNotNull(folder)) }
        val blocks = MarkdownBlockMapper.toBlocks(titled.body)
        return ImportedNote(
            sourceKey = entry.path + "|" + ImportMapper.contentDigest(titled.title, blocks),
            title = titled.title,
            blocks = blocks,
            tags = tags,
            created = ImportTime.parse(doc.firstValue("created_at", "created", "date")),
            edited = ImportTime.parse(doc.firstValue("updated_at", "updated", "modified")),
            losses = losses(doc, tags.size, notesnook, archivedFolder = ARCHIVE_DIR in dirs),
        )
    }

    /** The title and what is left of the text once a first line that only repeats the title is dropped. */
    private fun titled(doc: MarkdownDocument, fileTitle: String, plain: Boolean, firstLineIsTitle: Boolean): Titled {
        val frontTitle = doc.value("title")?.trim()
        val lines = doc.body.lines()
        val index = lines.indexOfFirst { it.isNotBlank() }
        val first = lines.getOrNull(index)?.trim()
        val candidate = when {
            first == null -> null
            plain -> first
            first.startsWith("# ") -> first.removePrefix("# ").trim()
            else -> null
        }
        val repeats = candidate != null && when {
            firstLineIsTitle -> true
            frontTitle != null -> candidate == frontTitle
            else -> sameName(candidate, fileTitle)
        }
        val title = frontTitle ?: if (repeats) candidate else fileTitle
        return Titled(title.orEmpty().trim(), if (repeats) lines.drop(index + 1).joinToString("\n") else doc.body)
    }

    /** File names turn spaces into `-` or `_`: "Meeting-notes" is the name of "Meeting notes". */
    private fun sameName(title: String, fileTitle: String): Boolean =
        title.lowercase().replace(SEPARATORS, " ").trim() == fileTitle.lowercase().replace(SEPARATORS, " ").trim()

    /** Notesnook's extras that Encly has no counterpart for, and labels beyond the first. */
    private fun losses(doc: MarkdownDocument, tagCount: Int, notesnook: Boolean, archivedFolder: Boolean) =
        ImportLosses(
            pinned = if (notesnook && doc.flag("pinned")) 1 else 0,
            archived = if (archivedFolder || (notesnook && doc.flag("archived"))) 1 else 0,
            colors = if (notesnook && doc.value("color") != null) 1 else 0,
            extraTags = (tagCount - 1).coerceAtLeast(0),
        )
}
