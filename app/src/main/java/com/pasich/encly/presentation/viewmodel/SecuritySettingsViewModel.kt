package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecuritySettingsUiState())
    val uiState: StateFlow<SecuritySettingsUiState> = _uiState.asStateFlow()

    init {
        loadSecurityState()
    }

    private fun loadSecurityState() {
        viewModelScope.launch {
            val authSettings = withContext(Dispatchers.IO) { securityManager.getSettingsAuth() }
            _uiState.value = _uiState.value.copy(
                isUserCreatedSeedKey = authSettings.isUserCreatedSeedKey,
                authType = authSettings.authType,
                biometricEnable = authSettings.isBiometricEnabled,
                isBiometricAvailable = securityManager.biometricAvailable()
            )
        }
    }

    fun activationPinAuth(target: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) { securityManager.configurePin(target) }
            if (ok) {
                _uiState.value = _uiState.value.copy(authType = AuthType.PIN)
            } else {
                _uiState.value = _uiState.value.copy(error = "Не вдалося оновити PIN")
            }
            onResult(ok)
        }
    }

    /**
     * Enabling biometrics performs the auth-bound CryptoObject enrollment itself.
     * Disabling an existing biometric slot requires a fresh strong-biometric confirmation.
     */
    fun toggleBiometric(
        activity: FragmentActivity,
        enable: Boolean
    ) {
        if (enable) {
            securityManager.enrollBiometric(activity) { ok ->
                _uiState.value = _uiState.value.copy(
                    biometricEnable = ok && securityManager.isBiometricEnabled(),
                    error = if (ok) null else "Не вдалося створити біометричний ключ"
                )
            }
            return
        }

        securityManager.confirmBiometric(activity) { confirmed ->
            if (confirmed) {
                securityManager.disableBiometric()
                _uiState.value = _uiState.value.copy(biometricEnable = false)
            } else {
                _uiState.value = _uiState.value.copy(
                    biometricEnable = securityManager.isBiometricEnabled(),
                    error = "Зміну біометричного захисту не підтверджено"
                )
            }
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
        val isBiometricAvailable: Boolean = false
    )
}
