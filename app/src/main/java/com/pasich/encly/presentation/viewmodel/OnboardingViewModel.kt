package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.domain.usecase.OnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


enum class SecurityType {
    /**
     * The user created their own seed phrase and manages it themselves.
     * Requires entering the seed phrase to unlock after a restart.
     */
    USER_MANAGED,

    /**
     * An automatic fallback seed phrase is used.
     * The system unlocks automatically without user involvement.
     */
    AUTO_MANAGED
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingUseCase: OnboardingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    /*
    * Navigates to the seed phrase creation slide (does not create a key)
    */
    fun navigateToSeedPhraseCreation() {
        _uiState.value = _uiState.value.copy(
            securityType = SecurityType.USER_MANAGED
        )
        // Navigate to the seed phrase display slide (third page)
        _currentPage.value = 2
        createUserManagedSecurity()
    }

    /**
     * Creates the user's own seed phrase (now invoked on the seed phrase slide)
     */
    fun createUserManagedSecurity() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            delay(1300L) // Intentional delay for smoothness

            onboardingUseCase.getMnemonicCode().onSuccess { seedPhrase ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false, phase = seedPhrase, securityType = SecurityType.USER_MANAGED
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false, error = error.message ?: "Помилка створення сід-фрази"
                )
            }
        }
    }

    /**
     * Skips seed phrase creation (auto-managed mode)
     */
    fun skipSecuritySetup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            onboardingUseCase.saveKeysStore().onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, securityType = SecurityType.AUTO_MANAGED, isComplete = false
                )
                // Advance only after keys are stored and the DB is unlocked (avoids a race
                // where the completion slide renders before setup finishes).
                nextPage()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false, error = error.message ?: "Помилка ініціалізації системи"
                )
            }
        }
    }

    fun nextPage() {
        if (_currentPage.value < 3) {
            _currentPage.value = _currentPage.value + 1
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun toggleKeyVisibility() {
        _uiState.value = _uiState.value.copy(isKeyVisible = !_uiState.value.isKeyVisible)
    }

    /**
     * Completes the entire onboarding process and records that it was shown
     */
    fun completeOnboarding() {
        _uiState.value = _uiState.value.copy(isComplete = true)
        viewModelScope.launch { onboardingUseCase.completeOnboarding() }
    }

    /**
     * Starts seed phrase verification
     */
    fun startSeedPhraseVerification() {
        val words = _uiState.value.phase.split(" ").filter { it.isNotBlank() }
        if (words.size < 3) return

        // Pick 3 random words to verify
        val randomIndices = words.indices.shuffled().take(3)
        val verificationWords = randomIndices.map { index ->
            index to words[index]
        }

        _uiState.value = _uiState.value.copy(
            isVerificationMode = true,
            verificationWords = verificationWords,
            userAnswers = emptyMap(),
            isVerificationComplete = false
        )
    }

    /**
     * Updates the user's answer for a specific word
     */
    fun updateUserAnswer(wordIndex: Int, answer: String) {
        val currentAnswers = _uiState.value.userAnswers.toMutableMap()
        currentAnswers[wordIndex] = answer.trim().lowercase()

        // Check whether all answers are correct
        val verificationWords = _uiState.value.verificationWords
        val isComplete = verificationWords.all { (index, word) ->
            currentAnswers[index]?.equals(word.lowercase(), ignoreCase = true) == true
        }

        _uiState.value = _uiState.value.copy(
            userAnswers = currentAnswers,
            isVerificationComplete = isComplete && currentAnswers.size == verificationWords.size
        )
    }

    /**
     * Completes verification and moves to the next step
     */
    fun completeVerification() {
        if (_uiState.value.isVerificationComplete) {
            val target = _uiState.value.phase.toCharArray()
            // Write the key to the keystore for its subsequent storage
            viewModelScope.launch {
                onboardingUseCase.saveKeysStore(target, target)
            }

            _uiState.value = _uiState.value.copy(
                isVerificationMode = false
            )
            // Navigate to the CompletionSlide
            nextPage()
        }
    }

    /**
     * Cancels verification and returns to displaying the seed phrase
     */
    fun cancelVerification() {
        _uiState.value = _uiState.value.copy(
            isVerificationMode = false,
            verificationWords = emptyList(),
            userAnswers = emptyMap(),
            isVerificationComplete = false
        )
    }

    data class OnboardingUiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val phase: String = "",
        val isKeyVisible: Boolean = false,
        val isComplete: Boolean = false,
        val securityType: SecurityType? = null,
        val isVerificationMode: Boolean = false,
        val verificationWords: List<Pair<Int, String>> = emptyList(), // word index + the word itself
        val userAnswers: Map<Int, String> = emptyMap(), // index -> user's answer
        val isVerificationComplete: Boolean = false
    )
}
