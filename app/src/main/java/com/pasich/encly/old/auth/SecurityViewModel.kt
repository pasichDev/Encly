package com.pasich.encly.old.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.old.AuthenticationManagerOLD
import com.pasich.encly.core.security.old.SecurityManagerOLD
import com.pasich.encly.domain.usecase.OnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val securityManagerOLD: SecurityManagerOLD,
    private val authenticationManagerOLD: AuthenticationManagerOLD,
    private val onboardingUseCase: OnboardingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    private var autoHideJob: Job? = null

    init {
        loadSecurityStatus()
        observeSecurityState()
    }

    private fun observeSecurityState() {
        viewModelScope.launch {
            combine(
                securityManagerOLD.securityState,
                authenticationManagerOLD.authState
            ) { securityState, authState ->
                _uiState.value = _uiState.value.copy(
                    isInitialized = securityState != SecurityManagerOLD.SecurityState.NotInitialized,
                    isUnlocked = securityState == SecurityManagerOLD.SecurityState.Unlocked,
                    authState = authState
                )
            }
        }
    }

    private fun loadSecurityStatus() {
        _uiState.value = _uiState.value.copy(
            isInitialized = securityManagerOLD.isInitialized()
        )
    }

    fun showSeedPhrase() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Генеруємо нову сід-фразу для показу (тільки якщо система не ініціалізована)
          /*  if (!securityManagerOLD.isInitialized()) {
                onboardingUseCase.completeOnboarding()
                    .onSuccess { seedPhrase ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            seedPhrase = seedPhrase,
                            isPhraseVisible = false,
                            showPhraseBottomSheet = true
                        )
                        startAutoHideTimer()
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Помилка генерації сід-фрази"
                        )
                    }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Система вже ініціалізована. Сід-фразу можна переглянути тільки під час створення."
                )
            }

           */
        }
    }

    fun togglePhraseVisibility() {
        _uiState.value = _uiState.value.copy(isPhraseVisible = !_uiState.value.isPhraseVisible)
        if (_uiState.value.isPhraseVisible) {
            startAutoHideTimer()
        }
    }

    fun hidePhraseBottomSheet() {
        autoHideJob?.cancel()
        _uiState.value = _uiState.value.copy(
            showPhraseBottomSheet = false,
            seedPhrase = "",
            isPhraseVisible = false
        )
    }

    fun setKeySaved() {
        _uiState.value = _uiState.value.copy(isKeySaved = true)

        // Автоматично приховуємо через 2 секунди після збереження
        viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(isKeySaved = false)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun startAutoHideTimer() {
        autoHideJob?.cancel()
        autoHideJob = viewModelScope.launch {
            delay(20_000) // 20 секунд
            _uiState.value = _uiState.value.copy(
                isPhraseVisible = false,
                showPhraseBottomSheet = false,
                seedPhrase = ""
            )
        }
    }

    fun regenerateSeedPhrase() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Спочатку скидаємо систему
            securityManagerOLD.reset()

            // Потім генеруємо нову сід-фразу
        /*    onboardingUseCase.completeOnboarding()
                .onSuccess { seedPhrase ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        seedPhrase = seedPhrase,
                        isPhraseVisible = false,
                        showPhraseBottomSheet = true,
                        isInitialized = true
                    )
                    startAutoHideTimer()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Помилка генерації нової сід-фрази"
                    )
                }

         */
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoHideJob?.cancel()
    }

    data class SecurityUiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val seedPhrase: String = "",
        val isPhraseVisible: Boolean = false,
        val showPhraseBottomSheet: Boolean = false,
        val isKeySaved: Boolean = false,
        val isInitialized: Boolean = false,
        val isUnlocked: Boolean = false,
        val authState: AuthenticationManagerOLD.AuthState = AuthenticationManagerOLD.AuthState()
    )
}