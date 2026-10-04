package com.pasich.encly.data.handoff

import com.pasich.encly.core.backup.BackupNote
import com.pasich.encly.core.backup.BackupPayload
import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.core.backup.BackupTag
import com.pasich.encly.core.backup.BackupTask
import com.pasich.encly.data.backup.BackupImporter
import java.security.MessageDigest

/** What the user confirms before the import: counts only, never content. */
data class HandoffPreview(
    val notes: Int,
    val tasks: Int,
    val tags: Int,
    /** Attachment files that were not sent and stay in My Notes. */
    val attachments: Int,
    /** Pinned notes: Encly has no pinning, so they arrive unpinned. */
    val pinned: Int,
)

/** A hand-off mapped for [BackupImporter]; [dropped] records (blank tasks) are not in [payload]. */
class HandoffImport(val payload: BackupPayload, val preview: HandoffPreview, val dropped: Int)

/**
 * My Notes hand-off -> the [BackupPayload] the backup MERGE import applies, so a hand-off gets
 * the same single transaction and uid skip as a backup: a repeated hand-off adds nothing.
 *
 * - uids: the hand-off's stable ids, which fit Encly's uid limit; a longer one becomes its
 *   SHA-256 (same id, same uid, so repeats are still skipped).
 * - Tags and task categories both become Encly tags. Within the hand-off they are merged by
 *   name, and the import matches them against the vault's tags by name too
 *   ([com.pasich.encly.data.backup.TagMatch.UID_OR_NAME]), so no second tag with a name the
 *   user already has appears. A blank name is no tag; a note tag naming no user tag is dropped.
 * - A task's text: the first line is the Encly title, the rest (if any) its description. A task
 *   with no text is dropped. Done tasks get their creation time as completion time.
 * - `isPinned` has no Encly counterpart and is dropped (the preview says how many).
 */
object MyNotesHandoffMapper {
    private const val HEX_DIGITS = "0123456789abcdef"
    private const val NIBBLE = 4
    private const val LOW_NIBBLE = 0x0f

    fun map(handoff: MyNotesHandoff): HandoffImport {
        val tags = HandoffTags(handoff)
        val notes = handoff.notes.map { note ->
            BackupNote(
                uid = uidFor("note", note.id),
                title = note.title.trim(),
                value = MyNotesBlockMapper.toBlocksJson(note),
                description = "",
                date = note.date,
                dateCreate = note.date,
                tagUid = note.tag?.let(tags::uidForName),
                isTrash = note.isTrash,
            )
        }
        val tasks = handoff.tasks.mapNotNull { task -> mapTask(task, tags) }
        val payload = BackupPayload(
            exportedAt = handoff.exportedAt,
            tags = tags.backupTags,
            notes = notes,
            tasks = tasks,
        )
        BackupPayloadCodec.validate(payload)
        val preview = HandoffPreview(
            notes = notes.size,
            tasks = tasks.size,
            tags = tags.backupTags.size,
            attachments = handoff.notes.sumOf { it.attachments.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt(),
            pinned = handoff.notes.count { it.isPinned },
        )
        return HandoffImport(payload, preview, dropped = handoff.tasks.size - tasks.size)
    }

    private fun mapTask(task: HandoffTask, tags: HandoffTags): BackupTask? {
        val text = task.description.trim()
        if (text.isEmpty()) return null
        val title = text.substringBefore('\n').trim()
        val rest = text.substringAfter('\n', missingDelimiterValue = "").trim()
        return BackupTask(
            uid = uidFor("task", task.id),
            title = title,
            description = rest.ifEmpty { null },
            isCompleted = task.isDone,
            createdDate = task.createdAt,
            completedDate = if (task.isDone) task.createdAt else null,
            priority = 0,
            categoryTagUid = task.categoryId?.let(tags::uidForCategory),
            position = task.position,
            // My Notes tasks have no checklist.
            subtasks = emptyList(),
        )
    }

    /** The hand-off id as an Encly uid: as is when it fits, else a stable digest of it. */
    internal fun uidFor(kind: String, id: String): String =
        if (id.isNotBlank() && id.length <= BackupPayloadCodec.MAX_UID_LENGTH) id else sha256Hex("$kind:$id")

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

    /** User tags then task categories, each in its own order, merged into one tag per name. */
    private class HandoffTags(handoff: MyNotesHandoff) {
        private val uidByName = LinkedHashMap<String, String>()
        private val uidByCategoryId = HashMap<String, String>()
        private val usedUids = HashSet<String>()
        val backupTags = mutableListOf<BackupTag>()

        init {
            handoff.tags.sortedBy { it.position }.forEach { add("tag", it.id, it.name) }
            handoff.taskCategories.sortedBy { it.position }.forEach { category ->
                add("category", category.id, category.name)?.let { uidByCategoryId[category.id] = it }
            }
        }

        fun uidForName(name: String): String? = uidByName[BackupImporter.tagNameKey(name)]

        fun uidForCategory(id: String): String? = uidByCategoryId[id]

        /** The uid of the tag called [rawName], added if it is new; null for a blank name. */
        private fun add(kind: String, id: String, rawName: String): String? {
            val name = rawName.trim()
            if (name.isEmpty()) return null
            return uidByName.getOrPut(BackupImporter.tagNameKey(name)) {
                // A tag and a category can share an id (each list is unique only in itself).
                val uid = uidFor(kind, id).takeUnless { it in usedUids } ?: sha256Hex("$kind:$id")
                usedUids += uid
                backupTags += BackupTag(uid = uid, name = name, visible = true, position = backupTags.size)
                uid
            }
        }
    }
}
