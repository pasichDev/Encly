package com.pasich.encly.presentation.viewmodel

import android.content.Context
import com.pasich.encly.R
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.BackupDocuments
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.BackupPhraseSetup
import com.pasich.encly.testutil.InMemorySharedPreferences
import com.pasich.encly.testutil.InMemoryVaultDataStore
import com.pasich.encly.testutil.anyString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private lateinit var security: SecurityManager
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        security = mock(SecurityManager::class.java)
        viewModel = BackupViewModel(
            backupManager = BackupManager(security, InMemoryVaultDataStore(), InMemorySharedPreferences()),
            phraseSetup = BackupPhraseSetup(security),
            securityManager = security,
            sessionLockManager = SessionLockManager(security),
            documents = BackupDocuments(mock(Context::class.java)),
        )
        viewModel.start(BackupAction.IMPORT)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theRightPinDuringALockoutIsNotReportedAsWrong() {
        `when`(security.pinLockoutRemainingMillis()).thenReturn(30_000L)

        viewModel.reauthFlow.submitPin("123456")

        val step = viewModel.uiState.value.step as BackupStep.Reauth
        assertNull(step.error)
        // Nothing is verified, so the lockout is neither extended nor reported as a wrong PIN.
        verify(security, never()).verifyPin(anyString())
    }

    @Test
    fun aWrongPinOutsideALockoutIsReported() {
        `when`(security.verifyPin(anyString())).thenReturn(false)

        viewModel.reauthFlow.submitPin("000000")

        waitForStep { (it as? BackupStep.Reauth)?.error != null }
        assertEquals(R.string.lock_wrong_pin, (viewModel.uiState.value.step as BackupStep.Reauth).error)
    }

    @Test
    fun theRightPinOutsideALockoutProceeds() {
        `when`(security.verifyPin(anyString())).thenReturn(true)

        viewModel.reauthFlow.submitPin("123456")

        waitForStep { it == BackupStep.PickImportFile }
    }

    @Test
    fun creatingARecoveryPhraseLaterShowsNewWordsAfterThePin() {
        `when`(security.verifyPin(anyString())).thenReturn(true)
        `when`(security.hasRecoverySeed()).thenReturn(false)
        `when`(security.generateMnemonicCode()).thenReturn(WORDS.toCharArray())
        viewModel.start(BackupAction.CREATE_PHRASE)

        viewModel.reauthFlow.submitPin("123456")

        waitForStep { it is BackupStep.ShowNewPhrase }
        assertEquals(WORDS.split(' '), (viewModel.uiState.value.step as BackupStep.ShowNewPhrase).words)
    }

    @Test
    fun eraseNeedsThePinAndAnExplicitConfirmation() {
        `when`(security.verifyPin(anyString())).thenReturn(true)
        viewModel.start(BackupAction.ERASE)

        viewModel.eraseAllData() // not confirmed yet: ignored
        verify(security, never()).wipeAndReset()

        viewModel.reauthFlow.submitPin("123456")
        waitForStep { it == BackupStep.ConfirmErase }
        viewModel.eraseAllData()

        waitForStep { it == BackupStep.Idle }
        verify(security).wipeAndReset()
        assertTrue(viewModel.uiState.value.erased)
    }

    @Test
    fun aDeviceWithoutAPickerEndsTheFlowWithAMessage() {
        viewModel.onSystemPickerUnavailable()

        assertEquals(BackupStep.Idle, viewModel.uiState.value.step)
        assertEquals(
            BackupMessage.Text(R.string.backup_error_no_picker),
            viewModel.uiState.value.message,
        )
    }

    /**
     * verifyPin runs on Dispatchers.Default, a real thread. Waits for the whole busy block to
     * end too, so nothing touches Dispatchers.Main after [tearDown] resets it.
     */
    private fun waitForStep(matches: (BackupStep) -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!matches(viewModel.uiState.value.step) || viewModel.uiState.value.busy) {
            check(System.currentTimeMillis() < deadline) { "step is ${viewModel.uiState.value.step}" }
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
        const val WORDS = "one two three four five six seven eight nine ten eleven twelve"
    }
}
