package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.repository.SettingsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class ThemeViewModel(settingsRepository: SettingsRepository) : ViewModel() {

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
