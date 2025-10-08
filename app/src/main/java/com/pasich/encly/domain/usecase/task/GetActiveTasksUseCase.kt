package com.pasich.encly.domain.usecase.task

import com.pasich.encly.data.repository.TasksRepository
import javax.inject.Inject

class GetActiveTasksUseCase @Inject constructor(
    private val tasksRepository: TasksRepository
) {
    operator fun invoke() = tasksRepository.getAllActiveTasks()
}
