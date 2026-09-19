package com.pasich.encly.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
    
    @Query("SELECT * FROM tasks WHERE reminderDate <= :currentTime AND isCompleted = 0")
    suspend fun getTasksWithReminder(currentTime: Long): List<Task>
    
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
    
}
