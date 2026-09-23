package com.pasich.encly.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.R
import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.data.backup.BackupDocuments
import com.pasich.encly.domain.usecase.OnboardingUseCase
import com.pasich.encly.presentation.screen.backup.backupErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SecurityType {
    /**
     * The user created their own recovery seed and manages it themselves.
     * Normal unlock still uses the mandatory PIN; the seed is an explicit recovery path.
     */
    USER_MANAGED,

    /**
     * No recovery seed is persisted. The random database key is still protected by the
     * mandatory PIN and optional auth-bound biometric slot.
     */
    AUTO_MANAGED,
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingUseCase: OnboardingUseCase,
    private val backupDocuments: BackupDocuments,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    /** The encrypted backup picked for "Restore from backup". Ciphertext only. */
    private var restoreFile: ByteArray? = null

    /**
     * The user-managed recovery seed until its vault exists. The vault is created from this
     * array, never from the displayed words; it is wiped once used and when the ViewModel goes.
     */
    private var recoverySeed: CharArray? = null

    /** Opens the "Restore from backup" page instead of creating an empty vault. */
    fun startRestore() {
        _uiState.value = _uiState.value.copy(isRestoring = true, error = null)
        _currentPage.value = RESTORE_PAGE
    }

    fun cancelRestore() {
        restoreFile = null
        _uiState.value = _uiState.value.copy(
            isRestoring = false,
            restoreFileReady = false,
            error = null,
        )
        _currentPage.value = SECURITY_CHOICE_PAGE
    }

    /** No app on this device can open documents: say so instead of crashing. */
    fun onRestorePickerUnavailable() {
        _uiState.value = _uiState.value.copy(error = UiText.of(R.string.backup_error_no_picker))
    }

    /** Reads the picked file and checks its header; the phrase is asked for only if it fits. */
    fun onRestoreFilePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val error = withContext(Dispatchers.IO) {
                try {
                    val file = backupDocuments.read(uri)
                    BackupCipher.inspect(file)
                    restoreFile = file
                    null
                } catch (e: BackupException) {
                    restoreFile = null
                    e.error
                }
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                restoreFileReady = error == null,
                error = error?.let { UiText.of(backupErrorMessage(it)) },
            )
        }
    }

    /**
     * Decrypts the picked backup with [phrase] and creates the vault around that phrase. The
     * backup itself is written after the mandatory PIN setup (AuthSetupScreen).
     */
    fun restoreBackup(phrase: String) {
        val file = restoreFile ?: return
        val chars = phrase.toCharArray()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = withContext(Dispatchers.Default) {
                onboardingUseCase.restoreFromBackup(file, chars)
            }
            result.onSuccess {
                restoreFile = null
                _uiState.value = _uiState.value.copy(isLoading = false)
                completeOnboarding()
            }.onFailure { e ->
                val error = (e as? BackupException)?.error ?: BackupError.IO
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = UiText.of(backupErrorMessage(error)),
                )
            }
        }
    }

    /*
     * Navigates to the seed phrase creation slide (does not create a key)
     */
    fun navigateToSeedPhraseCreation() {
        _uiState.value = _uiState.value.copy(
            securityType = SecurityType.USER_MANAGED,
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

            onboardingUseCase.generateRecoverySeed().onSuccess { seed ->
                wipeRecoverySeed()
                recoverySeed = seed
                _uiState.value = _uiState.value.copy(
                    // The words must be shown to be written down, so the UI gets them as text.
                    isLoading = false,
                    phase = String(seed),
                    securityType = SecurityType.USER_MANAGED,
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = UiText.of(R.string.onboarding_error_seed_generation),
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

            onboardingUseCase.createVault(recoverySeed = null).onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    securityType = SecurityType.AUTO_MANAGED,
                    isComplete = false,
                )
                // Advance only after keys are stored and the DB is unlocked (avoids a race
                // where the completion slide renders before setup finishes).
                nextPage()
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = UiText.of(R.string.onboarding_error_initialization),
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
     * Finishes the onboarding slides. Completion is deliberately not persisted here: first-run
     * setup becomes durable only after the mandatory PIN slot exists and SQLCipher opens
     * successfully (SecurityManager.finishInitialSetup).
     */
    fun completeOnboarding() {
        _uiState.value = _uiState.value.copy(isComplete = true)
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
            isVerificationComplete = false,
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
            isVerificationComplete = isComplete && currentAnswers.size == verificationWords.size,
        )
    }

    /**
     * Completes verification and moves to the next step
     */
    fun completeVerification() {
        if (!_uiState.value.isVerificationComplete) return

        val seed = recoverySeed ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            // A copy: the use case wipes what it gets, and a failed attempt must stay retryable.
            onboardingUseCase.createVault(seed.copyOf())
                .onSuccess {
                    wipeRecoverySeed()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isVerificationMode = false,
                        phase = "",
                        verificationWords = emptyList(),
                        userAnswers = emptyMap(),
                        isVerificationComplete = false,
                    )
                    nextPage()
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = UiText.of(R.string.onboarding_error_vault_creation),
                    )
                }
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
            isVerificationComplete = false,
        )
    }

    private fun wipeRecoverySeed() {
        recoverySeed?.let(SensitiveDataCleaner::clear)
        recoverySeed = null
    }

    override fun onCleared() {
        wipeRecoverySeed()
        super.onCleared()
    }

    data class OnboardingUiState(
        val isLoading: Boolean = false,
        val error: UiText? = null,
        val phase: String = "",
        val isKeyVisible: Boolean = false,
        val isComplete: Boolean = false,
        val securityType: SecurityType? = null,
        val isVerificationMode: Boolean = false,
        val verificationWords: List<Pair<Int, String>> = emptyList(), // word index + the word itself
        val userAnswers: Map<Int, String> = emptyMap(), // index -> user's answer
        val isVerificationComplete: Boolean = false,
        val isRestoring: Boolean = false,
        val restoreFileReady: Boolean = false,
    )

    private companion object {
        const val SECURITY_CHOICE_PAGE = 1
        const val RESTORE_PAGE = 2
    }
}
