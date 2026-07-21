package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.data.datasource.local.ThemeType
import com.pasich.encly.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(settingsRepository: SettingsRepository) :
    ViewModel() {

    // Default (dynamic=off, follow system theme, no screen protection). The real
    // stored values are emitted asynchronously — never block the main thread on the
    // DataStore read, since this ViewModel is created on the first frame (AppTheme).
    val themeSettingsFlow: StateFlow<Triple<Boolean, ThemeType, Boolean>> =
        settingsRepository.combinedThemeSettingsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Triple(false, ThemeType.SYSTEM, false)
        )
}