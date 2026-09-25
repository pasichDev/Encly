package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import com.pasich.encly.R
import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.data.backup.BackupDocuments
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.PendingRestore
import com.pasich.encly.domain.usecase.OnboardingUseCase
import com.pasich.encly.testutil.InMemorySharedPreferences
import com.pasich.encly.testutil.InMemoryVaultDataStore
import com.pasich.encly.testutil.answerCallback
import com.pasich.encly.testutil.anyCallback
import com.pasich.encly.testutil.anyCharArray
import com.pasich.encly.testutil.eqValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** Onboarding when something fails, plus the small switches of its steps. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingFailuresTest {
    private val words = MnemonicCode(WordCount.COUNT_12).chars
    private lateinit var security: SecurityManager
    private val activity: FragmentActivity = mock(FragmentActivity::class.java)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        security = mock(SecurityManager::class.java)
        `when`(security.generateMnemonicCode()).thenAnswer { words.copyOf() }
        `when`(security.initializeNewVault(anyChars())).thenReturn(true)
        `when`(security.configurePin(anyCharArray())).thenReturn(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aVaultThatCannotBeCreatedOnSkipShowsAnErrorAndStays() = runTest {
        `when`(security.initializeNewVault(anyChars())).thenReturn(false)
        val vm = atRecoveryInfo()

        vm.skipPhrase()
        val state = vm.uiState.first { !it.isLoading }

        assertEquals(OnboardingStep.RECOVERY_INFO, state.step)
        assertEquals(UiText.of(R.string.onboarding_error_initialization), state.error)
        verify(security, never()).configurePin(anyCharArray())
    }

    @Test
    fun aVaultThatCannotBeCreatedAfterTheCheckShowsAnErrorAndCanBeRetried() = runTest {
        `when`(security.initializeNewVault(anyChars())).thenReturn(false)
        val vm = atRecoveryInfo()
        vm.createPhrase()
        vm.startSeedPhraseVerification()
        vm.uiState.value.verificationWords.forEach { (index, word) -> vm.updateUserAnswer(index, word) }

        vm.completeVerification()
        val failed = vm.uiState.first { !it.isLoading }
        assertEquals(UiText.of(R.string.onboarding_error_vault_creation), failed.error)
        assertEquals(OnboardingStep.VERIFY, failed.step)

        `when`(security.initializeNewVault(anyChars())).thenReturn(true)
        vm.completeVerification()

        assertEquals(OnboardingStep.READY, vm.uiState.first { it.step == OnboardingStep.READY }.step)
    }

    @Test
    fun wordsThatCannotBeGeneratedShowAnError() {
        `when`(security.generateMnemonicCode()).thenThrow(IllegalStateException("no entropy"))
        val vm = atRecoveryInfo()

        vm.createPhrase()

        assertTrue(vm.uiState.value.words.isEmpty())
        assertEquals(UiText.of(R.string.onboarding_error_seed_generation), vm.uiState.value.error)
    }

    @Test
    fun theWordsCanBeHiddenAndShownAgain() {
        val vm = atRecoveryInfo()
        vm.createPhrase()
        val hidden = vm.uiState.value.wordsHidden

        vm.toggleWordsHidden()
        assertEquals(!hidden, vm.uiState.value.wordsHidden)
        vm.toggleWordsHidden()
        assertEquals(hidden, vm.uiState.value.wordsHidden)
    }

    @Test
    fun answersAreComparedTrimmedAndIgnoringCase() {
        val vm = atRecoveryInfo()
        vm.createPhrase()
        vm.startSeedPhraseVerification()
        val checks = vm.uiState.value.verificationWords

        checks.forEach { (index, word) -> vm.updateUserAnswer(index, "  ${word.uppercase()} ") }

        assertTrue(vm.uiState.value.isVerificationComplete)
    }

    @Test
    fun oneWrongAnswerKeepsTheCheckOpen() {
        val vm = atRecoveryInfo()
        vm.createPhrase()
        vm.startSeedPhraseVerification()
        val checks = vm.uiState.value.verificationWords

        checks.forEach { (index, word) -> vm.updateUserAnswer(index, word) }
        vm.updateUserAnswer(checks.first().first, "zzz")
        vm.completeVerification()

        assertFalse(vm.uiState.value.isVerificationComplete)
        verify(security, never()).initializeNewVault(anyChars())
    }

    @Test
    fun skippingTheFingerprintContinuesWithThePinOnly() = runTest {
        val vm = readyWithBiometricPending()

        vm.skipBiometric()

        assertFalse(vm.uiState.value.biometricPending)
        assertFalse(vm.uiState.value.biometricEnabled)
        verify(security, never()).enrollBiometric(eqValue(activity), anyCallback())
    }

    @Test
    fun theFingerprintPromptIsShownOnceAndItsResultKept() = runTest {
        answerCallback(true).`when`(security).enrollBiometric(eqValue(activity), anyCallback())
        val vm = readyWithBiometricPending()

        vm.enrollBiometric(activity)
        vm.enrollBiometric(activity)

        assertTrue(vm.uiState.value.biometricEnabled)
        verify(security).enrollBiometric(eqValue(activity), anyCallback())
    }

    @Test
    fun noDocumentPickerIsReportedOnTheRestoreStep() {
        val vm = viewModel()
        vm.startRestore()

        vm.onRestorePickerUnavailable()

        assertEquals(UiText.of(R.string.backup_error_no_picker), vm.uiState.value.restoreFileError)
    }

    @Test
    fun wordsTypedBeforeAFileIsPickedAreIgnored() {
        val vm = viewModel()
        vm.startRestore()
        vm.onRestoreFilePicked(null)

        vm.restoreBackup(String(words).toCharArray())

        assertFalse(vm.uiState.value.isLoading)
        verify(security, never()).initializeNewVault(anyChars())
    }

    @Test
    fun anAnswerIsWrongOnlyOnceItIsAsLongAsTheWord() {
        assertEquals(AnswerState.EMPTY, phraseAnswerState("orbit", "  "))
        assertEquals(AnswerState.TYPING, phraseAnswerState("orbit", "orb"))
        assertEquals(AnswerState.TYPING, phraseAnswerState("orbit", "xyz"))
        assertEquals(AnswerState.WRONG, phraseAnswerState("orbit", "orbix"))
        assertEquals(AnswerState.CORRECT, phraseAnswerState("orbit", " ORBIT "))
        assertEquals(AnswerState.TYPING, phraseAnswerState(null, "orbit"))
    }

    private fun viewModel(biometric: Boolean = false): OnboardingViewModel {
        `when`(security.biometricAvailable()).thenReturn(biometric)
        val backupManager = BackupManager(security, InMemoryVaultDataStore(), InMemorySharedPreferences())
        return OnboardingViewModel(
            OnboardingUseCase(security, backupManager, PendingRestore(backupManager)),
            mock(BackupDocuments::class.java),
        )
    }

    private fun atRecoveryInfo(): OnboardingViewModel = viewModel().apply {
        getStarted()
        repeat(2) {
            PIN.forEach { onPinDigit(it.digitToInt()) }
            submitPin()
        }
        check(uiState.value.step == OnboardingStep.RECOVERY_INFO) { "at ${uiState.value.step}" }
    }

    private suspend fun readyWithBiometricPending(): OnboardingViewModel {
        val vm = viewModel(biometric = true)
        vm.getStarted()
        vm.setBiometricRequested(true)
        repeat(2) {
            PIN.forEach { vm.onPinDigit(it.digitToInt()) }
            vm.submitPin()
        }
        vm.skipPhrase()
        vm.uiState.first { it.step == OnboardingStep.READY && !it.isLoading }
        check(vm.uiState.value.biometricPending) { "no biometric pending" }
        return vm
    }

    private fun anyChars(): CharArray? = ArgumentMatchers.any()

    private companion object {
        const val PIN = "482915"
    }
}
