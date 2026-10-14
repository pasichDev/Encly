package com.pasich.encly.data.importer

import androidx.annotation.StringRes
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.Block

/**
 * The apps Encly can import from. [formats], [howTo] and [losses] are string resources shown on
 * the Import notes page; a source is listed only once its parser and tests exist.
 */
enum class ImportSource(
    @get:StringRes val title: Int,
    @get:StringRes val formats: Int,
    @get:StringRes val howTo: Int,
    @get:StringRes val losses: Int,
) {
    GOOGLE_KEEP(
        R.string.import_keep_name,
        R.string.import_keep_formats,
        R.string.import_keep_howto,
        R.string.import_keep_losses,
    ),
    STANDARD_NOTES(
        R.string.import_sn_name,
        R.string.import_sn_formats,
        R.string.import_sn_howto,
        R.string.import_sn_losses,
    ),
    JOPLIN(
        R.string.import_joplin_name,
        R.string.import_joplin_formats,
        R.string.import_joplin_howto,
        R.string.import_joplin_losses,
    ),
    NOTESNOOK(
        R.string.import_nn_name,
        R.string.import_nn_formats,
        R.string.import_nn_howto,
        R.string.import_nn_losses,
    ),
    MARKDOWN(
        R.string.import_md_name,
        R.string.import_md_formats,
        R.string.import_md_howto,
        R.string.import_md_losses,
    ),
}

/** Why an import stops before anything is written. */
enum class ImportError {
    /** A limit of [ImportLimits] was exceeded. */
    TOO_LARGE,

    /** Files were read, but none is a supported export. */
    UNSUPPORTED,

    /** Nothing readable was selected. */
    NOTHING_FOUND,

    /** The selection could not be opened or read. */
    UNREADABLE,
}

/** Carries an [ImportError]; the message never quotes content. */
class ImportException(val error: ImportError) : Exception(error.name)

/**
 * Every limit is enforced on the bytes actually read, never on a size an archive header claims,
 * so a ZIP bomb stops at the limit.
 */
data class ImportLimits(
    /** Bytes read from the selected files in total (compressed bytes count as read). */
    val maxInputBytes: Long = MAX_TOTAL_BYTES,
    /** Bytes after decompression, over every entry (skipped ones included). */
    val maxTotalBytes: Long = MAX_TOTAL_BYTES,
    /** One note file. */
    val maxEntryBytes: Int = MAX_ENTRY_BYTES,
    /** A JSON export that holds many notes at once (Standard Notes). */
    val maxContainerBytes: Int = MAX_CONTAINER_BYTES,
    val maxEntries: Int = MAX_ENTRIES,
    val maxNotes: Int = MAX_NOTES,
    val maxPathDepth: Int = MAX_PATH_DEPTH,
    /** JSON nesting; the tree parser recurses per level. */
    val maxJsonDepth: Int = MAX_JSON_DEPTH,
) {
    companion object {
        private const val MIB = 1024 * 1024
        const val MAX_TOTAL_BYTES = 64L * MIB
        const val MAX_ENTRY_BYTES = 4 * MIB
        const val MAX_CONTAINER_BYTES = 16 * MIB
        const val MAX_ENTRIES = 20_000
        const val MAX_NOTES = 20_000
        const val MAX_PATH_DEPTH = 16
        const val MAX_JSON_DEPTH = 64
    }
}

/** One file of the selection (or one archive member), fully in memory and never on disk. */
class ImportEntry(val path: String, val bytes: ByteArray) {
    val name: String get() = path.substringAfterLast('/')
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

/** Files the reading layer left out, by reason. */
data class ReadSkips(
    val unsafePaths: Int = 0,
    val nestedArchives: Int = 0,
    val tooLarge: Int = 0,
    val unreadable: Int = 0,
) {
    val total: Int get() = unsafePaths + nestedArchives + tooLarge + unreadable
}

/** What the parsers could not bring over, counted over the whole selection. */
data class ImportLosses(
    val attachments: Int = 0,
    val pinned: Int = 0,
    val archived: Int = 0,
    val colors: Int = 0,
    /** Labels beyond the first: Encly has one tag per note. */
    val extraTags: Int = 0,
    /** Joplin to-do notes arrive as ordinary notes. */
    val todos: Int = 0,
) {
    operator fun plus(other: ImportLosses) = ImportLosses(
        attachments + other.attachments,
        pinned + other.pinned,
        archived + other.archived,
        colors + other.colors,
        extraTags + other.extraTags,
        todos + other.todos,
    )

    val any: Boolean get() = this != ImportLosses()
}

/** Entries the parsers did not use. [unsupported] includes encrypted items. */
data class ImportSkips(
    val unsupported: Int = 0,
    val unreadable: Int = 0,
    val empty: Int = 0,
    val duplicatesInSelection: Int = 0,
) {
    val files: Int get() = unreadable + empty
}

/** A note as read from another app, before it is mapped to Encly's records. */
data class ImportedNote(
    /** Stable id inside the source (uuid, path, ...) for a repeatable uid; null when there is none. */
    val sourceKey: String?,
    val title: String,
    val blocks: List<Block>,
    /** Tag names in order of preference; Encly keeps the first. */
    val tags: List<String> = emptyList(),
    val created: Long? = null,
    val edited: Long? = null,
    val trashed: Boolean = false,
    val losses: ImportLosses = ImportLosses(),
)

/** Parser output. */
class ParsedNotes(
    val notes: List<ImportedNote>,
    val skips: ImportSkips,
    /** Losses that belong to no single note (attachment files, Joplin resources). */
    val extraLosses: ImportLosses = ImportLosses(),
)
