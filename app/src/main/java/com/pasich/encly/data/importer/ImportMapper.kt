package com.pasich.encly.data.importer

import com.pasich.encly.core.backup.BackupNote
import com.pasich.encly.core.backup.BackupPayload
import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.core.backup.BackupTag
import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.backup.BackupImporter
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockType
import java.security.MessageDigest

/** What the user confirms before anything is written: counts only, never content. */
data class ImportPreview(
    val source: ImportSource,
    val notes: Int,
    val checklists: Int,
    val tags: Int,
    val trashed: Int,
    /** Notes whose uid is already in the vault; filled in once the vault has been looked at. */
    val duplicates: Int = 0,
    /** Notes that repeat another note of the same selection. */
    val duplicatesInSelection: Int = 0,
    val losses: ImportLosses = ImportLosses(),
    /** Encrypted or unsupported entries that were left out. */
    val unsupported: Int = 0,
    /** Files that could not be read, were too large, empty or unsafe. */
    val filesSkipped: Int = 0,
    /** Archives inside archives, which are not opened. */
    val nestedArchives: Int = 0,
)

/** A mapped selection: the payload [com.pasich.encly.data.backup.BackupImporter] merges, and its [preview]. */
class ImportPlan(val payload: BackupPayload, val preview: ImportPreview, val rows: List<ReviewNote>) {
    fun withExisting(existingNoteUids: Set<String>): ImportPlan = ImportPlan(
        payload,
        preview.copy(duplicates = payload.notes.count { it.uid in existingNoteUids }),
        rows.map { it.copy(existing = it.uid in existingNoteUids) },
    )
}

/**
 * Parsed notes -> the [BackupPayload] the backup MERGE import applies, so an import gets the
 * same single transaction and uid skip as a backup.
 *
 * - uid: SHA-256 of `encly-import/v1|<source>|<source id>` when the export has a stable id
 *   (uuid, Joplin id, path, Keep path + creation time), else of the normalized title and text.
 *   The same export imports to the same uids, so a second import adds nothing; two notes with
 *   equal text but different titles or paths stay two notes.
 * - Tags are matched by name against the vault ([com.pasich.encly.data.backup.TagMatch.UID_OR_NAME]).
 *   Encly has one tag per note, so the first of a note's tags is used. Names are cut to
 *   [MAX_TAG_NAME] characters, as the tag editor does.
 * - A missing creation or edit date becomes the other one, or [now].
 */
object ImportMapper {
    /** The tag editor's limit (presentation TAG_NAME_MAX_LENGTH). */
    private const val MAX_TAG_NAME = 20
    private const val HEX_DIGITS = "0123456789abcdef"
    private const val NIBBLE = 4
    private const val LOW_NIBBLE = 0x0f
    private val WHITESPACE = Regex("""\s+""")

    internal fun map(parsed: ParsedNotes, source: ImportSource, read: ReadSkips, now: Long): ImportPlan {
        val tags = LinkedHashMap<String, BackupTag>()
        val uids = HashSet<String>()
        val notes = ArrayList<BackupNote>()
        val rows = ArrayList<ReviewNote>()
        var losses = parsed.extraLosses
        var checklists = 0
        var trashed = 0
        var duplicates = 0
        for (note in parsed.notes) {
            val uid = uidFor(source, note)
            if (!uids.add(uid)) {
                duplicates++
                continue
            }
            val tagUid = note.tags.firstNotNullOfOrNull { tag(it, tags) }
            val edited = note.edited ?: note.created ?: now
            notes += BackupNote(
                uid = uid,
                title = note.title.trim(),
                value = BlockConverter.blocksToJson(note.blocks),
                description = "",
                date = edited,
                dateCreate = note.created ?: edited,
                tagUid = tagUid,
                isTrash = note.trashed,
            )
            rows += ImportReview.row(uid, note, tagUid?.let { id -> tags.values.first { it.uid == id }.name }, edited)
            losses += note.losses
            if (note.blocks.any { it is Block.ListBlock && it.blockType == BlockType.LIST_CHECK }) checklists++
            if (note.trashed) trashed++
        }
        val usedTagUids = notes.mapNotNullTo(HashSet()) { it.tagUid }
        val payload = BackupPayload(
            exportedAt = now,
            tags = tags.values.filter { it.uid in usedTagUids }.mapIndexed { index, tag -> tag.copy(position = index) },
            notes = notes,
            tasks = emptyList(),
        )
        BackupPayloadCodec.validate(payload)
        val preview = ImportPreview(
            source = source,
            notes = notes.size,
            checklists = checklists,
            tags = payload.tags.size,
            trashed = trashed,
            duplicatesInSelection = duplicates,
            losses = losses,
            unsupported = parsed.skips.unsupported,
            filesSkipped = parsed.skips.files + read.total - read.nestedArchives,
            nestedArchives = read.nestedArchives,
        )
        return ImportPlan(payload, preview, rows)
    }

    private fun tag(raw: String, tags: MutableMap<String, BackupTag>): String? {
        val name = raw.trim().split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ").take(MAX_TAG_NAME).trim()
        if (name.isEmpty()) return null
        val key = BackupImporter.tagNameKey(name)
        return tags.getOrPut(key) {
            BackupTag(uid = sha256Hex("encly-import/v1|tag|$key"), name = name, visible = true, position = tags.size)
        }.uid
    }

    private fun uidFor(source: ImportSource, note: ImportedNote): String {
        val key = note.sourceKey?.takeIf { it.isNotBlank() }
        val identity = if (key != null) {
            "encly-import/v1|${source.name.lowercase()}|$key"
        } else {
            "encly-import/v1|${source.name.lowercase()}|content|${normalized(note.title)}\n${normalized(textOf(note))}"
        }
        return sha256Hex(identity)
    }

    private fun normalized(text: String): String = text.trim().replace(WHITESPACE, " ").lowercase()

    private fun textOf(note: ImportedNote): String = note.blocks.joinToString("\n") { block ->
        when (block) {
            is Block.TextBlock -> block.text.value
            is Block.HBlock -> block.text.value
            is Block.QuoteBlock -> block.text.value
            is Block.ListBlock -> block.items.value.joinToString("\n") { (if (it.isCheck) "[x] " else "") + it.value }
            else -> ""
        }
    }

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val b = byte.toInt()
                append(HEX_DIGITS[(b shr NIBBLE) and LOW_NIBBLE])
                append(HEX_DIGITS[b and LOW_NIBBLE])
            }
        }
    }
}

/** The outcome of looking at a selection. */
sealed interface Analysis {
    class Ready(val plan: ImportPlan) : Analysis

    data class Encrypted(val source: ImportSource) : Analysis

    /** Files of several apps: the user chooses one with [ImportAnalyzer.analyze]'s `forced`. */
    data object Ambiguous : Analysis

    data class Failed(val error: ImportError) : Analysis
}

/** Selection -> detection -> parser -> [ImportPlan]. Pure and in memory. */
object ImportAnalyzer {

    fun analyze(
        read: ReadResult,
        forced: ImportSource? = null,
        limits: ImportLimits = ImportLimits(),
        now: Long = System.currentTimeMillis(),
    ): Analysis {
        val kinds = FormatDetector.classify(read.entries, limits)
        val detection = if (forced == null) FormatDetector.detect(kinds) else FormatDetector.validate(forced, kinds)
        return when (detection) {
            Detection.Ambiguous -> Analysis.Ambiguous

            Detection.Empty -> Analysis.Failed(emptyError(read.skips))

            Detection.Unsupported -> Analysis.Failed(ImportError.UNSUPPORTED)

            is Detection.Encrypted -> Analysis.Encrypted(detection.source)

            is Detection.Found -> try {
                Analysis.Ready(plan(detection.source, read, kinds, limits, now))
            } catch (e: ImportException) {
                Analysis.Failed(e.error)
            }
        }
    }

    /** Nothing usable was read: say why, if files were left out on the way (too large, unreadable). */
    private fun emptyError(skips: ReadSkips): ImportError = when {
        skips.tooLarge > 0 -> ImportError.TOO_LARGE
        skips.unreadable > 0 -> ImportError.UNREADABLE
        else -> ImportError.NOTHING_FOUND
    }

    private fun plan(
        source: ImportSource,
        read: ReadResult,
        kinds: List<EntryKind>,
        limits: ImportLimits,
        now: Long,
    ): ImportPlan {
        val skipped = setOf(EntryKind.IGNORED, EntryKind.STANDARD_NOTES_ENCRYPTED, EntryKind.NOTESNOOK_BACKUP)
        // Ignored files still reach the parsers when they are media, so attachments can be counted.
        val usable = read.entries.filterIndexed { index, entry ->
            kinds[index] !in skipped ||
                (kinds[index] == EntryKind.IGNORED && !ImportKinds.isHidden(entry) && ImportKinds.isMedia(entry))
        }
        val encrypted = kinds.count { it == EntryKind.STANDARD_NOTES_ENCRYPTED || it == EntryKind.NOTESNOOK_BACKUP }
        val parsed = when (source) {
            ImportSource.GOOGLE_KEEP -> KeepParser.parse(usable, limits)
            ImportSource.STANDARD_NOTES -> StandardNotesParser.parse(usable, limits)
            ImportSource.JOPLIN -> JoplinParser.parse(usable, limits)
            ImportSource.NOTESNOOK -> MarkdownNotesParser.parse(usable, limits, notesnook = true)
            ImportSource.MARKDOWN -> MarkdownNotesParser.parse(usable, limits, notesnook = false)
        }
        val withEncrypted = ParsedNotes(
            parsed.notes,
            parsed.skips.copy(unsupported = parsed.skips.unsupported + encrypted),
            parsed.extraLosses,
        )
        val plan = ImportMapper.map(withEncrypted, source, read.skips, now)
        if (plan.payload.notes.isEmpty()) throw ImportException(ImportError.NOTHING_FOUND)
        return plan
    }
}
