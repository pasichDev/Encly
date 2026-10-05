package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Subtask

/** What the Tasks list's inline sub-task field is for: a new sub-task of a task, or renaming one. */
sealed interface InlineSubtaskTarget {
    val taskId: Long

    /** The "Add sub-task" field at the bottom of the task's tree. */
    data class Add(override val taskId: Long) : InlineSubtaskTarget

    /** A sub-task row turned into a field to rename it. */
    data class Rename(override val taskId: Long, val subtaskId: Long) : InlineSubtaskTarget
}

/**
 * The one inline sub-task field open on the Tasks list: its [target], the [text] typed so far
 * and, when renaming, the stored sub-task it started from ([original], what Undo restores after a
 * delete).
 */
data class InlineSubtaskEdit(val target: InlineSubtaskTarget, val text: String, val original: Subtask? = null)
