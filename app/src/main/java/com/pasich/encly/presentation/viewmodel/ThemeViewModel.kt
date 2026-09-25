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

    // MainActivity holds the splash until the theme was read once, so the first frame starts
    // from the stored theme instead of flashing the defaults. The main thread never blocks on
    // the DataStore: the defaults are only a fallback if that read has not happened.
    val themeSettingsFlow: StateFlow<ThemeSettings> =
        settingsRepository.themeSettingsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = settingsRepository.latestThemeSettings ?: ThemeSettings(),
        )
}
