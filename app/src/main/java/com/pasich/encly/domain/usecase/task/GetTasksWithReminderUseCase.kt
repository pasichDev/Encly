package com.pasich.encly.domain.usecase.task

import com.pasich.encly.data.repository.TasksRepository
import javax.inject.Inject

class GetTasksWithReminderUseCase @Inject constructor(
    private val tasksRepository: TasksRepository
) {
    suspend operator fun invoke(currentTime: Long) = tasksRepository.getTasksWithReminder(currentTime)
}
