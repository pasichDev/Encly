package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.domain.repository.SettingsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class MainListStateViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val isGridNoteList: StateFlow<Boolean> =
        settingsRepository.isGridNoteList.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun updateGridNoteList() {
        val value = !isGridNoteList.value
        viewModelScope.launch { settingsRepository.setGridNoteList(value) }
    }
}
