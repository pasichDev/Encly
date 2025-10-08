package com.pasich.encly.presentation.viewmodel

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.old.AuthenticationManagerOLD
import com.pasich.encly.core.security.old.SecurityManagerOLD
import com.pasich.encly.old.auth.DialogAuthType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewAuthState(
    val isAuthEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val hasPinCode: Boolean = false,
    val isAuthenticated: Boolean = false,
    val showPinInput: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val authMethod: AuthenticationManagerOLD.AuthMethod = AuthenticationManagerOLD.AuthMethod.NONE
)

enum class NewDialogAuthType {
    NONE,
    PIN_SETUP,
    PIN_CONFIRM_FOR_DISABLE
}

@HiltViewModel
class NewAuthViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authenticationManagerOLD: AuthenticationManagerOLD,
    private val securityManagerOLD: SecurityManagerOLD
) : ViewModel() {

    private val _state = MutableStateFlow(NewAuthState())
    val state: StateFlow<NewAuthState> = _state.asStateFlow()

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            combine(
                authenticationManagerOLD.authState,
                securityManagerOLD.securityState
            ) { authState, securityState ->
                _state.value = _state.value.copy(
                    isAuthEnabled = authState.isAuthEnabled,
                    isBiometricEnabled = authState.isBiometricEnabled,
                    isBiometricAvailable = authState.isBiometricAvailable,
                    hasPinCode = authState.isPinEnabled,
                    isAuthenticated = authState.isAuthenticated,
                    authMethod = authState.authMethod
                )
            }
        }
    }

    /**
     * Встановлює PIN-код
     */
    fun setupPin(pin: String): Boolean {
        return authenticationManagerOLD.setPinCode(pin)
    }

    /**
     * Перевіряє PIN-код
     */
    fun verifyPin(pin: String): Boolean {
        val isValid = authenticationManagerOLD.verifyPin(pin)
        if (isValid) {
            // Розблоковуємо систему безпеки
            viewModelScope.launch {
                securityManagerOLD.unlockWithLocalAuth()
            }
        }
        return isValid
    }

    /**
     * Видаляє PIN-код
     */
    fun removePin(): Boolean {
        val success = authenticationManagerOLD.removePinCode()
        if (success) {
            authenticationManagerOLD.logout()
        }
        return success
    }

    /**
     * Перемикає стан авторизації
     */
    fun toggleSwitchAuth(enabled: Boolean): DialogAuthType {
        return if (enabled) {
            if (!_state.value.hasPinCode) {
                DialogAuthType.PIN_SETUP
            } else {
                DialogAuthType.NONE
            }
        } else {
            if (_state.value.hasPinCode) {
                DialogAuthType.PIN_CONFIRM_FOR_DISABLE
            } else {
                DialogAuthType.NONE
            }
        }
    }

    /**
     * Перемикає біометричну авторизацію
     */
    fun toggleBiometric(
        context: Context,
        activity: FragmentActivity?,
        enabled: Boolean,
        onError: (String) -> Unit
    ) {
        if (enabled && !_state.value.isBiometricAvailable) {
            onError("Біометрична автентифікація недоступна на цьому пристрої")
            return
        }

        if (enabled && !_state.value.hasPinCode) {
            onError("Спочатку встановіть PIN-код")
            return
        }

        if (enabled && activity != null) {
            // Перевіряємо біометрію перед увімкненням
            authenticationManagerOLD.authenticateWithBiometric(
                activity = activity,
                onSuccess = {
                    authenticationManagerOLD.setBiometricEnabled(true)
                },
                onError = { error ->
                    onError(error)
                },
                onFailed = {
                    onError("Біометрична автентифікація не вдалася")
                }
            )
        } else {
            authenticationManagerOLD.setBiometricEnabled(enabled)
        }
    }

    /**
     * Аутентифікація з біометрією
     */
    fun authenticateWithBiometric(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        authenticationManagerOLD.authenticateWithBiometric(
            activity = activity,
            onSuccess = {
                viewModelScope.launch {
                    securityManagerOLD.unlockWithLocalAuth()
                    onSuccess()
                }
            },
            onError = onError,
            onFailed = {
                onError("Біометрична автентифікація не вдалася")
            }
        )
    }

    /**
     * Вихід з системи
     */
    fun logout() {
        authenticationManagerOLD.logout()
        securityManagerOLD.lock()
    }

    /**
     * Очищення помилок
     */
    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    /**
     * Перевірка, чи потрібна автентифікація
     */
    fun requiresAuthentication(): Boolean {
        return _state.value.isAuthEnabled && !_state.value.isAuthenticated
    }
}
