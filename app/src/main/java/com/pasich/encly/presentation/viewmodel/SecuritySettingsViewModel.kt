package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.R
import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.BiometricStatus
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
class SecuritySettingsViewModel @Inject constructor(private val securityManager: SecurityManager) : ViewModel() {

    private val _uiState = MutableStateFlow(SecuritySettingsUiState())
    val uiState: StateFlow<SecuritySettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Reloads the vault's security state, e.g. after a recovery phrase was added. */
    fun refresh() {
        viewModelScope.launch {
            val authSettings = withContext(Dispatchers.IO) { securityManager.getSettingsAuth() }
            _uiState.value = _uiState.value.copy(
                isUserCreatedSeedKey = authSettings.isUserCreatedSeedKey,
                authType = authSettings.authType,
                biometricEnable = authSettings.isBiometricEnabled,
                biometricStatus = securityManager.biometricStatus(),
                loaded = true,
            )
        }
    }

    /**
     * False right after the vault was unlocked with the recovery phrase: the user forgot the
     * PIN, so the new one is set without it.
     */
    fun requiresCurrentPin(): Boolean = !securityManager.canResetPinWithoutCurrent()

    fun pinLockoutRemainingMillis(): Long = securityManager.pinLockoutRemainingMillis()

    fun verifyCurrentPin(target: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) { securityManager.verifyPin(target) }
            if (!ok) {
                _uiState.value = _uiState.value.copy(error = UiText.of(R.string.pin_current_wrong))
            }
            onResult(ok)
        }
    }

    fun activationPinAuth(target: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) { securityManager.configurePin(target) }
            if (ok) {
                _uiState.value = _uiState.value.copy(authType = AuthType.PIN)
            } else {
                _uiState.value = _uiState.value.copy(error = UiText.of(R.string.pin_update_failed))
            }
            onResult(ok)
        }
    }

    /**
     * Enabling biometrics performs the auth-bound CryptoObject enrollment itself.
     * Disabling an existing biometric slot requires a fresh strong-biometric confirmation.
     */
    fun toggleBiometric(activity: FragmentActivity, enable: Boolean) {
        if (enable) {
            securityManager.enrollBiometric(activity) { ok ->
                _uiState.value = _uiState.value.copy(
                    biometricEnable = ok && securityManager.isBiometricEnabled(),
                    error = if (ok) null else UiText.of(R.string.biometric_enroll_failed),
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
                    error = UiText.of(R.string.biometric_change_not_confirmed),
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    data class SecuritySettingsUiState(
        val error: UiText? = null,
        val authType: AuthType = AuthType.NONE,
        val isUserCreatedSeedKey: Boolean = false,
        val biometricEnable: Boolean = false,
        val biometricStatus: BiometricStatus = BiometricStatus.UNAVAILABLE,
        /** The first [refresh] finished; until then the page shows nothing rather than guesses. */
        val loaded: Boolean = false,
    ) {
        val isBiometricAvailable: Boolean get() = biometricStatus == BiometricStatus.AVAILABLE
    }
}
