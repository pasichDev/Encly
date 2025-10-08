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
     * Користувач створив власну сід-фразу і керує нею самостійно.
     * Потребує введення сід-фрази для розблокування після перезапуску.
     */
    USER_MANAGED,

    /**
     * Використовується автоматична fallback сід-фраза.
     * Система автоматично розблоковується без участі користувача.
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
    * Переходить до слайду створення сід-фрази (не створює ключ)
    */
    fun navigateToSeedPhraseCreation() {
        _uiState.value = _uiState.value.copy(
            securityType = SecurityType.USER_MANAGED
        )
        // Переходимо до слайду показу сід-фрази (третя сторінка)
        _currentPage.value = 2
        createUserManagedSecurity()
    }

    /**
     * Створює власну сід-фразу (тепер викликається на слайді сід-фрази)
     */
    fun createUserManagedSecurity() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            delay(1300L) // Спеціально затримуємо для плавності

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
     * Пропускає створення сід-фрази (auto-managed режим)
     */
    fun skipSecuritySetup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            onboardingUseCase.saveKeysStore().onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, securityType = SecurityType.AUTO_MANAGED, isComplete = false
                )
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
     * Завершує весь процес онбордінгу та вносить відмітку про його показ
     */
    fun completeOnboarding() {
        _uiState.value = _uiState.value.copy(isComplete = true)
        viewModelScope.launch { onboardingUseCase.completeOnboarding() }
    }

    /**
     * Починає верифікацію сід-фрази
     */
    fun startSeedPhraseVerification() {
        val words = _uiState.value.phase.split(" ").filter { it.isNotBlank() }
        if (words.size < 3) return

        // Вибираємо 3 випадкових слова для перевірки
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
     * Оновлює відповідь користувача для конкретного слова
     */
    fun updateUserAnswer(wordIndex: Int, answer: String) {
        val currentAnswers = _uiState.value.userAnswers.toMutableMap()
        currentAnswers[wordIndex] = answer.trim().lowercase()

        // Перевіряємо, чи всі відповіді правильні
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
     * Завершує верифікацію та переходить до наступного кроку
     */
    fun completeVerification() {
        if (_uiState.value.isVerificationComplete) {
            val target = _uiState.value.phase.toCharArray()
            // Записуємо ключ в кейстор для його подальшого збереження
            viewModelScope.launch {
                onboardingUseCase.saveKeysStore(target, target)
            }

            _uiState.value = _uiState.value.copy(
                isVerificationMode = false
            )
            // Переходимо до CompletionSlide
            nextPage()
        }
    }

    /**
     * Скасовує верифікацію та повертається до відображення сід-фрази
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
        val verificationWords: List<Pair<Int, String>> = emptyList(), // індекс слова + саме слово
        val userAnswers: Map<Int, String> = emptyMap(), // індекс -> відповідь користувача
        val isVerificationComplete: Boolean = false
    )
}
