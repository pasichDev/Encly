package com.pasich.encly.domain.usecase.task

import com.pasich.encly.data.repository.TasksRepository
import javax.inject.Inject

class DeleteTaskUseCase @Inject constructor(
    private val tasksRepository: TasksRepository
) {
    suspend operator fun invoke(id: Long) = tasksRepository.deleteTaskById(id)
}
