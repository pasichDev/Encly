package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.data.datasource.local.ThemeType
import com.pasich.encly.data.repository.SettingsRepository
import com.pasich.encly.utils.DeviceCapabilities
import com.pasich.encly.utils.SettingsValidator
import com.pasich.encly.utils.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val deviceCapabilities: DeviceCapabilities,
    private val settingsValidator: SettingsValidator
) : ViewModel() {

    val dialogVisibly = MutableStateFlow(false)

    private val _validationMessage = MutableStateFlow<String?>(null)
    val validationMessage: StateFlow<String?> = _validationMessage.asStateFlow()

    val themeSettingsFlow: StateFlow<Triple<Boolean, ThemeType, Boolean>> =
        settingsRepository.combinedThemeSettingsFlow.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = runBlocking { settingsRepository.combinedThemeSettingsFlow.first() })

    val showTasksFlow: StateFlow<Boolean> = settingsRepository.showTasksFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = runBlocking { settingsRepository.showTasksFlow.first() })

    val simpleEditFlow: StateFlow<Boolean> = settingsRepository.simpleEditFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = runBlocking { settingsRepository.simpleEditFlow.first() })

    // Device capabilities
    fun supportsDynamicColors() = deviceCapabilities.supportsDynamicColors()

    fun setDialogVisibility(value: Boolean) {
        dialogVisibly.value = value
    }

    fun clearValidationMessage() {
        _validationMessage.value = null
    }

    private fun validateAndExecute(
        validation: () -> ValidationResult, action: () -> Unit
    ) {
        when (val result = validation()) {
            is ValidationResult.Success -> {
                action()
                _validationMessage.value = null
            }

            is ValidationResult.Warning -> {
                _validationMessage.value = result.message
                action() // Execute anyway for warnings
            }

            is ValidationResult.Error -> {
                _validationMessage.value = result.message
                // Don't execute action for errors
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.UpdateIsDynamicTheme -> {
                settingsRepository.setDynamicTheme(event.value, viewModelScope)
            }

            is SettingsEvent.UpdateThemeType -> {
                viewModelScope.launch(Dispatchers.IO) {
                    settingsRepository.setThemeType(event.themeType, viewModelScope)
                }
            }

            is SettingsEvent.UpdateScreenProtect -> {
                validateAndExecute(
                    validation = { settingsValidator.validateScreenProtection(event.value) },
                    action = {
                        viewModelScope.launch(Dispatchers.IO) {
                            settingsRepository.setScreenProtection(event.value, viewModelScope)
                        }
                    })
            }

            is SettingsEvent.UpdateShowTasks -> {
                viewModelScope.launch(Dispatchers.IO) {
                    settingsRepository.setShowTasks(event.value, viewModelScope)
                }
            }

            is SettingsEvent.UpdateSimpleEdit -> {
                viewModelScope.launch(Dispatchers.IO) {
                    settingsRepository.setSimpleEdit(event.value, viewModelScope)
                }
            }


        }
    }
}

sealed class SettingsEvent {
    data class UpdateIsDynamicTheme(val value: Boolean) : SettingsEvent()
    data class UpdateThemeType(val themeType: ThemeType) : SettingsEvent()
    data class UpdateScreenProtect(val value: Boolean) : SettingsEvent()
    data class UpdateShowTasks(val value: Boolean) : SettingsEvent()
    data class UpdateSimpleEdit(val value: Boolean) : SettingsEvent()

}
