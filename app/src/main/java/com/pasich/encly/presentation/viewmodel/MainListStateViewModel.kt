package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainListStateViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {

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
