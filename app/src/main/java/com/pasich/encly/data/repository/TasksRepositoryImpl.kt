package com.pasich.encly.data.repository

import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.repository.TasksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TasksRepository"

/** See [NotesRepositoryImpl] for why the DAO is resolved per call. */
@Singleton
class TasksRepositoryImpl @Inject constructor(private val databaseProvider: DatabaseProvider) : TasksRepository {

    private fun dao() = databaseProvider.getDatabase().tasksDao()

    override fun getAllActiveTasks(): Flow<List<Task>> = daoFlow { dao().getAllActiveTasks() }

    override fun getAllCompletedTasks(): Flow<List<Task>> = daoFlow { dao().getAllCompletedTasks() }

    override fun getAllTasks(): Flow<List<Task>> = daoFlow { dao().getAllTasks() }

    override fun getActiveTasksCount(): Flow<Int> = daoFlow { dao().getActiveTasksCount() }

    override fun getCompletedTasksCount(): Flow<Int> = daoFlow { dao().getCompletedTasksCount() }

    override suspend fun insertTask(task: Task): Result<Long> = storageWrite(TAG, "insertTask") {
        dao().insertTask(task)
    }

    override suspend fun updateTask(task: Task): Result<Unit> = storageWrite(TAG, "updateTask") {
        dao().updateTask(task).requireRows()
    }

    override suspend fun updateTaskStatus(id: Long, isCompleted: Boolean, completedDate: Long?): Result<Unit> =
        storageWrite(TAG, "updateTaskStatus") {
            dao().updateTaskStatus(id, isCompleted, completedDate).requireRows()
        }

    override suspend fun deleteTaskById(id: Long): Result<Unit> = storageWrite(TAG, "deleteTaskById") {
        dao().deleteTaskById(id).requireRows()
    }

    override suspend fun deleteAllCompletedTasks(): Result<Unit> = storageWrite(TAG, "deleteAllCompletedTasks") {
        dao().deleteAllCompletedTasks()
    }
}
