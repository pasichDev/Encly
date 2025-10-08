package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


@HiltViewModel
class MainListStateViewModel @Inject constructor(private var settingsRepository: SettingsRepository) :
    ViewModel() {

    val isGridNoteList: StateFlow<Boolean> =
        settingsRepository.isGridNoteList.map { it }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun updateGridNoteList() = settingsRepository.setGridNoteList(!isGridNoteList.value, viewModelScope)




}