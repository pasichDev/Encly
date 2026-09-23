package com.pasich.encly.domain.repository

import com.pasich.encly.data.model.Task
import kotlinx.coroutines.flow.Flow

/** The tasks. Writes follow the [NotesRepository] error contract. */
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
}
