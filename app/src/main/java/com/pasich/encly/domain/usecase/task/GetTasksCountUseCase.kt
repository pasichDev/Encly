package com.pasich.encly.domain.usecase.task

import com.pasich.encly.data.repository.TasksRepository
import javax.inject.Inject

class GetTasksCountUseCase @Inject constructor(
    private val tasksRepository: TasksRepository
) {
    fun getActiveCount() = tasksRepository.getActiveTasksCount()
    fun getCompletedCount() = tasksRepository.getCompletedTasksCount()
}
