package com.pasich.encly.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One item of a task's checklist. One level only: a sub-task has no sub-tasks of its own.
 * Deleting the task deletes its sub-tasks (`ON DELETE CASCADE`). [uid]: stable backup
 * identity, see [Note.uid].
 */
@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["taskId"]), Index(value = ["uid"], unique = true)],
)
data class Subtask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val title: String,
    val isCompleted: Boolean = false,
    val position: Int = 0,
    @ColumnInfo(defaultValue = "''")
    val uid: String = "",
)

/** How many of a task's sub-tasks are done, for the `2/5` on its tile. */
data class SubtaskProgress(val taskId: Long, val done: Int, val total: Int)
