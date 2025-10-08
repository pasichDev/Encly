package com.pasich.encly.domain.usecase.task

import com.pasich.encly.data.model.Task
import com.pasich.encly.data.repository.TasksRepository
import javax.inject.Inject

class AddTaskUseCase @Inject constructor(
    private val tasksRepository: TasksRepository
) {
    suspend operator fun invoke(task: Task): Long = tasksRepository.insertTask(task)
}
