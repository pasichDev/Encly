package com.pasich.encly.domain.repository

import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.SubtaskProgress
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

    /** Done/total sub-tasks of every task that has any. */
    fun getSubtaskProgress(): Flow<List<SubtaskProgress>>

    /** The task's sub-tasks in their order. */
    suspend fun getSubtasks(taskId: Long): Result<List<Subtask>>

    /** Makes [subtasks] the task's whole checklist, in this order (see TasksDao.replaceSubtasks). */
    suspend fun saveSubtasks(taskId: Long, subtasks: List<Subtask>): Result<Unit>

    /** Undo of a delete: the same task (id, uid, dates) and its sub-tasks, in one transaction. */
    suspend fun restoreTask(task: Task, subtasks: List<Subtask>): Result<Unit>
}
