package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.domain.repository.TagsRepository
import com.pasich.encly.domain.repository.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatisticViewModel @Inject constructor(tagsRepository: TagsRepository, tasksRepository: TasksRepository) :
    ViewModel() {

    val totalTagsCreated: StateFlow<Int> = tagsRepository.getTags()
        .map { tags -> tags.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0,
        )

    val totalTasksCreated: StateFlow<Int> = tasksRepository.getAllTasks()
        .map { tasks -> tasks.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0,
        )
}
