package com.pasich.encly.data.backup

import com.pasich.encly.core.backup.BackupNote
import com.pasich.encly.core.backup.BackupPayload
import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.core.backup.BackupTag
import com.pasich.encly.core.backup.BackupTask
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task

enum class ImportMode {
    /** Keep the vault; add backup records whose uid is not present yet, skip the rest. */
    MERGE,

    /** Delete every note, tag and task first, then add the whole backup. */
    REPLACE,
}

data class ImportSummary(val notesAdded: Int, val tagsAdded: Int, val tasksAdded: Int, val skipped: Int)

/** Vault rows <-> [BackupPayload]. Links travel as uids, never as local autoincrement ids. */
object BackupMapper {
    fun toPayload(snapshot: VaultSnapshot, exportedAt: Long): BackupPayload {
        val tagUidById = snapshot.tags.associate { it.id to it.uid }
        return BackupPayload(
            exportedAt = exportedAt,
            tags = snapshot.tags.map { BackupTag(it.uid, it.nameTag, it.isVisible, it.position) },
            notes = snapshot.notes.map { note ->
                BackupNote(
                    uid = note.uid,
                    title = note.title,
                    value = note.value,
                    description = note.description,
                    date = note.date,
                    dateCreate = note.dateCreate,
                    tagUid = note.tagId?.let(tagUidById::get),
                    isTrash = note.isTrash,
                )
            },
            tasks = snapshot.tasks.map { task ->
                BackupTask(
                    uid = task.uid,
                    title = task.title,
                    description = task.description,
                    isCompleted = task.isCompleted,
                    createdDate = task.createdDate,
                    completedDate = task.completedDate,
                    priority = task.priority,
                    categoryTagUid = task.categoryId?.let(tagUidById::get),
                    position = task.position,
                )
            },
        ).also(BackupPayloadCodec::validate)
    }
}

/**
 * Applies an already decrypted and validated payload in one transaction: any failure rolls
 * the whole import back, so the vault is either unchanged or fully imported.
 */
object BackupImporter {

    suspend fun import(payload: BackupPayload, mode: ImportMode, store: VaultDataStore): ImportSummary {
        BackupPayloadCodec.validate(payload)
        return store.inTransaction {
            val existing = if (mode == ImportMode.REPLACE) {
                store.deleteAll()
                VaultSnapshot(emptyList(), emptyList(), emptyList())
            } else {
                store.snapshot()
            }
            val tagIds = existing.tags.associate { it.uid to it.id }.toMutableMap()
            val tagsAdded = importTags(payload.tags, existing, mode, tagIds, store)

            val noteUids = existing.notes.mapTo(HashSet()) { it.uid }
            val newNotes = payload.notes.filter { it.uid !in noteUids }
            newNotes.forEach { store.insertNote(it.toEntity(tagIds)) }

            val taskUids = existing.tasks.mapTo(HashSet()) { it.uid }
            val newTasks = payload.tasks.filter { it.uid !in taskUids }
            newTasks.forEach { store.insertTask(it.toEntity(tagIds)) }

            val total = payload.tags.size + payload.notes.size + payload.tasks.size
            val added = tagsAdded + newNotes.size + newTasks.size
            ImportSummary(
                notesAdded = newNotes.size,
                tagsAdded = tagsAdded,
                tasksAdded = newTasks.size,
                skipped = total - added,
            )
        }
    }

    /** Adds missing tags and fills [tagIds] (uid -> local id) for every backup tag. */
    private suspend fun importTags(
        tags: List<BackupTag>,
        existing: VaultSnapshot,
        mode: ImportMode,
        tagIds: MutableMap<String, Long>,
        store: VaultDataStore,
    ): Int {
        // Merged tags go after the ones already on this device, in their backup order.
        var nextPosition = if (mode == ImportMode.MERGE) {
            existing.tags.maxOfOrNull { it.position + 1 } ?: 0
        } else {
            0
        }
        var added = 0
        tags.sortedBy { it.position }.forEach { tag ->
            if (tag.uid in tagIds) return@forEach
            val position = if (mode == ImportMode.MERGE) nextPosition++ else tag.position
            tagIds[tag.uid] = store.insertTag(
                Tag(nameTag = tag.name, isVisible = tag.visible, position = position, uid = tag.uid),
            )
            added++
        }
        return added
    }

    private fun BackupNote.toEntity(tagIds: Map<String, Long>) = Note(
        title = title,
        value = value,
        description = description,
        date = date,
        dateCreate = dateCreate,
        tagId = tagUid?.let(tagIds::get),
        isTrash = isTrash,
        uid = uid,
    )

    private fun BackupTask.toEntity(tagIds: Map<String, Long>) = Task(
        title = title,
        description = description,
        isCompleted = isCompleted,
        createdDate = createdDate,
        completedDate = completedDate,
        priority = priority,
        categoryId = categoryTagUid?.let(tagIds::get),
        position = position,
        uid = uid,
    )
}
