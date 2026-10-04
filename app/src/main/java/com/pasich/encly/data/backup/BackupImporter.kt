package com.pasich.encly.data.backup

import com.pasich.encly.core.backup.BackupNote
import com.pasich.encly.core.backup.BackupPayload
import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.core.backup.BackupSubtask
import com.pasich.encly.core.backup.BackupTag
import com.pasich.encly.core.backup.BackupTask
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import java.util.Locale

enum class ImportMode {
    /**
     * Keep the vault; add backup records whose uid is not present yet, skip the rest. A task
     * that is already here still gets the backup's sub-tasks it is missing.
     */
    MERGE,

    /** Delete every note, tag, task and sub-task first, then add the whole backup. */
    REPLACE,
}

/** How a backup tag finds the vault tag it is. */
enum class TagMatch {
    /** Only by uid: a tag with an unknown uid is added, even if one with its name exists. */
    UID,

    /**
     * By uid, else by name (trimmed, case-insensitive): a tag whose name the vault already has
     * reuses that tag instead of adding a second one with the same name. For sources whose tags
     * are just names, such as the My Notes hand-off.
     */
    UID_OR_NAME,
}

/** [skipped] counts notes, tags and tasks; sub-tasks are reported on their own. */
data class ImportSummary(
    val notesAdded: Int,
    val tagsAdded: Int,
    val tasksAdded: Int,
    val skipped: Int,
    val subtasksAdded: Int = 0,
)

/** Vault rows <-> [BackupPayload]. Links travel as uids, never as local autoincrement ids. */
object BackupMapper {
    fun toPayload(snapshot: VaultSnapshot, exportedAt: Long): BackupPayload {
        val tagUidById = snapshot.tags.associate { it.id to it.uid }
        val subtasksByTask = snapshot.subtasks.groupBy { it.taskId }
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
                    subtasks = subtasksByTask[task.id].orEmpty()
                        .sortedWith(compareBy({ it.position }, { it.id }))
                        .map { BackupSubtask(it.uid, it.title, it.isCompleted, it.position) },
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

    suspend fun import(
        payload: BackupPayload,
        mode: ImportMode,
        store: VaultDataStore,
        tagMatch: TagMatch = TagMatch.UID,
    ): ImportSummary {
        BackupPayloadCodec.validate(payload)
        return store.inTransaction {
            val existing = if (mode == ImportMode.REPLACE) {
                store.deleteAll()
                VaultSnapshot(emptyList(), emptyList(), emptyList(), emptyList())
            } else {
                store.snapshot()
            }
            val tagIds = existing.tags.associate { it.uid to it.id }.toMutableMap()
            val tagsAdded = importTags(payload.tags, existing, TagTarget(mode, tagMatch, tagIds), store)

            val noteUids = existing.notes.mapTo(HashSet()) { it.uid }
            val newNotes = payload.notes.filter { it.uid !in noteUids }
            newNotes.forEach { store.insertNote(it.toEntity(tagIds)) }

            val taskIds = existing.tasks.associate { it.uid to it.id }
            val newTasks = payload.tasks.filter { it.uid !in taskIds }
            var subtasksAdded = 0
            newTasks.forEach { task ->
                val taskId = store.insertTask(task.toEntity(tagIds))
                task.subtasks.forEach { store.insertSubtask(it.toEntity(taskId, it.position)) }
                subtasksAdded += task.subtasks.size
            }
            subtasksAdded += mergeSubtasks(payload.tasks.filter { it.uid in taskIds }, taskIds, existing, store)

            val total = payload.tags.size + payload.notes.size + payload.tasks.size
            val added = tagsAdded + newNotes.size + newTasks.size
            ImportSummary(
                notesAdded = newNotes.size,
                tagsAdded = tagsAdded,
                tasksAdded = newTasks.size,
                skipped = total - added,
                subtasksAdded = subtasksAdded,
            )
        }
    }

    /** [tagIds]: uid -> local id, filled in for every backup tag. */
    private class TagTarget(val mode: ImportMode, val match: TagMatch, val tagIds: MutableMap<String, Long>)

    /**
     * Adds missing tags and fills [TagTarget.tagIds] for every backup tag. A tag matched by name
     * ([TagMatch.UID_OR_NAME]) is not added; it counts as skipped, like a uid match.
     */
    private suspend fun importTags(
        tags: List<BackupTag>,
        existing: VaultSnapshot,
        target: TagTarget,
        store: VaultDataStore,
    ): Int {
        val merge = target.mode == ImportMode.MERGE
        // Merged tags go after the ones already on this device, in their backup order.
        var nextPosition = if (merge) existing.tags.maxOfOrNull { it.position + 1 } ?: 0 else 0
        // Name -> local id; the first tag with a name wins, also for tags added below.
        val idsByName = HashMap<String, Long>()
        if (target.match == TagMatch.UID_OR_NAME) {
            existing.tags.forEach { idsByName.putIfAbsent(tagNameKey(it.nameTag), it.id) }
        }
        var added = 0
        tags.sortedBy { it.position }.forEach { tag ->
            if (tag.uid in target.tagIds) return@forEach
            val sameName = if (target.match == TagMatch.UID_OR_NAME) idsByName[tagNameKey(tag.name)] else null
            if (sameName != null) {
                target.tagIds[tag.uid] = sameName
                return@forEach
            }
            val position = if (merge) nextPosition++ else tag.position
            val id = store.insertTag(
                Tag(nameTag = tag.name, isVisible = tag.visible, position = position, uid = tag.uid),
            )
            target.tagIds[tag.uid] = id
            if (target.match == TagMatch.UID_OR_NAME) idsByName.putIfAbsent(tagNameKey(tag.name), id)
            added++
        }
        return added
    }

    /**
     * For tasks already in the vault (MERGE only): adds the backup's sub-tasks whose uid is not
     * here yet, after the task's own ones and in their backup order. A uid that exists anywhere
     * in the vault is skipped, like every other merged record.
     */
    private suspend fun mergeSubtasks(
        tasks: List<BackupTask>,
        taskIds: Map<String, Long>,
        existing: VaultSnapshot,
        store: VaultDataStore,
    ): Int {
        val subtaskUids = existing.subtasks.mapTo(HashSet()) { it.uid }
        val nextPositions = existing.subtasks.groupBy { it.taskId }
            .mapValues { (_, subtasks) -> subtasks.maxOf { it.position } + 1 }
        var added = 0
        tasks.forEach { task ->
            val taskId = taskIds.getValue(task.uid)
            var nextPosition = nextPositions[taskId] ?: 0
            task.subtasks.sortedBy { it.position }.forEach { subtask ->
                if (subtask.uid !in subtaskUids) {
                    store.insertSubtask(subtask.toEntity(taskId, nextPosition++))
                    added++
                }
            }
        }
        return added
    }

    private fun BackupSubtask.toEntity(taskId: Long, position: Int) = Subtask(
        taskId = taskId,
        title = title,
        isCompleted = isCompleted,
        position = position,
        uid = uid,
    )

    /** Two tag names are the same tag for [TagMatch.UID_OR_NAME] when their keys are equal. */
    fun tagNameKey(name: String): String = name.trim().lowercase(Locale.ROOT)

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
