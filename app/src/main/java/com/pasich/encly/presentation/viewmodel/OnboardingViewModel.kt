package com.pasich.encly.presentation.viewmodel

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.R
import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.security.PIN_LENGTH
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.data.backup.BackupDocuments
import com.pasich.encly.domain.usecase.OnboardingUseCase
import com.pasich.encly.presentation.screen.backup.backupErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

/**
 * The onboarding screens, in flow order (transitions slide forward along this order).
 * Welcome → PIN → recovery explained → write the phrase → check three words → ready;
 * "I have a backup" goes Welcome → restore → PIN → ready.
 */
enum class OnboardingStep { WELCOME, RESTORE, PIN, RECOVERY_INFO, PHRASE, VERIFY, READY }

/** Creating a new vault, or restoring one from a backup file and its phrase. */
enum class OnboardingPath { CREATE, RESTORE }

/** How a verification field looks: nothing typed, still typing, right, or wrong. */
enum class AnswerState { EMPTY, TYPING, CORRECT, WRONG }

/**
 * How a typed-back word compares with [expected]. It is flagged wrong only once it is as long as
 * the word, so the user is not told "wrong" while still typing. Shared with the backup check.
 */
fun phraseAnswerState(expected: String?, answer: String): AnswerState {
    val word = expected?.lowercase()
    val typed = answer.trim().lowercase()
    return when {
        typed.isEmpty() -> AnswerState.EMPTY
        typed == word -> AnswerState.CORRECT
        word != null && typed.length >= word.length -> AnswerState.WRONG
        else -> AnswerState.TYPING
    }
}

/** "STEP [step] OF [total]" in the progress header. */
data class StepProgress(val step: Int, val total: Int)

/**
 * The onboarding step machine. Nothing is written until the flow reaches Ready: the PIN is
 * held here (and wiped once used), the vault is created once the phrase is checked, skipped or
 * restored, and only then gets its PIN slot and, if asked for, its biometric slot. Committing
 * onboarding (and importing a staged restore) is [AuthSetupViewModel.finishSetup], run by
 * "Open my notebook". An abandoned flow leaves no committed vault, so the next start simply
 * begins onboarding again.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingUseCase: OnboardingUseCase,
    private val backupDocuments: BackupDocuments,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(biometricAvailable = onboardingUseCase.biometricAvailable()),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /** The encrypted backup picked for "Restore from backup". Ciphertext only. */
    private var restoreFile: ByteArray? = null

    /**
     * The user-managed recovery seed until its vault exists. The vault is created from this
     * array, never from the displayed words; it is wiped once used and when the ViewModel goes.
     */
    private var recoverySeed: CharArray? = null

    /** Digits typed on the PIN screen; only the first [OnboardingUiState.pinLength] count. */
    private val pinEntry = CharArray(PIN_LENGTH)

    /** The first PIN entry while the user types it again. */
    private var firstPin: CharArray? = null

    /** The confirmed PIN, kept until the vault exists and its PIN slot is set. */
    private var chosenPin: CharArray? = null

    // --- navigation -----------------------------------------------------------------------

    /** Welcome → "Get started". */
    fun getStarted() {
        resetPin()
        _uiState.update { it.copy(path = OnboardingPath.CREATE, step = OnboardingStep.PIN, error = null) }
    }

    /** Welcome → "I have a backup". */
    fun startRestore() {
        _uiState.update {
            it.copy(path = OnboardingPath.RESTORE, step = OnboardingStep.RESTORE, securityType = null, error = null)
        }
    }

    /**
     * The header back button and system back. Ready has no way back (the vault exists), and
     * nothing moves while an operation runs.
     */
    fun back() {
        val state = _uiState.value
        if (state.isLoading) return
        when (state.step) {
            OnboardingStep.WELCOME, OnboardingStep.READY -> Unit

            OnboardingStep.RESTORE -> {
                leaveRestore()
                goTo(OnboardingStep.WELCOME)
            }

            OnboardingStep.PIN -> {
                resetPin()
                goTo(if (state.path == OnboardingPath.RESTORE) OnboardingStep.RESTORE else OnboardingStep.WELCOME)
            }

            OnboardingStep.RECOVERY_INFO -> {
                resetPin()
                goTo(OnboardingStep.PIN)
            }

            OnboardingStep.PHRASE -> goTo(OnboardingStep.RECOVERY_INFO)

            OnboardingStep.VERIFY -> cancelVerification()
        }
    }

    private fun goTo(step: OnboardingStep) {
        _uiState.update { it.copy(step = step, error = null) }
    }

    // --- step 1: PIN and fingerprint ----------------------------------------------------

    fun onPinDigit(digit: Int) {
        val length = _uiState.value.pinLength
        if (length >= PIN_LENGTH || digit !in 0..MAX_DIGIT) return
        pinEntry[length] = '0' + digit
        _uiState.update { it.copy(pinLength = length + 1, pinError = null) }
    }

    fun onPinBackspace() {
        val length = _uiState.value.pinLength
        if (length == 0) return
        pinEntry[length - 1] = 0.toChar()
        _uiState.update { it.copy(pinLength = length - 1) }
    }

    fun setBiometricRequested(requested: Boolean) {
        _uiState.update { it.copy(biometricRequested = requested) }
    }

    /**
     * "Continue" on the PIN screen: the first entry asks for the PIN again; a matching second
     * entry moves on (on the restore path straight to Ready), a different one starts over.
     */
    fun submitPin() {
        val state = _uiState.value
        if (state.pinLength != PIN_LENGTH || state.isLoading) return
        val entered = pinEntry.copyOf()
        SensitiveDataCleaner.clear(pinEntry)
        val first = firstPin
        if (first == null) askPinAgain(entered) else confirmPin(first, entered, state.path)
    }

    private fun askPinAgain(entered: CharArray) {
        firstPin = entered
        _uiState.update { it.copy(pinLength = 0, isConfirmingPin = true, pinError = null) }
    }

    private fun confirmPin(first: CharArray, entered: CharArray, path: OnboardingPath) {
        val matches = first.contentEquals(entered)
        SensitiveDataCleaner.clear(entered)
        if (!matches) {
            resetPin()
            _uiState.update { it.copy(pinError = UiText.of(R.string.pin_mismatch_retry)) }
            return
        }
        firstPin = null
        chosenPin?.let(SensitiveDataCleaner::clear)
        chosenPin = first
        _uiState.update { it.copy(pinLength = 0, isConfirmingPin = false, pinError = null) }
        if (path == OnboardingPath.RESTORE) {
            // The restore already created the vault around the backup's phrase.
            _uiState.update { it.copy(isLoading = true) }
            viewModelScope.launch { reachReady() }
        } else {
            goTo(OnboardingStep.RECOVERY_INFO)
        }
    }

    // --- step 2: why a recovery phrase ----------------------------------------------------

    /** "Create my phrase": a user-managed vault. The words are generated once per flow. */
    fun createPhrase() {
        _uiState.update {
            it.copy(securityType = SecurityType.USER_MANAGED, step = OnboardingStep.PHRASE, error = null)
        }
        if (recoverySeed == null) generateRecoverySeed()
    }

    /** "Skip: PIN only, no backups": an auto-managed vault with no recovery slot. */
    fun skipPhrase() {
        if (_uiState.value.isLoading) return
        wipeRecoverySeed()
        _uiState.update {
            it.copy(securityType = SecurityType.AUTO_MANAGED, words = emptyList(), isLoading = true, error = null)
        }
        viewModelScope.launch {
            val created = withContext(Dispatchers.Default) { onboardingUseCase.createVault(recoverySeed = null) }
            if (created.isSuccess) {
                reachReady()
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = UiText.of(R.string.onboarding_error_initialization))
                }
            }
        }
    }

    private fun generateRecoverySeed() {
        onboardingUseCase.generateRecoverySeed().onSuccess { seed ->
            wipeRecoverySeed()
            recoverySeed = seed
            // The words must be shown to be written down, so the UI gets them as text.
            _uiState.update { it.copy(words = String(seed).split(" ").filter(String::isNotBlank)) }
        }.onFailure {
            _uiState.update { it.copy(error = UiText.of(R.string.onboarding_error_seed_generation)) }
        }
    }

    // --- step 3: write the phrase, then check three words ---------------------------------

    fun toggleWordsHidden() {
        _uiState.update { it.copy(wordsHidden = !it.wordsHidden) }
    }

    /** "I wrote them down": asks for three random words, in ascending order. */
    fun startSeedPhraseVerification() {
        val words = _uiState.value.words
        if (words.size < VERIFY_WORD_COUNT) return
        val verificationWords = words.indices.shuffled().take(VERIFY_WORD_COUNT).sorted().map { it to words[it] }
        _uiState.update {
            it.copy(
                step = OnboardingStep.VERIFY,
                verificationWords = verificationWords,
                userAnswers = emptyMap(),
                isVerificationComplete = false,
            )
        }
    }

    fun updateUserAnswer(wordIndex: Int, answer: String) {
        val answers = _uiState.value.userAnswers + (wordIndex to answer.trim().lowercase())
        val verificationWords = _uiState.value.verificationWords
        val complete = verificationWords.all { (index, word) -> answers[index] == word.lowercase() }
        _uiState.update { it.copy(userAnswers = answers, isVerificationComplete = complete) }
    }

    /** "Confirm": creates the user-managed vault from the seed, then sets its PIN. */
    fun completeVerification() {
        val state = _uiState.value
        if (!state.isVerificationComplete || state.isLoading) return
        val seed = recoverySeed ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // A copy: the use case wipes what it gets, and a failed attempt must stay retryable.
            val created = withContext(Dispatchers.Default) { onboardingUseCase.createVault(seed.copyOf()) }
            if (created.isSuccess) {
                reachReady()
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = UiText.of(R.string.onboarding_error_vault_creation))
                }
            }
        }
    }

    /** "Show the words again" (and back from the check): returns to the word list. */
    fun cancelVerification() {
        _uiState.update {
            it.copy(
                step = OnboardingStep.PHRASE,
                verificationWords = emptyList(),
                userAnswers = emptyMap(),
                isVerificationComplete = false,
            )
        }
    }

    // --- step 4: ready --------------------------------------------------------------------

    /**
     * The vault exists: give it the chosen PIN, then ask the screen for the biometric slot
     * ([enrollBiometric]) when the user wanted it. A PIN slot that fails keeps the PIN, so the
     * same button retries; creating the still uncommitted vault again is safe.
     */
    private suspend fun reachReady() {
        val pin = chosenPin
        val configured = pin != null &&
            withContext(Dispatchers.Default) { onboardingUseCase.configurePin(pin.copyOf()) }
        if (!configured) {
            _uiState.update { it.copy(isLoading = false, error = UiText.of(R.string.pin_save_failed)) }
            return
        }
        chosenPin?.let(SensitiveDataCleaner::clear)
        chosenPin = null
        wipeRecoverySeed()
        _uiState.update {
            it.copy(
                isLoading = false,
                step = OnboardingStep.READY,
                words = emptyList(),
                verificationWords = emptyList(),
                userAnswers = emptyMap(),
                isVerificationComplete = false,
                biometricPending = it.biometricRequested && it.biometricAvailable,
            )
        }
    }

    /** Shows the system biometric prompt that adds the fingerprint slot. */
    fun enrollBiometric(activity: FragmentActivity) {
        if (!_uiState.value.biometricPending) return
        _uiState.update { it.copy(biometricPending = false) }
        onboardingUseCase.enrollBiometric(activity) { ok ->
            _uiState.update { it.copy(biometricEnabled = ok) }
        }
    }

    /** No activity to show the prompt in: continue with the PIN only. */
    fun skipBiometric() {
        _uiState.update { it.copy(biometricPending = false, biometricEnabled = false) }
    }

    // --- restore from a backup ------------------------------------------------------------

    /** No app on this device can open documents: say so instead of crashing. */
    fun onRestorePickerUnavailable() {
        _uiState.update { it.copy(restoreFileError = UiText.of(R.string.backup_error_no_picker)) }
    }

    /** Reads the picked file and checks its header; the phrase is asked for only if it fits. */
    fun onRestoreFilePicked(uri: Uri?) {
        if (uri == null) return
        _uiState.update { it.copy(isLoading = true, restoreFileError = null, restorePhraseError = null) }
        viewModelScope.launch {
            val picked = withContext(Dispatchers.IO) { readBackup(uri) }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    restoreFileReady = picked.error == null,
                    restoreFileName = picked.name,
                    restoreFileError = picked.error?.let { error -> UiText.of(backupErrorMessage(error)) },
                )
            }
        }
    }

    private fun readBackup(uri: Uri): PickedBackup {
        val name = backupDocuments.displayName(uri)
        return try {
            val file = backupDocuments.read(uri)
            BackupCipher.inspect(file)
            restoreFile = file
            PickedBackup(name, error = null)
        } catch (e: BackupException) {
            restoreFile = null
            PickedBackup(name, e.error)
        }
    }

    private class PickedBackup(val name: String?, val error: BackupError?)

    /**
     * Decrypts the picked backup with [phrase] and creates the vault around that phrase. The
     * backup itself is written once the PIN is set and onboarding is committed.
     */
    fun restoreBackup(chars: CharArray) {
        val file = restoreFile
        if (file == null || _uiState.value.isLoading) {
            SensitiveDataCleaner.clear(chars)
            return
        }
        _uiState.update { it.copy(isLoading = true, restoreFileError = null, restorePhraseError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { onboardingUseCase.restoreFromBackup(file, chars) }
            result.onSuccess {
                resetPin()
                _uiState.update { it.copy(isLoading = false, step = OnboardingStep.PIN) }
            }.onFailure { e ->
                val error = (e as? BackupException)?.error ?: BackupError.IO
                val message = UiText.of(backupErrorMessage(error))
                _uiState.update {
                    if (error in PHRASE_ERRORS) {
                        it.copy(isLoading = false, restorePhraseError = message)
                    } else {
                        it.copy(isLoading = false, restoreFileError = message)
                    }
                }
            }
        }
    }

    /** The words were edited: a wrong-phrase error under them goes away. */
    fun onRestorePhraseEdited() {
        if (_uiState.value.restorePhraseError != null) _uiState.update { it.copy(restorePhraseError = null) }
    }

    private fun leaveRestore() {
        restoreFile = null
        onboardingUseCase.discardRestore()
        _uiState.update {
            it.copy(
                path = OnboardingPath.CREATE,
                restoreFileReady = false,
                restoreFileName = null,
                restoreFileError = null,
                restorePhraseError = null,
            )
        }
    }

    // --- secrets --------------------------------------------------------------------------

    private fun resetPin() {
        SensitiveDataCleaner.clear(pinEntry)
        firstPin?.let(SensitiveDataCleaner::clear)
        firstPin = null
        chosenPin?.let(SensitiveDataCleaner::clear)
        chosenPin = null
        _uiState.update { it.copy(pinLength = 0, isConfirmingPin = false, pinError = null) }
    }

    private fun wipeRecoverySeed() {
        recoverySeed?.let(SensitiveDataCleaner::clear)
        recoverySeed = null
    }

    override fun onCleared() {
        wipeRecoverySeed()
        resetPin()
        super.onCleared()
    }

    data class OnboardingUiState(
        val step: OnboardingStep = OnboardingStep.WELCOME,
        val path: OnboardingPath = OnboardingPath.CREATE,
        val securityType: SecurityType? = null,
        val isLoading: Boolean = false,
        val error: UiText? = null,
        // PIN
        val pinLength: Int = 0,
        val isConfirmingPin: Boolean = false,
        val pinError: UiText? = null,
        val biometricAvailable: Boolean = false,
        val biometricRequested: Boolean = true,
        // Recovery phrase
        val words: List<String> = emptyList(),
        val wordsHidden: Boolean = false,
        val verificationWords: List<Pair<Int, String>> = emptyList(), // word index + the word itself
        val userAnswers: Map<Int, String> = emptyMap(), // index -> user's answer
        val isVerificationComplete: Boolean = false,
        // Restore
        val restoreFileReady: Boolean = false,
        val restoreFileName: String? = null,
        val restoreFileError: UiText? = null,
        val restorePhraseError: UiText? = null,
        // Ready
        val biometricPending: Boolean = false,
        val biometricEnabled: Boolean = false,
    ) {
        /** The header's progress, or null on Welcome and Restore, which have none. */
        val progress: StepProgress?
            get() = if (path == OnboardingPath.RESTORE) {
                when (step) {
                    OnboardingStep.PIN -> StepProgress(1, RESTORE_STEPS)
                    OnboardingStep.READY -> StepProgress(RESTORE_STEPS, RESTORE_STEPS)
                    else -> null
                }
            } else {
                when (step) {
                    OnboardingStep.PIN -> StepProgress(1, CREATE_STEPS)
                    OnboardingStep.RECOVERY_INFO -> StepProgress(2, CREATE_STEPS)
                    OnboardingStep.PHRASE, OnboardingStep.VERIFY -> StepProgress(PHRASE_STEP, CREATE_STEPS)
                    OnboardingStep.READY -> StepProgress(CREATE_STEPS, CREATE_STEPS)
                    else -> null
                }
            }

        /** Whether the header shows a back button; Welcome and Ready have none. */
        val canGoBack: Boolean get() = step != OnboardingStep.WELCOME && step != OnboardingStep.READY

        /**
         * A verification field is flagged wrong only once the answer is as long as the word,
         * so the user is not told "wrong" while still typing.
         */
        fun answerState(index: Int): AnswerState =
            phraseAnswerState(verificationWords.firstOrNull { it.first == index }?.second, userAnswers[index].orEmpty())
    }

    private companion object {
        const val VERIFY_WORD_COUNT = 3
        const val MAX_DIGIT = 9
        const val CREATE_STEPS = 4
        const val PHRASE_STEP = 3
        const val RESTORE_STEPS = 2
        val PHRASE_ERRORS = setOf(BackupError.WRONG_SECRET, BackupError.INVALID_PHRASE)
    }
}
