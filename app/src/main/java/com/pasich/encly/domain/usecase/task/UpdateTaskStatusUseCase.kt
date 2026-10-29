package com.pasich.encly.domain.usecase.task

import com.pasich.encly.core.common.currentTimeMillis
import com.pasich.encly.domain.repository.TasksRepository
import dev.zacsweers.metro.Inject

/** Completes or reopens a task; a completed task records when it was completed. */
@Inject
class UpdateTaskStatusUseCase(private val tasksRepository: TasksRepository) {
    suspend operator fun invoke(
        id: Long,
        isCompleted: Boolean,
        completedDate: Long? = if (isCompleted) currentTimeMillis() else null,
    ): Result<Unit> = tasksRepository.updateTaskStatus(id, isCompleted, completedDate)
}
