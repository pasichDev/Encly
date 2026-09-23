package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.locale.AppLocales
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.utils.DeviceCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val deviceCapabilities: DeviceCapabilities,
) : ViewModel() {

    val dialogVisibly = MutableStateFlow(false)

    val languageDialogVisible = MutableStateFlow(false)

    // Sane defaults as the initial value; the real stored values arrive asynchronously.
    // Never block the main thread on the DataStore read (this VM feeds the settings UI).
    val themeSettingsFlow: StateFlow<ThemeSettings> =
        settingsRepository.themeSettingsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeSettings(),
        )

    val showTasksFlow: StateFlow<Boolean> = settingsRepository.showTasksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true,
    )

    val simpleEditFlow: StateFlow<Boolean> = settingsRepository.simpleEditFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false,
    )

    // Device capabilities
    fun supportsDynamicColors() = deviceCapabilities.supportsDynamicColors()

    /** Read live, not cached: this ViewModel outlives the activity a language change recreates. */
    fun currentLanguage(): AppLanguage = AppLocales.current()

    fun setLanguageDialogVisibility(value: Boolean) {
        languageDialogVisible.value = value
    }

    fun selectLanguage(language: AppLanguage) {
        languageDialogVisible.value = false
        if (language != AppLocales.current()) AppLocales.apply(language)
    }

    fun setDialogVisibility(value: Boolean) {
        dialogVisibly.value = value
    }

    /** DataStore writes are main-safe; no dispatcher switch is needed. */
    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsEvent.UpdateIsDynamicTheme -> settingsRepository.setDynamicTheme(event.value)
                is SettingsEvent.UpdateThemeType -> settingsRepository.setThemeType(event.themeType)
                is SettingsEvent.UpdateShowTasks -> settingsRepository.setShowTasks(event.value)
                is SettingsEvent.UpdateSimpleEdit -> settingsRepository.setSimpleEdit(event.value)
            }
        }
    }
}

sealed class SettingsEvent {
    data class UpdateIsDynamicTheme(val value: Boolean) : SettingsEvent()
    data class UpdateThemeType(val themeType: ThemeType) : SettingsEvent()
    data class UpdateShowTasks(val value: Boolean) : SettingsEvent()
    data class UpdateSimpleEdit(val value: Boolean) : SettingsEvent()
}
