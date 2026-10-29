package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.AppLogger
import com.pasich.encly.domain.repository.TagsRepository
import com.pasich.encly.domain.repository.TasksRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val TAG = "StatisticViewModel"

@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class StatisticViewModel(tagsRepository: TagsRepository, tasksRepository: TasksRepository) : ViewModel() {

    /**
     * The counts below read the vault while the drawer is open. A read that fails (the vault was
     * just locked and its session closed) keeps the last count instead of crashing the app.
     */
    val totalTagsCreated: StateFlow<Int> = tagsRepository.getTags()
        .map { tags -> tags.size }
        .catch { AppLogger.e(TAG, "Tags count read failed") }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0,
        )

    /** Open (not completed) tasks: the drawer's Tasks badge. */
    val openTasksCount: StateFlow<Int> = tasksRepository.getActiveTasksCount()
        .catch { AppLogger.e(TAG, "Open tasks count read failed") }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0,
        )
}
