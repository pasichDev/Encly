package com.pasich.encly.domain.repository

import com.pasich.encly.data.database.dao.TasksDao
import com.pasich.encly.data.model.Task
import com.pasich.encly.data.repository.TasksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TasksRepositoryImpl @Inject constructor(
    private val tasksDao: TasksDao
) : TasksRepository {
    
    override fun getAllActiveTasks(): Flow<List<Task>> = tasksDao.getAllActiveTasks()
    
    override fun getAllCompletedTasks(): Flow<List<Task>> = tasksDao.getAllCompletedTasks()
    
    override fun getAllTasks(): Flow<List<Task>> = tasksDao.getAllTasks()
    
    override fun getActiveTasksCount(): Flow<Int> = tasksDao.getActiveTasksCount()
    
    override fun getCompletedTasksCount(): Flow<Int> = tasksDao.getCompletedTasksCount()
    
    override suspend fun getTaskById(id: Long): Task? = tasksDao.getTaskById(id)
    
    override suspend fun getTasksWithReminder(currentTime: Long): List<Task> = 
        tasksDao.getTasksWithReminder(currentTime)
    
    override suspend fun insertTask(task: Task): Long = tasksDao.insertTask(task)
    
    override suspend fun updateTask(task: Task) = tasksDao.updateTask(task)
    
    override suspend fun updateTaskStatus(id: Long, isCompleted: Boolean, completedDate: Long?) = 
        tasksDao.updateTaskStatus(id, isCompleted, completedDate)
    
    override suspend fun deleteTask(task: Task) = tasksDao.deleteTask(task)
    
    override suspend fun deleteAllCompletedTasks() = tasksDao.deleteAllCompletedTasks()
    
    override suspend fun deleteTaskById(id: Long) = tasksDao.deleteTaskById(id)
}
