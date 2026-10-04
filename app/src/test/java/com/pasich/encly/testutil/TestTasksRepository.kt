package com.pasich.encly.testutil

import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.SubtaskProgress
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.repository.TasksRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Tasks and sub-tasks in memory; deleting a task deletes its sub-tasks, like the foreign key.
 * Each write fails while its `fail*` flag is set.
 */
internal class TestTasksRepository(initial: List<Task> = emptyList(), initialSubtasks: List<Subtask> = emptyList()) :
    TasksRepository {
    val tasks = MutableStateFlow(initial)
    val subtasks = MutableStateFlow(initialSubtasks)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1
    private var nextSubtaskId = (initialSubtasks.maxOfOrNull { it.id } ?: 0L) + 1

    var failInsert = false
    var failUpdate = false
    var failStatus = false
    var failDelete = false
    var failClear = false
    var failSubtasks = false

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
        subtasks.value = subtasks.value.filterNot { it.taskId == id }
    }

    override suspend fun deleteAllCompletedTasks(): Result<Unit> = write(failClear) {
        tasks.value = tasks.value.filterNot { it.isCompleted }
    }

    override fun getSubtaskProgress(): Flow<List<SubtaskProgress>> = subtasks.map { list ->
        list.groupBy {
            it.taskId
        }.map { (taskId, rows) -> SubtaskProgress(taskId, rows.count { it.isCompleted }, rows.size) }
    }

    override suspend fun getSubtasks(taskId: Long): Result<List<Subtask>> =
        Result.success(subtasks.value.filter { it.taskId == taskId }.sortedBy { it.position })

    override suspend fun saveSubtasks(taskId: Long, subtasks: List<Subtask>): Result<Unit> = write(failSubtasks) {
        val saved = subtasks.mapIndexed { index, subtask ->
            subtask.copy(
                id = if (subtask.id > 0L) subtask.id else nextSubtaskId++,
                taskId = taskId,
                position = index,
                uid = subtask.uid.ifBlank { "sub-$nextSubtaskId" },
            )
        }
        this.subtasks.value = this.subtasks.value.filterNot { it.taskId == taskId } + saved
    }

    override suspend fun restoreTask(task: Task, subtasks: List<Subtask>): Result<Unit> = write(failInsert) {
        tasks.value = tasks.value + task
        this.subtasks.value = this.subtasks.value + subtasks.map { it.copy(taskId = task.id) }
    }

    private inline fun write(fail: Boolean, block: () -> Unit): Result<Unit> =
        if (fail) Result.failure(IllegalStateException("write")) else runCatching(block)
}
