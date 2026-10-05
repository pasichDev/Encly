package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Subtask

/**
 * One row of the task editor's checklist. [key] identifies the row while it is edited and
 * dragged (a stored row uses its id, a new one a negative number); [id] and [uid] are 0 and
 * blank until the row is saved.
 */
data class SubtaskDraft(
    val key: Long,
    val title: String,
    val isCompleted: Boolean = false,
    val id: Long = 0,
    val uid: String = "",
)

/** The editor checklist's edits and its mapping to stored rows, as pure functions. */
internal object SubtaskDrafts {
    fun fromSubtasks(subtasks: List<Subtask>): List<SubtaskDraft> = subtasks.map {
        SubtaskDraft(key = it.id, title = it.title, isCompleted = it.isCompleted, id = it.id, uid = it.uid)
    }

    /** [drafts] with the row at [from] moved to [to]; out-of-range indices change nothing. */
    fun move(drafts: List<SubtaskDraft>, from: Int, to: Int): List<SubtaskDraft> {
        if (from !in drafts.indices || to !in drafts.indices || from == to) return drafts
        return drafts.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * [rows] with the one whose id is [movedId] moved to where the one with [targetId] is; null
     * when either is not among them (deleted meanwhile). Unchanged when they are the same.
     */
    fun moveById(rows: List<Subtask>, movedId: Long, targetId: Long): List<Subtask>? {
        val from = rows.indexOfFirst { it.id == movedId }
        val to = rows.indexOfFirst { it.id == targetId }
        if (from < 0 || to < 0) return null
        return if (from == to) rows else rows.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * After [saved] was stored as [stored] (a save in the order [toSubtasks] gives), the stored
     * row of each saved draft, by the draft's key. A row whose title does not match (the
     * checklist changed under the save) is left out.
     */
    fun storedRows(saved: List<SubtaskDraft>, stored: List<Subtask>): Map<Long, Subtask> {
        val kept = saved.filter { it.title.isNotBlank() }
        if (kept.size != stored.size) return emptyMap()
        return kept.zip(stored)
            .filter { (draft, row) -> draft.title.trim() == row.title }
            .associate { (draft, row) -> draft.key to row }
    }

    /**
     * The rows to store for [taskId], in this order. A row left blank is dropped, like a task
     * without a title is never saved.
     */
    fun toSubtasks(taskId: Long, drafts: List<SubtaskDraft>): List<Subtask> = drafts
        .filter { it.title.isNotBlank() }
        .mapIndexed { index, draft ->
            Subtask(
                id = draft.id,
                taskId = taskId,
                title = draft.title.trim(),
                isCompleted = draft.isCompleted,
                position = index,
                uid = draft.uid,
            )
        }

    /**
     * Whether a save should offer to complete the task: it is still open and this save ticked
     * its last open sub-task. Neither direction is automatic: completing a task never ticks its
     * sub-tasks, and ticking every sub-task only offers to complete the task.
     */
    fun offersCompletion(taskCompleted: Boolean, before: List<Subtask>, after: List<Subtask>): Boolean {
        val allDoneBefore = before.isNotEmpty() && before.all { it.isCompleted }
        val allDoneAfter = after.isNotEmpty() && after.all { it.isCompleted }
        return !taskCompleted && allDoneAfter && !allDoneBefore
    }
}
