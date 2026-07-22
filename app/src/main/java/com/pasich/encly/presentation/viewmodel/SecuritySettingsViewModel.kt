package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.domain.usecase.AuthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val biometricManager: BiometricManager,
    private val securityManager: SecurityManager,
    private val authUseCase: AuthUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecuritySettingsUiState())
    val uiState: StateFlow<SecuritySettingsUiState> = _uiState.asStateFlow()

    init {
        loadSecurityState()
    }

    // Reads auth settings off the main thread (getSettingsAuth does Keystore decrypts).
    private fun loadSecurityState() {
        viewModelScope.launch {
            val authSettings = withContext(Dispatchers.IO) { securityManager.getSettingsAuth() }
            val biometricAvailable = biometricManager.isStrongBiometricAvailable()
            _uiState.value = _uiState.value.copy(
                isUserCreatedSeedKey = authSettings.isUserCreatedSeedKey,
                authType = authSettings.authType,
                biometricEnable = authSettings.isBiometricEnabled,
                isBiometricAvailable = biometricAvailable
            )
        }
    }


    fun activationPinAuth(target: String) {
        viewModelScope.launch {
            delay(1500)
            // PBKDF2 PIN hashing runs off the main thread.
            withContext(Dispatchers.Default) { authUseCase.saveAuthConfigPinCode(target) }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    authType = AuthType.PIN
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    error = error.message ?: "Failed to set PIN"
                )
            }
        }
    }

    /**
     * Enables or disables biometric unlock and reflects the real persisted state in the UI.
     * Enabling can fail if no PIN/seed auth is configured — in that case the switch stays off
     * and an error is surfaced, so the toggle never lies about what was actually saved.
     */
    fun toggleBiometric(enable: Boolean) {
        viewModelScope.launch {
            val applied = withContext(Dispatchers.IO) {
                if (enable) {
                    securityManager.enableBiometric()
                } else {
                    securityManager.disableBiometric()
                    false
                }
            }
            if (enable && !applied) {
                _uiState.value = _uiState.value.copy(
                    error = "Спочатку налаштуйте PIN або сід-фразу"
                )
            }
            _uiState.value = _uiState.value.copy(
                biometricEnable = securityManager.isBiometricEnabled()
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    data class SecuritySettingsUiState(
        val error: String? = null,
        val authType: AuthType = AuthType.NONE,
        val isUserCreatedSeedKey: Boolean = false,
        val biometricEnable: Boolean = false,
        val isBiometricAvailable: Boolean = false,
    )
}
