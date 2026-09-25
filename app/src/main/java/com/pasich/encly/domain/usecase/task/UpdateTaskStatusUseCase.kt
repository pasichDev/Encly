package com.pasich.encly.domain.usecase.task

import com.pasich.encly.domain.repository.TasksRepository
import javax.inject.Inject

/** Completes or reopens a task; a completed task records when it was completed. */
class UpdateTaskStatusUseCase @Inject constructor(private val tasksRepository: TasksRepository) {
    suspend operator fun invoke(
        id: Long,
        isCompleted: Boolean,
        completedDate: Long? = if (isCompleted) System.currentTimeMillis() else null,
    ): Result<Unit> = tasksRepository.updateTaskStatus(id, isCompleted, completedDate)
}
