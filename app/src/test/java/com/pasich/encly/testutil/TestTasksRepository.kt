package com.pasich.encly.testutil

import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.repository.TasksRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Tasks in memory. Each write fails while its `fail*` flag is set. */
internal class TestTasksRepository(initial: List<Task> = emptyList()) : TasksRepository {
    val tasks = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    var failInsert = false
    var failUpdate = false
    var failStatus = false
    var failDelete = false
    var failClear = false

    /** Every (id, isCompleted, completedDate) passed to [updateTaskStatus]. */
    val statusUpdates = mutableListOf<Triple<Long, Boolean, Long?>>()

    override fun getAllActiveTasks(): Flow<List<Task>> = tasks.map { list -> list.filterNot { it.isCompleted } }
    override fun getAllCompletedTasks(): Flow<List<Task>> = tasks.map { list -> list.filter { it.isCompleted } }
    override fun getAllTasks(): Flow<List<Task>> = tasks
    override fun getActiveTasksCount(): Flow<Int> = getAllActiveTasks().map { it.size }
    override fun getCompletedTasksCount(): Flow<Int> = getAllCompletedTasks().map { it.size }

    override suspend fun insertTask(task: Task): Result<Long> {
        if (failInsert) return Result.failure(IllegalStateException("insert"))
        val id = if (task.id > 0L) task.id else nextId++
        tasks.value = tasks.value + task.copy(id = id)
        return Result.success(id)
    }

    override suspend fun updateTask(task: Task): Result<Unit> = write(failUpdate) {
        check(tasks.value.any { it.id == task.id })
        tasks.value = tasks.value.map { if (it.id == task.id) task else it }
    }

    override suspend fun updateTaskStatus(id: Long, isCompleted: Boolean, completedDate: Long?): Result<Unit> =
        write(failStatus) {
            statusUpdates += Triple(id, isCompleted, completedDate)
            tasks.value = tasks.value.map {
                if (it.id == id) it.copy(isCompleted = isCompleted, completedDate = completedDate) else it
            }
        }

    override suspend fun deleteTaskById(id: Long): Result<Unit> = write(failDelete) {
        check(tasks.value.any { it.id == id })
        tasks.value = tasks.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllCompletedTasks(): Result<Unit> = write(failClear) {
        tasks.value = tasks.value.filterNot { it.isCompleted }
    }

    private inline fun write(fail: Boolean, block: () -> Unit): Result<Unit> =
        if (fail) Result.failure(IllegalStateException("write")) else runCatching(block)
}
