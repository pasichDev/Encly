package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(settingsRepository: SettingsRepository) : ViewModel() {

    // Default (dynamic=off, follow system theme). The real
    // stored values are emitted asynchronously — never block the main thread on the
    // DataStore read, since this ViewModel is created on the first frame (AppTheme).
    val themeSettingsFlow: StateFlow<ThemeSettings> =
        settingsRepository.themeSettingsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeSettings(),
        )
}
