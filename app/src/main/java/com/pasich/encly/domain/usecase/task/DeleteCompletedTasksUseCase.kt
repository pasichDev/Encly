package com.pasich.encly.domain.usecase.task

import com.pasich.encly.data.repository.TasksRepository
import javax.inject.Inject

class DeleteCompletedTasksUseCase @Inject constructor(
    private val tasksRepository: TasksRepository
) {
    suspend operator fun invoke() = tasksRepository.deleteAllCompletedTasks()
}
