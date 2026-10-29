package com.pasich.encly.domain.repository

import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Task
import kotlinx.coroutines.flow.Flow

/** The tasks. Writes follow the [NotesRepository] error contract. */
@Suppress("TooManyFunctions") // Tasks and their sub-tasks, so a task and its checklist are written together.
interface TasksRepository {
    fun getAllActiveTasks(): Flow<List<Task>>
    fun getAllCompletedTasks(): Flow<List<Task>>
    fun getAllTasks(): Flow<List<Task>>
    fun getActiveTasksCount(): Flow<Int>
    fun getCompletedTasksCount(): Flow<Int>

    /** The task's row id. */
    suspend fun insertTask(task: Task): Result<Long>
    suspend fun updateTask(task: Task): Result<Unit>
    suspend fun updateTaskStatus(id: Long, isCompleted: Boolean, completedDate: Long?): Result<Unit>
    suspend fun deleteTaskById(id: Long): Result<Unit>
    suspend fun deleteAllCompletedTasks(): Result<Unit>

    /** Every sub-task, by task and in each task's order. */
    fun observeSubtasks(): Flow<List<Subtask>>

    /** Ticks or unticks one sub-task. */
    suspend fun setSubtaskCompleted(id: Long, done: Boolean): Result<Unit>

    /** Adds a sub-task titled [title] after the task's last one; its row id. */
    suspend fun addSubtask(taskId: Long, title: String): Result<Long>

    suspend fun renameSubtask(id: Long, title: String): Result<Unit>

    suspend fun deleteSubtask(id: Long): Result<Unit>

    /** Undo of [deleteSubtask]: the same sub-task (uid, title, state) back at its position. */
    suspend fun restoreSubtask(subtask: Subtask): Result<Unit>

    /** The task's sub-tasks in their order. */
    suspend fun getSubtasks(taskId: Long): Result<List<Subtask>>

    /** Makes [subtasks] the task's whole checklist, in this order (see TasksDao.replaceSubtasks). */
    suspend fun saveSubtasks(taskId: Long, subtasks: List<Subtask>): Result<Unit>

    /** Undo of a delete: the same task (id, uid, dates) and its sub-tasks, in one transaction. */
    suspend fun restoreTask(task: Task, subtasks: List<Subtask>): Result<Unit>
}
