package com.pasich.encly.domain.usecase.task

import com.pasich.encly.data.repository.TasksRepository
import javax.inject.Inject

class UpdateTaskStatusUseCase @Inject constructor(
    private val tasksRepository: TasksRepository
) {
    suspend operator fun invoke(
        id: Long, 
        isCompleted: Boolean, 
        completedDate: Long? = if (isCompleted) System.currentTimeMillis() else null
    ) = tasksRepository.updateTaskStatus(id, isCompleted, completedDate)
}
