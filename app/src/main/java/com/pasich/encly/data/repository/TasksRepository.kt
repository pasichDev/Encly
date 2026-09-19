package com.pasich.encly.data.repository

import com.pasich.encly.data.model.Task
import kotlinx.coroutines.flow.Flow

interface TasksRepository {
    fun getAllActiveTasks(): Flow<List<Task>>
    fun getAllCompletedTasks(): Flow<List<Task>>
    fun getAllTasks(): Flow<List<Task>>
    fun getActiveTasksCount(): Flow<Int>
    fun getCompletedTasksCount(): Flow<Int>
    suspend fun getTaskById(id: Long): Task?
    suspend fun getTasksWithReminder(currentTime: Long): List<Task>
    suspend fun insertTask(task: Task): Long
    suspend fun updateTask(task: Task): Boolean
    suspend fun updateTaskStatus(id: Long, isCompleted: Boolean, completedDate: Long? = null): Boolean
    suspend fun deleteTask(task: Task): Boolean
    suspend fun deleteAllCompletedTasks(): Boolean
    suspend fun deleteTaskById(id: Long): Boolean
}
