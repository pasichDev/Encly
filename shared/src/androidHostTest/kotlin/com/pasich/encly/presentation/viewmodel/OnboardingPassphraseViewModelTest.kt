package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.common.UiText
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.passphrase_mismatch_retry
import com.pasich.encly.core.resources.passphrase_save_failed
import com.pasich.encly.core.resources.passphrase_too_short
import com.pasich.encly.core.resources.passphrase_too_simple
import com.pasich.encly.core.security.Mnemonic
import com.pasich.encly.core.security.PinChange
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.data.backup.BackupDocuments
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.NoBackupPreferences
import com.pasich.encly.data.backup.PendingRestore
import com.pasich.encly.domain.usecase.OnboardingUseCase
import com.pasich.encly.presentation.screen.onboarding.sendTo
import com.pasich.encly.presentation.screen.onboarding.suggestFrom
import com.pasich.encly.presentation.screen.pincode.PassphraseInput
import com.pasich.encly.testutil.FakePassphrasePolicy
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.testutil.InMemoryVaultDataStore
import com.pasich.encly.testutil.anyCharArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** The onboarding step machine where the vault's secret is a passphrase: choose it, check it, type it again. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingPassphraseViewModelTest {
    private val words = Mnemonic.generate()
    private lateinit var security: SecurityManager
    private val policy = FakePassphrasePolicy()

    private fun anyChars(): CharArray? = ArgumentMatchers.any()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        security = mock(SecurityManager::class.java)
        `when`(security.generateMnemonicCode()).thenAnswer { words.copyOf() }
        `when`(security.initializeNewVault(anyChars())).thenReturn(true)
        `when`(security.changePin(anyCharArray())).thenReturn(PinChange.SET)
        `when`(security.biometricAvailable()).thenReturn(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(withPolicy: Boolean = true): OnboardingViewModel {
        val backupManager =
            BackupManager(security, InMemoryVaultDataStore(), InMemoryKeyValueStore(), NoBackupPreferences)
        val useCase = OnboardingUseCase(security, backupManager, PendingRestore(backupManager))
        val documents = mock(BackupDocuments::class.java)
        return if (withPolicy) {
            OnboardingViewModel(useCase, documents, policy)
        } else {
            OnboardingViewModel(useCase, documents)
        }
    }

    private val OnboardingViewModel.state get() = uiState.value

    private fun OnboardingViewModel.submit(typed: String): Boolean = submitPassphrase(typed.toCharArray())

    @Test
    fun theKindComesFromThePolicyAndIsAPinWithoutOne() {
        assertEquals(SecretKind.PASSWORD, viewModel().state.secretKind)
        assertEquals(SecretKind.PIN, viewModel(withPolicy = false).state.secretKind)
    }

    @Test
    fun aTooShortPassphraseStaysOnTheChooseStepAndSaysWhy() {
        val vm = viewModel().also { it.getStarted() }

        assertFalse("the text stays in the field to be fixed", vm.submit("short"))

        assertFalse(vm.state.isConfirmingPin)
        assertEquals(UiText.of(Res.string.passphrase_too_short), vm.state.pinError)
    }

    @Test
    fun aTooSimplePassphraseSaysWhy() {
        val vm = viewModel().also { it.getStarted() }

        assertFalse(vm.submit("123456789012"))

        assertEquals(UiText.of(Res.string.passphrase_too_simple), vm.state.pinError)
    }

    @Test
    fun anEditClearsTheReason() {
        val vm = viewModel().also { it.getStarted() }
        vm.submit("short")

        vm.onPassphraseEdited()

        assertNull(vm.state.pinError)
        // Nothing to clear a second time.
        vm.onPassphraseEdited()
        assertNull(vm.state.pinError)
    }

    @Test
    fun aGoodPassphraseIsAskedForAgain() {
        val vm = viewModel().also { it.getStarted() }

        assertTrue(vm.submit(FakePassphrasePolicy.GOOD))

        assertTrue(vm.state.isConfirmingPin)
        assertNull(vm.state.pinError)
        assertEquals(OnboardingStep.PIN, vm.state.step)
    }

    @Test
    fun aDifferentSecondEntryStartsOverWithTheMismatchWording() {
        val vm = viewModel().also { it.getStarted() }
        vm.submit(FakePassphrasePolicy.GOOD)

        assertTrue(vm.submit("correct horse batterz"))

        assertEquals(OnboardingStep.PIN, vm.state.step)
        assertFalse(vm.state.isConfirmingPin)
        assertEquals(UiText.of(Res.string.passphrase_mismatch_retry), vm.state.pinError)
    }

    @Test
    fun aMatchingSecondEntryMovesOnAndTheVaultGetsThePassphraseAtTheEnd() = runTest {
        val vm = viewModel().also { it.getStarted() }
        vm.submit(FakePassphrasePolicy.GOOD)

        assertTrue(vm.submit(FakePassphrasePolicy.GOOD))
        assertEquals(OnboardingStep.RECOVERY_INFO, vm.state.step)
        verify(security, never()).changePin(anyCharArray())

        vm.skipPhrase()
        vm.uiState.first { !it.isLoading }

        assertEquals(OnboardingStep.READY, vm.state.step)
        verify(security).changePin(FakePassphrasePolicy.GOOD.toCharArray())
    }

    @Test
    fun nothingTypedIsIgnored() {
        val vm = viewModel().also { it.getStarted() }

        assertFalse(vm.submitPassphrase(CharArray(0)))

        assertFalse(vm.state.isConfirmingPin)
        assertNull(vm.state.pinError)
    }

    @Test
    fun anEntryWhileSomethingRunsIsIgnoredAndWiped() {
        val release = java.util.concurrent.CountDownLatch(1)
        `when`(security.initializeNewVault(anyChars())).thenAnswer {
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            true
        }
        val vm = viewModel().also { it.getStarted() }
        vm.submit(FakePassphrasePolicy.GOOD)
        vm.submit(FakePassphrasePolicy.GOOD)
        vm.skipPhrase()
        val typed = FakePassphrasePolicy.GOOD.toCharArray()

        assertTrue(vm.state.isLoading)
        assertFalse(vm.submitPassphrase(typed))
        release.countDown()
        runBlocking { vm.uiState.first { !it.isLoading } }

        assertArrayEquals(CharArray(typed.size), typed)
    }

    @Test
    fun theSuggestionFillsTheFieldInClearAndTheArrayIsWiped() {
        val input = PassphraseInput()

        input.suggestFrom(viewModel())

        assertEquals(FakePassphrasePolicy.SUGGESTION, input.text)
        assertTrue(input.revealed)
        assertTrue(input.suggested)
    }

    @Test
    fun aPinHasNoSuggestionToFill() {
        val input = PassphraseInput()

        input.suggestFrom(viewModel(withPolicy = false))

        assertEquals("", input.text)
    }

    @Test
    fun sendingTheFieldGivesTheViewModelTheTextAndEmptiesItWhenUsed() {
        val vm = viewModel().also { it.getStarted() }
        val input = PassphraseInput().apply { edit(FakePassphrasePolicy.GOOD) }

        input.sendTo(vm)

        assertEquals("", input.text)
        assertTrue(vm.state.isConfirmingPin)
    }

    @Test
    fun aRefusedEntryIsWipedBeforeTheScreenGetsItsAnswer() {
        val vm = viewModel().also { it.getStarted() }
        val refused = "short".toCharArray()

        vm.submitPassphrase(refused)

        assertArrayEquals(CharArray(refused.size), refused)
    }

    @Test
    fun aFailedSaveSaysSoInThePassphraseWording() = runTest {
        `when`(security.changePin(anyCharArray())).thenReturn(PinChange.FAILED)
        val vm = viewModel().also { it.getStarted() }
        vm.submit(FakePassphrasePolicy.GOOD)
        vm.submit(FakePassphrasePolicy.GOOD)

        vm.skipPhrase()
        vm.uiState.first { !it.isLoading }

        assertEquals(UiText.of(Res.string.passphrase_save_failed), vm.state.error)
    }

    @Test
    fun aPassphraseIsSuggestedAndAPinIsNot() {
        assertEquals(FakePassphrasePolicy.SUGGESTION, String(viewModel().suggestPassphrase()!!))
        assertNull(viewModel(withPolicy = false).suggestPassphrase())
    }
}
