package com.pasich.encly.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TasksDao {

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY priority DESC, createdDate DESC")
    fun getAllActiveTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY completedDate DESC")
    fun getAllCompletedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, priority DESC, createdDate DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 0")
    fun getActiveTasksCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 1")
    fun getCompletedTasksCount(): Flow<Int>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task): Int

    @Query("UPDATE tasks SET isCompleted = :isCompleted, completedDate = :completedDate WHERE id = :id")
    suspend fun updateTaskStatus(id: Long, isCompleted: Boolean, completedDate: Long?): Int

    @Delete
    suspend fun deleteTask(task: Task): Int

    @Query("DELETE FROM tasks WHERE isCompleted = 1")
    suspend fun deleteAllCompletedTasks(): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long): Int

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position, id")
    suspend fun getSubtasks(taskId: Long): List<Subtask>

    /** Every sub-task, grouped by task and in each task's order, for the Tasks list. */
    @Query("SELECT * FROM subtasks ORDER BY taskId, position, id")
    fun observeSubtasks(): Flow<List<Subtask>>

    @Query("UPDATE subtasks SET isCompleted = :done WHERE id = :id")
    suspend fun setSubtaskCompleted(id: Long, done: Boolean): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSubtask(subtask: Subtask): Long

    /** The position after the task's last sub-task; 0 when it has none. */
    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM subtasks WHERE taskId = :taskId")
    suspend fun nextSubtaskPosition(taskId: Long): Int

    /** Adds a sub-task after the task's last one; its row id. */
    @Transaction
    suspend fun appendSubtask(taskId: Long, title: String): Long =
        insertSubtask(Subtask(taskId = taskId, title = title, position = nextSubtaskPosition(taskId)))

    @Query("UPDATE subtasks SET title = :title WHERE id = :id")
    suspend fun renameSubtask(id: Long, title: String): Int

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteSubtaskById(id: Long): Int

    @Query("UPDATE subtasks SET position = position + 1 WHERE taskId = :taskId AND position >= :position")
    suspend fun shiftSubtasksFrom(taskId: Long, position: Int): Int

    /**
     * Puts a deleted sub-task back (undo) at its old position, with its uid: the rows from that
     * position on move down one. It gets a new row id, as its old one may have been reused.
     */
    @Transaction
    suspend fun restoreSubtask(subtask: Subtask) {
        shiftSubtasksFrom(subtask.taskId, subtask.position)
        insertSubtask(subtask.copy(id = 0))
    }

    @Update
    suspend fun updateSubtask(subtask: Subtask): Int

    @Query("DELETE FROM subtasks WHERE taskId = :taskId AND id NOT IN (:keepIds)")
    suspend fun deleteSubtasksExcept(taskId: Long, keepIds: List<Long>): Int

    /**
     * Makes [subtasks] the task's whole checklist, in this order: rows that are no longer in
     * the list are deleted, the others are updated in place (keeping their id and uid), and
     * new ones (id 0) are inserted.
     */
    @Transaction
    suspend fun replaceSubtasks(taskId: Long, subtasks: List<Subtask>) {
        deleteSubtasksExcept(taskId, subtasks.map { it.id }.filter { it != 0L })
        subtasks.forEachIndexed { index, subtask ->
            val row = subtask.copy(taskId = taskId, position = index)
            // A row id that is gone (deleted meanwhile) is inserted again rather than lost.
            if (row.id == 0L || updateSubtask(row) == 0) insertSubtask(row)
        }
    }

    /** Puts a deleted task back together with its sub-tasks (undo). */
    @Transaction
    suspend fun restoreTask(task: Task, subtasks: List<Subtask>) {
        insertTask(task)
        subtasks.forEach { insertSubtask(it.copy(taskId = task.id)) }
    }
}
