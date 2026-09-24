package com.pasich.encly.presentation.viewmodel

import android.net.Uri
import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import com.pasich.encly.R
import com.pasich.encly.core.backup.BackupCipher
import com.pasich.encly.core.backup.BackupPayloadCodec
import com.pasich.encly.core.backup.BackupSecret
import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.data.backup.BackupDocuments
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.BackupMapper
import com.pasich.encly.data.backup.PendingRestore
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.usecase.OnboardingUseCase
import com.pasich.encly.testutil.InMemorySharedPreferences
import com.pasich.encly.testutil.InMemoryVaultDataStore
import com.pasich.encly.testutil.anyString
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** The onboarding step machine: order, back, skip, verification and the restore path. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val words = MnemonicCode(WordCount.COUNT_12).chars
    private val wordList = String(words).split(" ")
    private lateinit var security: SecurityManager
    private lateinit var store: InMemoryVaultDataStore
    private lateinit var pending: PendingRestore
    private lateinit var documents: BackupDocuments
    private var vaultSeed: String? = "not created"

    private fun anyChars(): CharArray? = ArgumentMatchers.any()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        security = mock(SecurityManager::class.java)
        `when`(security.generateMnemonicCode()).thenAnswer { words.copyOf() }
        `when`(security.initializeNewVault(anyChars())).thenAnswer {
            vaultSeed = it.getArgument<CharArray?>(0)?.let(::String)
            true
        }
        `when`(security.configurePin(anyString())).thenReturn(true)
        `when`(security.isValidRecoveryPhrase(anyChars() ?: CharArray(0))).thenAnswer {
            runCatching { MnemonicCode(it.getArgument<CharArray>(0).copyOf()).validate() }.isSuccess
        }
        store = InMemoryVaultDataStore()
        documents = mock(BackupDocuments::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(biometric: Boolean = false): OnboardingViewModel {
        `when`(security.biometricAvailable()).thenReturn(biometric)
        val backupManager = BackupManager(security, store, InMemorySharedPreferences())
        pending = PendingRestore(backupManager)
        return OnboardingViewModel(OnboardingUseCase(security, backupManager, pending), documents)
    }

    private val OnboardingViewModel.state get() = uiState.value

    private suspend fun OnboardingViewModel.settled() = uiState.first { !it.isLoading }

    private fun OnboardingViewModel.typePin(pin: String) = pin.forEach { onPinDigit(it.digitToInt()) }

    private fun OnboardingViewModel.choosePin(pin: String = "123456") {
        typePin(pin)
        submitPin()
        typePin(pin)
        submitPin()
    }

    private fun OnboardingViewModel.answerAll() = state.verificationWords.forEach { (index, word) ->
        updateUserAnswer(index, word)
    }

    // --- the create path ---------------------------------------------------------------------

    @Test
    fun theFlowStartsOnWelcomeWithoutProgress() {
        val vm = viewModel()

        assertEquals(OnboardingStep.WELCOME, vm.state.step)
        assertNull(vm.state.progress)
        assertFalse(vm.state.canGoBack)
    }

    @Test
    fun theCreatePathRunsPinPhraseCheckReadyAndWritesOnlyAtTheEnd() = runTest {
        val vm = viewModel()

        vm.getStarted()
        assertEquals(OnboardingStep.PIN, vm.state.step)
        assertEquals(StepProgress(1, 4), vm.state.progress)

        vm.choosePin()
        assertEquals(OnboardingStep.RECOVERY_INFO, vm.state.step)
        assertEquals(StepProgress(2, 4), vm.state.progress)

        vm.createPhrase()
        assertEquals(OnboardingStep.PHRASE, vm.state.step)
        assertEquals(StepProgress(3, 4), vm.state.progress)
        assertEquals(wordList, vm.state.words)

        vm.startSeedPhraseVerification()
        assertEquals(OnboardingStep.VERIFY, vm.state.step)
        assertEquals(StepProgress(3, 4), vm.state.progress)
        val indices = vm.state.verificationWords.map { it.first }
        assertEquals("three distinct words, in ascending order", indices.distinct().sorted(), indices)
        assertEquals(3, indices.size)

        // Nothing is written until the check passes.
        verify(security, never()).initializeNewVault(anyChars())
        verify(security, never()).configurePin(anyString())

        vm.answerAll()
        assertTrue(vm.state.isVerificationComplete)
        vm.completeVerification()
        vm.settled()

        assertEquals(OnboardingStep.READY, vm.state.step)
        assertEquals(StepProgress(4, 4), vm.state.progress)
        assertFalse("Ready has no way back", vm.state.canGoBack)
        assertEquals(SecurityType.USER_MANAGED, vm.state.securityType)
        assertEquals(String(words), vaultSeed)
        assertTrue("the words are dropped once the vault exists", vm.state.words.isEmpty())
        val order = inOrder(security)
        order.verify(security).initializeNewVault(anyChars())
        order.verify(security).configurePin("123456")
    }

    @Test
    fun differentPinsStartTheEntryOver() {
        val vm = viewModel()
        vm.getStarted()

        vm.typePin("123456")
        vm.submitPin()
        assertTrue(vm.state.isConfirmingPin)
        vm.typePin("654321")
        vm.submitPin()

        assertEquals(OnboardingStep.PIN, vm.state.step)
        assertFalse(vm.state.isConfirmingPin)
        assertEquals(0, vm.state.pinLength)
        assertEquals(UiText.of(R.string.pin_mismatch_retry), vm.state.pinError)
    }

    @Test
    fun thePinTakesSixDigitsAndContinueNeedsAllOfThem() {
        val vm = viewModel()
        vm.getStarted()

        vm.typePin("1234567")
        assertEquals(6, vm.state.pinLength)
        vm.onPinBackspace()
        vm.submitPin()

        assertEquals(5, vm.state.pinLength)
        assertFalse("five digits do not continue", vm.state.isConfirmingPin)
    }

    @Test
    fun backWalksTheStepsInReverse() {
        val vm = viewModel()
        vm.getStarted()
        vm.choosePin()
        vm.createPhrase()
        vm.startSeedPhraseVerification()

        vm.back()
        assertEquals(OnboardingStep.PHRASE, vm.state.step)
        assertTrue(vm.state.verificationWords.isEmpty())
        vm.back()
        assertEquals(OnboardingStep.RECOVERY_INFO, vm.state.step)
        vm.back()
        assertEquals(OnboardingStep.PIN, vm.state.step)
        assertFalse("the PIN is chosen again", vm.state.isConfirmingPin)
        assertEquals(0, vm.state.pinLength)
        vm.back()
        assertEquals(OnboardingStep.WELCOME, vm.state.step)
        vm.back()
        assertEquals(OnboardingStep.WELCOME, vm.state.step)
    }

    @Test
    fun theWordsStayTheSameWhenComingBackToThem() {
        val vm = viewModel()
        vm.getStarted()
        vm.choosePin()
        vm.createPhrase()
        vm.back()
        vm.createPhrase()

        assertEquals(wordList, vm.state.words)
        verify(security).generateMnemonicCode()
    }

    @Test
    fun skippingThePhraseCreatesAVaultWithoutRecoveryAndGoesToReady() = runTest {
        val vm = viewModel()
        vm.getStarted()
        vm.choosePin()

        vm.skipPhrase()
        vm.settled()

        assertEquals(OnboardingStep.READY, vm.state.step)
        assertEquals(SecurityType.AUTO_MANAGED, vm.state.securityType)
        assertNull("no recovery seed", vaultSeed)
        verify(security).configurePin("123456")
    }

    @Test
    fun wrongWordsDoNotPassTheCheck() = runTest {
        val vm = viewModel()
        vm.getStarted()
        vm.choosePin()
        vm.createPhrase()
        vm.startSeedPhraseVerification()
        val (first, word) = vm.state.verificationWords.first()

        vm.updateUserAnswer(first, word.take(1))
        assertEquals("still typing", AnswerState.TYPING, vm.state.answerState(first))
        vm.updateUserAnswer(first, "z".repeat(word.length))
        assertEquals(AnswerState.WRONG, vm.state.answerState(first))
        vm.state.verificationWords.drop(1).forEach { (index, right) -> vm.updateUserAnswer(index, right) }

        assertFalse(vm.state.isVerificationComplete)
        vm.completeVerification()
        assertEquals(OnboardingStep.VERIFY, vm.state.step)
        verify(security, never()).initializeNewVault(anyChars())

        vm.updateUserAnswer(first, " ${word.uppercase()} ")
        assertEquals("trimmed and case-insensitive", AnswerState.CORRECT, vm.state.answerState(first))
        assertTrue(vm.state.isVerificationComplete)
    }

    @Test
    fun aPinSlotThatFailsKeepsTheStepSoTheSameButtonRetries() = runTest {
        `when`(security.configurePin(anyString())).thenReturn(false)
        val vm = viewModel()
        vm.getStarted()
        vm.choosePin()
        vm.createPhrase()
        vm.startSeedPhraseVerification()
        vm.answerAll()

        vm.completeVerification()
        vm.settled()
        assertEquals(OnboardingStep.VERIFY, vm.state.step)
        assertEquals(UiText.of(R.string.pin_save_failed), vm.state.error)

        `when`(security.configurePin(anyString())).thenReturn(true)
        vm.completeVerification()
        vm.settled()
        assertEquals(OnboardingStep.READY, vm.state.step)
        verify(security, times(2)).configurePin("123456")
    }

    @Test
    fun biometricEnrolmentIsRequestedAtReadyOnlyWhenWantedAndAvailable() = runTest {
        val wanted = viewModel(biometric = true)
        wanted.getStarted()
        assertTrue("the switch defaults on", wanted.state.biometricRequested)
        wanted.choosePin()
        wanted.skipPhrase()
        wanted.settled()
        assertTrue(wanted.state.biometricPending)

        val declined = viewModel(biometric = true)
        declined.getStarted()
        declined.setBiometricRequested(false)
        declined.choosePin()
        declined.skipPhrase()
        declined.settled()
        assertFalse(declined.state.biometricPending)

        val unavailable = viewModel(biometric = false)
        unavailable.getStarted()
        unavailable.choosePin()
        unavailable.skipPhrase()
        unavailable.settled()
        assertFalse(unavailable.state.biometricPending)
    }

    // --- the restore path --------------------------------------------------------------------

    private suspend fun backupFile(): ByteArray {
        val source = InMemoryVaultDataStore().apply { insertNote(Note(title = "Plan", value = "[]", uid = "n-plan")) }
        val plaintext = BackupPayloadCodec.encode(BackupMapper.toPayload(source.snapshot(), exportedAt = 1))
        return BackupCipher.seal(plaintext, BackupSecret.RecoveryPhrase(words.copyOf()))
    }

    private suspend fun OnboardingViewModel.pickBackup() {
        val uri = mock(Uri::class.java)
        `when`(documents.read(uri)).thenReturn(backupFile())
        `when`(documents.displayName(uri)).thenReturn("encly.enclybak")
        onRestoreFilePicked(uri)
        settled()
    }

    @Test
    fun theRestorePathRestoresThenSetsThePinAndSkipsThePhrase() = runTest {
        val vm = viewModel()
        vm.startRestore()
        assertEquals(OnboardingStep.RESTORE, vm.state.step)
        assertNull("the restore screen has a label, not progress", vm.state.progress)

        vm.pickBackup()
        assertTrue(vm.state.restoreFileReady)
        assertEquals("encly.enclybak", vm.state.restoreFileName)

        vm.restoreBackup(words.copyOf())
        vm.settled()
        assertEquals(OnboardingStep.PIN, vm.state.step)
        assertEquals(StepProgress(1, 2), vm.state.progress)
        assertEquals(String(words), vaultSeed)
        verify(security, never()).configurePin(anyString())

        vm.choosePin()
        vm.settled()
        assertEquals(OnboardingStep.READY, vm.state.step)
        assertEquals(StepProgress(2, 2), vm.state.progress)
        verify(security).configurePin("123456")
        assertTrue("the backup is imported when setup is committed", store.notes.isEmpty())
        assertTrue(pending.apply())
        assertEquals(listOf("n-plan"), store.notes.map { it.uid })
    }

    @Test
    fun wrongWordsAreReportedUnderThePhraseAndCreateNothing() = runTest {
        val vm = viewModel()
        vm.startRestore()
        vm.pickBackup()

        vm.restoreBackup(MnemonicCode(WordCount.COUNT_12).chars.copyOf())
        vm.settled()

        assertEquals(OnboardingStep.RESTORE, vm.state.step)
        assertEquals(UiText.of(R.string.backup_error_wrong_phrase), vm.state.restorePhraseError)
        assertNull(vm.state.restoreFileError)
        verify(security, never()).initializeNewVault(anyChars())
    }

    @Test
    fun editingTheWordsClearsTheWrongPhraseError() = runTest {
        val vm = viewModel()
        vm.startRestore()
        vm.pickBackup()
        vm.restoreBackup(MnemonicCode(WordCount.COUNT_12).chars.copyOf())
        vm.settled()

        vm.onRestorePhraseEdited()

        assertNull(vm.state.restorePhraseError)
    }

    @Test
    fun theWordsAreWipedAfterARestore() = runTest {
        val vm = viewModel()
        vm.startRestore()
        vm.pickBackup()
        val typed = words.copyOf()

        vm.restoreBackup(typed)
        vm.settled()

        assertTrue(typed.all { it == 0.toChar() })
    }

    @Test
    fun aFileThatIsNotABackupIsReportedUnderThePicker() = runTest {
        val vm = viewModel()
        vm.startRestore()
        val uri = mock(Uri::class.java)
        `when`(documents.read(uri)).thenReturn(ByteArray(64) { 7 })

        vm.onRestoreFilePicked(uri)
        vm.settled()

        assertFalse(vm.state.restoreFileReady)
        assertTrue(vm.state.restoreFileError != null)
    }

    @Test
    fun leavingTheRestorePathDropsTheStagedBackup() = runTest {
        val vm = viewModel()
        vm.startRestore()
        vm.pickBackup()
        vm.restoreBackup(words.copyOf())
        vm.settled()

        vm.back()
        assertEquals("back from the PIN returns to the restore screen", OnboardingStep.RESTORE, vm.state.step)
        vm.back()
        assertEquals(OnboardingStep.WELCOME, vm.state.step)
        assertEquals(OnboardingPath.CREATE, vm.state.path)
        assertFalse(vm.state.restoreFileReady)

        assertTrue(pending.apply())
        assertTrue("nothing staged any more", store.notes.isEmpty())
    }
}
