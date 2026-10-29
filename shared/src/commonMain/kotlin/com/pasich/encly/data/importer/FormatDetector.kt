package com.pasich.encly.data.importer

/** What one selected file turned out to be, judged by its content and never by its extension alone. */
internal enum class EntryKind {
    KEEP,
    STANDARD_NOTES,
    STANDARD_NOTES_ENCRYPTED,
    JOPLIN,
    NOTESNOOK,
    NOTESNOOK_BACKUP,

    /** Markdown or plain text with no other app's marks. */
    TEXT,

    /** Not a supported export: other JSON, unknown files. Never guessed at. */
    UNSUPPORTED,

    /** A text file that is not valid UTF-8. */
    UNREADABLE,

    /** Media, HTML duplicates, hidden folders, the plain files beside a Standard Notes backup. */
    IGNORED,
}

/** The verdict on a selection. */
sealed interface Detection {
    data class Found(val source: ImportSource) : Detection

    /** A refused, encrypted export; [source] says whose, for the guidance shown. */
    data class Encrypted(val source: ImportSource) : Detection

    /** Files of more than one app: the user picks the source. */
    data object Ambiguous : Detection

    data object Unsupported : Detection

    data object Empty : Detection
}

/**
 * Recognises exports by what is inside them:
 * - Keep: a JSON object with numeric `createdTimestampUsec` and `userEditedTimestampUsec`.
 * - Standard Notes: JSON `{version, items}` of typed items; `keyParams`/`auth_params` or a string
 *   `content` marks an encrypted backup, which is refused.
 * - Joplin: Markdown whose last paragraph is `key: value` lines with a 32-hex `id` and `type_`.
 * - Notesnook: Markdown front matter with `title`, `created_at` and `updated_at`; a `.nnbackup`
 *   or backup chunk (`hash_type`, `compressed`/`encrypted`) is an encrypted backup, refused.
 * - Anything else that is Markdown or text is generic; other JSON or files are unsupported.
 */
object FormatDetector {
    private val FOLDER_SOURCES = mapOf(
        EntryKind.KEEP to ImportSource.GOOGLE_KEEP,
        EntryKind.STANDARD_NOTES to ImportSource.STANDARD_NOTES,
        EntryKind.JOPLIN to ImportSource.JOPLIN,
        EntryKind.NOTESNOOK to ImportSource.NOTESNOOK,
    )

    internal fun classify(entries: List<ImportEntry>, limits: ImportLimits): List<EntryKind> {
        val itemFolders = entries.filter { it.name.startsWith(StandardNotesParser.BACKUP_FILE_PREFIX) }
            .map { it.path.removeSuffix(it.name) + "Items/" }
        return entries.map { entry ->
            when {
                ImportKinds.isNotesnookBackup(entry) -> EntryKind.NOTESNOOK_BACKUP

                ImportKinds.isHidden(entry) || itemFolders.any { entry.path.startsWith(it) } -> EntryKind.IGNORED

                entry.extension == "json" || entry.name.startsWith(StandardNotesParser.BACKUP_FILE_PREFIX) ||
                    isExtensionlessJson(entry) -> json(entry, limits)

                MarkdownNotesParser.isTextFile(entry) -> text(entry)

                ImportKinds.isIgnorable(entry) -> EntryKind.IGNORED

                else -> EntryKind.UNSUPPORTED
            }
        }
    }

    /** Notesnook's backup chunks (`0-plain-<hash>`) are JSON without an extension. */
    private fun isExtensionlessJson(entry: ImportEntry): Boolean =
        '.' !in entry.name && entry.bytes.firstOrNull { !it.toInt().toChar().isWhitespace() } == '{'.code.toByte()

    private fun json(entry: ImportEntry, limits: ImportLimits): EntryKind {
        val text = ImportText.utf8(entry.bytes)
        val root = text?.let { ImportText.obj(ImportText.json(it, limits.maxJsonDepth)) }
        val shape = StandardNotesParser.shape(root)
        return when {
            text == null -> EntryKind.UNREADABLE
            root == null -> EntryKind.UNSUPPORTED
            KeepParser.isNote(root) != null -> EntryKind.KEEP
            shape == StandardNotesParser.Shape.DECRYPTED -> EntryKind.STANDARD_NOTES
            shape == StandardNotesParser.Shape.ENCRYPTED -> EntryKind.STANDARD_NOTES_ENCRYPTED
            "hash_type" in root || ("compressed" in root && "encrypted" in root) -> EntryKind.NOTESNOOK_BACKUP
            else -> EntryKind.UNSUPPORTED
        }
    }

    private fun text(entry: ImportEntry): EntryKind {
        val text = ImportText.utf8(entry.bytes)
        val doc = text?.let(Frontmatter::split)
        return when {
            text == null -> EntryKind.UNREADABLE
            entry.extension == "md" && JoplinParser.item(text) != null -> EntryKind.JOPLIN
            doc != null && doc.has("title") && doc.has("created_at") && doc.has("updated_at") -> EntryKind.NOTESNOOK
            else -> EntryKind.TEXT
        }
    }

    /** Which source the [kinds] of a whole selection point to. */
    internal fun detect(kinds: List<EntryKind>): Detection {
        val present = kinds.toSet()
        val specific = FOLDER_SOURCES.filterKeys { it in present }.values.toSet()
        return when {
            specific.size > 1 -> Detection.Ambiguous
            specific.size == 1 -> Detection.Found(specific.first())
            EntryKind.STANDARD_NOTES_ENCRYPTED in present -> Detection.Encrypted(ImportSource.STANDARD_NOTES)
            EntryKind.NOTESNOOK_BACKUP in present -> Detection.Encrypted(ImportSource.NOTESNOOK)
            EntryKind.TEXT in present -> Detection.Found(ImportSource.MARKDOWN)
            kinds.isEmpty() -> Detection.Empty
            else -> Detection.Unsupported
        }
    }

    /** A manual choice of [source] still has to find something it can read in the selection. */
    internal fun validate(source: ImportSource, kinds: List<EntryKind>): Detection {
        val present = kinds.toSet()
        val accepted = when (source) {
            ImportSource.GOOGLE_KEEP -> setOf(EntryKind.KEEP)
            ImportSource.STANDARD_NOTES -> setOf(EntryKind.STANDARD_NOTES)
            ImportSource.JOPLIN -> setOf(EntryKind.JOPLIN)
            ImportSource.NOTESNOOK -> setOf(EntryKind.NOTESNOOK, EntryKind.TEXT)
            ImportSource.MARKDOWN -> setOf(EntryKind.TEXT, EntryKind.NOTESNOOK, EntryKind.JOPLIN)
        }
        return when {
            present.any { it in accepted } -> Detection.Found(source)

            source == ImportSource.STANDARD_NOTES && EntryKind.STANDARD_NOTES_ENCRYPTED in present ->
                Detection.Encrypted(source)

            source == ImportSource.NOTESNOOK && EntryKind.NOTESNOOK_BACKUP in present -> Detection.Encrypted(source)

            kinds.isEmpty() -> Detection.Empty

            else -> Detection.Unsupported
        }
    }

    /** Public entry point: the [Detection] for [entries]. */
    fun detect(entries: List<ImportEntry>, limits: ImportLimits = ImportLimits()): Detection =
        detect(classify(entries, limits))
}

internal object ImportKinds {
    private val IGNORABLE = setOf(
        "html", "htm", "css", "js", "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "tif",
        "tiff", "mp3", "m4a", "wav", "ogg", "aac", "amr", "3gp", "mp4", "mov", "webm", "pdf", "ds_store",
    )

    /** A hidden file or folder (`.obsidian/`, `.DS_Store`) or macOS metadata (`__MACOSX/`). */
    fun isHidden(entry: ImportEntry): Boolean = isHidden(entry.path)

    fun isHidden(path: String): Boolean = path.split('/').any { it.startsWith(".") || it == "__MACOSX" }

    fun isIgnorable(entry: ImportEntry): Boolean = entry.extension in IGNORABLE

    /** Images, audio, video and PDFs: attachments, which are not imported. */
    fun isMedia(entry: ImportEntry): Boolean = isMedia(entry.path)

    /** [path] (either separator) names media, judged by its extension alone, so it need not be read. */
    fun isMedia(path: String): Boolean {
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in IGNORABLE && extension !in MARKUP
    }

    private val MARKUP = setOf("html", "htm", "css", "js", "ds_store")

    fun isNotesnookBackup(entry: ImportEntry): Boolean = entry.extension == "nnbackup" || entry.name == ".nnbackup"
}
