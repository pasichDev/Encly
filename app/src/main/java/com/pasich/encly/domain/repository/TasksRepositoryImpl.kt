package com.pasich.encly.domain.repository

import com.pasich.encly.data.datasource.local.DatabaseLocalDataSource
import com.pasich.encly.data.model.Task
import com.pasich.encly.data.repository.TasksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TasksRepositoryImpl @Inject constructor(
    private val databaseLocalDataSource: DatabaseLocalDataSource
) : TasksRepository {

    override fun getAllActiveTasks(): Flow<List<Task>> =
        databaseLocalDataSource.getAllActiveTasks()

    override fun getAllCompletedTasks(): Flow<List<Task>> =
        databaseLocalDataSource.getAllCompletedTasks()

    override fun getAllTasks(): Flow<List<Task>> =
        databaseLocalDataSource.getAllTasks()

    override fun getActiveTasksCount(): Flow<Int> =
        databaseLocalDataSource.getActiveTasksCount()

    override fun getCompletedTasksCount(): Flow<Int> =
        databaseLocalDataSource.getCompletedTasksCount()

    override suspend fun getTaskById(id: Long): Task? =
        databaseLocalDataSource.getTaskById(id)

    override suspend fun getTasksWithReminder(currentTime: Long): List<Task> =
        databaseLocalDataSource.getTasksWithReminder(currentTime)

    override suspend fun insertTask(task: Task): Long =
        databaseLocalDataSource.insertTask(task)

    override suspend fun updateTask(task: Task) =
        databaseLocalDataSource.updateTask(task)

    override suspend fun updateTaskStatus(id: Long, isCompleted: Boolean, completedDate: Long?) =
        databaseLocalDataSource.updateTaskStatus(id, isCompleted, completedDate)

    override suspend fun deleteTask(task: Task) =
        databaseLocalDataSource.deleteTask(task)

    override suspend fun deleteAllCompletedTasks() =
        databaseLocalDataSource.deleteAllCompletedTasks()

    override suspend fun deleteTaskById(id: Long) =
        databaseLocalDataSource.deleteTaskById(id)
}
