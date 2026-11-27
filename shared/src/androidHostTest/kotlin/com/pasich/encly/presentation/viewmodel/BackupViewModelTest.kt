package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.backup_error_no_picker
import com.pasich.encly.core.resources.backup_phrase_exists
import com.pasich.encly.core.resources.keyring_not_running
import com.pasich.encly.core.resources.lock_wrong_pin
import com.pasich.encly.core.resources.security_erase_failed
import com.pasich.encly.core.security.AutoLockDelay
import com.pasich.encly.core.security.KeyStoreProblem
import com.pasich.encly.core.security.PinCheck
import com.pasich.encly.core.security.ScreenshotAllowance
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.BackupDocuments
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.BackupPhraseSetup
import com.pasich.encly.data.backup.NoBackupPreferences
import com.pasich.encly.testutil.FakeDocuments
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.testutil.InMemoryVaultDataStore
import com.pasich.encly.testutil.anyCharArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val screenshots = ScreenshotAllowance()
    private lateinit var sessionLock: SessionLockManager

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        security = mock(SecurityManager::class.java)
        sessionLock = SessionLockManager(security, screenshots = screenshots)
        viewModel = BackupViewModel(
            backupManager = BackupManager(
                security,
                InMemoryVaultDataStore(),
                InMemoryKeyValueStore(),
                NoBackupPreferences,
            ),
            phraseSetup = BackupPhraseSetup(security),
            securityManager = security,
            sessionLockManager = sessionLock,
            documents = BackupDocuments(FakeDocuments()),
            screenshots = screenshots,
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

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        val step = viewModel.uiState.value.step as BackupStep.Reauth
        assertNull(step.error)
        // Nothing is verified, so the lockout is neither extended nor reported as a wrong PIN.
        verify(security, never()).verifyPinResult(anyCharArray())
    }

    @Test
    fun theSubmittedPinIsWipedWhetherItIsCheckedOrNot() {
        `when`(security.pinLockoutRemainingMillis()).thenReturn(30_000L)
        val refused = "123456".toCharArray()
        viewModel.reauthFlow.submitPin(refused)
        assertArrayEquals(CharArray(refused.size), refused)

        `when`(security.pinLockoutRemainingMillis()).thenReturn(0L)
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.WRONG)
        val checked = "000000".toCharArray()
        viewModel.reauthFlow.submitPin(checked)
        waitForStep { (it as? BackupStep.Reauth)?.error != null }
        assertArrayEquals(CharArray(checked.size), checked)
    }

    @Test
    fun aWrongPinOutsideALockoutIsReported() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.WRONG)

        viewModel.reauthFlow.submitPin("000000".toCharArray())

        waitForStep { (it as? BackupStep.Reauth)?.error != null }
        assertEquals(Res.string.lock_wrong_pin, (viewModel.uiState.value.step as BackupStep.Reauth).error)
    }

    @Test
    fun theRightPinOutsideALockoutProceeds() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it == BackupStep.PickImportFile }
    }

    @Test
    fun creatingARecoveryPhraseLaterShowsNewWordsAfterThePin() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        `when`(security.hasRecoverySeed()).thenReturn(false)
        `when`(security.generateMnemonicCode()).thenReturn(WORDS.toCharArray())
        viewModel.start(BackupAction.CREATE_PHRASE)

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it is BackupStep.ShowNewPhrase }
        assertEquals(WORDS.split(' '), (viewModel.uiState.value.step as BackupStep.ShowNewPhrase).words)
    }

    @Test
    fun eachWrongPinCountsAFailureSoTheDotsShakeAgain() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.WRONG)

        viewModel.reauthFlow.submitPin("000000".toCharArray())
        waitForStep { (it as? BackupStep.Reauth)?.failures == 1 }
        viewModel.reauthFlow.submitPin("000000".toCharArray())

        waitForStep { (it as? BackupStep.Reauth)?.failures == 2 }
    }

    @Test
    fun creatingARecoveryPhraseWhenOneExistsSaysSo() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        `when`(security.hasRecoverySeed()).thenReturn(true)
        viewModel.start(BackupAction.CREATE_PHRASE)

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it == BackupStep.Idle }
        assertEquals(BackupMessage.Text(Res.string.backup_phrase_exists), viewModel.uiState.value.message)
    }

    @Test
    fun theCheckKnowsItsWordsAndCanGoBackToThem() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        `when`(security.hasRecoverySeed()).thenReturn(false)
        `when`(security.generateMnemonicCode()).thenReturn(WORDS.toCharArray())
        viewModel.start(BackupAction.CREATE_PHRASE)
        viewModel.reauthFlow.submitPin("123456".toCharArray())
        waitForStep { it is BackupStep.ShowNewPhrase }

        viewModel.phraseFlow.writtenDown()
        val check = viewModel.uiState.value.step as BackupStep.CheckNewPhrase
        assertEquals(check.positions.map(WORDS.split(' ')::get), check.expected)

        viewModel.phraseFlow.showAgain()
        assertEquals(WORDS.split(' '), (viewModel.uiState.value.step as BackupStep.ShowNewPhrase).words)
    }

    @Test
    fun eraseNeedsThePinThenRunsAtOnce() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        `when`(security.wipeAndReset()).thenReturn(true)
        viewModel.start(BackupAction.ERASE)

        viewModel.eraseAllData() // not re-authenticated yet: ignored
        verify(security, never()).wipeAndReset()

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it == BackupStep.Idle && viewModel.uiState.value.erased }
        verify(security).wipeAndReset()
        assertTrue(viewModel.uiState.value.erased)
    }

    @Test
    fun eraseAllDataIsToldToEveryWindow() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        `when`(security.wipeAndReset()).thenReturn(true)
        viewModel.start(BackupAction.ERASE)

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it == BackupStep.Idle && viewModel.uiState.value.erased }
        assertEquals(1L, sessionLock.erasedCount.value)
        assertFalse(sessionLock.locked.value)
    }

    @Test
    fun aRefusedEraseIsNotToldToTheOtherWindows() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        `when`(security.wipeAndReset()).thenReturn(false)
        viewModel.start(BackupAction.ERASE)

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it == BackupStep.Idle && viewModel.uiState.value.message != null }
        assertEquals(0L, sessionLock.erasedCount.value)
        assertFalse(sessionLock.locked.value)
    }

    @Test
    fun cancelDuringTheCheckCancelsTheEraseAndTheScreenshotAllowance() {
        for (action in listOf(BackupAction.ERASE, BackupAction.ALLOW_SCREENSHOTS)) {
            val checking = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            `when`(security.wipeAndReset()).thenReturn(true)
            `when`(security.verifyPinResult(anyCharArray())).thenAnswer {
                checking.countDown()
                release.await(5, java.util.concurrent.TimeUnit.SECONDS)
                PinCheck.OK
            }
            viewModel.start(action)

            viewModel.reauthFlow.submitPin("123456".toCharArray())
            assertTrue(checking.await(5, java.util.concurrent.TimeUnit.SECONDS))
            viewModel.cancel()
            release.countDown()

            waitForStep { it == BackupStep.Idle }
            assertEquals(BackupStep.Idle, viewModel.uiState.value.step)
            assertFalse("the allowance was not granted", screenshots.allowed.value)
            assertFalse("nothing was erased", viewModel.uiState.value.erased)
            verify(security, never()).wipeAndReset()
        }
    }

    @Test
    fun theKeyStoreThatDidNotAnswerIsNotReportedAsAWrongPinAndKeepsTheStep() {
        `when`(security.keyStoreProblem()).thenReturn(KeyStoreProblem.NOT_RUNNING)
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.KEY_STORE_UNAVAILABLE)
        viewModel.start(BackupAction.EXPORT)

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it is BackupStep.Reauth && it.error != null }
        assertEquals(Res.string.keyring_not_running, (viewModel.uiState.value.step as BackupStep.Reauth).error)

        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.WRONG)
        viewModel.reauthFlow.submitPin("123456".toCharArray())
        waitForStep { it is BackupStep.Reauth && it.error == Res.string.lock_wrong_pin }
    }

    @Test
    fun allowingScreenshotsNeedsReauthenticationAndNothingElse() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.WRONG)
        viewModel.start(BackupAction.ALLOW_SCREENSHOTS)
        assertEquals(BackupStep.Reauth(BackupAction.ALLOW_SCREENSHOTS, biometric = false), viewModel.uiState.value.step)
        assertFalse(screenshots.allowed.value)

        // A wrong PIN allows nothing.
        viewModel.reauthFlow.submitPin("111111".toCharArray())
        waitForStep { it is BackupStep.Reauth && it.error != null }
        assertFalse(screenshots.allowed.value)

        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it == BackupStep.Idle && screenshots.allowed.value }
        assertTrue(screenshots.allowed.value)
        // No recovery phrase, no export: the re-authentication was the whole flow.
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun aPinCheckThatEndsAfterTheAppLockedDoesNotAllowScreenshots() {
        `when`(security.isLockable()).thenReturn(true)
        `when`(security.isDatabaseUnlocked()).thenReturn(true)
        // The app locks (no auto-lock delay) while the PIN is being checked.
        `when`(security.verifyPinResult(anyCharArray())).thenAnswer {
            sessionLock.lockNow()
            PinCheck.OK
        }
        viewModel.start(BackupAction.ALLOW_SCREENSHOTS)

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it == BackupStep.Idle }
        assertTrue(sessionLock.locked.value)
        assertFalse(screenshots.allowed.value)
    }

    @Test
    fun aPinCheckThatEndsWhileALockWaitsForTheEditorsDoesNotAllowScreenshots() {
        `when`(security.isLockable()).thenReturn(true)
        `when`(security.isDatabaseUnlocked()).thenReturn(true)
        // An editor is saving: the lock revokes the allowance at once but closes the vault only after the save.
        sessionLock.registerPreLockFlush { awaitCancellation() }
        `when`(security.verifyPinResult(anyCharArray())).thenAnswer {
            sessionLock.lockNow()
            PinCheck.OK
        }
        viewModel.start(BackupAction.ALLOW_SCREENSHOTS)

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        // The lock is still waiting for the save here (up to a second), so `locked` alone would not refuse it.
        waitForStep { it == BackupStep.Idle }
        assertFalse(screenshots.allowed.value)
    }

    @Test
    fun erasingTheVaultEndsTheAllowance() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        `when`(security.wipeAndReset()).thenReturn(true)
        screenshots.allow()
        viewModel.start(BackupAction.ERASE)

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it == BackupStep.Idle && viewModel.uiState.value.erased }
        assertFalse(screenshots.allowed.value)
    }

    @Test
    fun cancellingTheReauthenticationLeavesScreenshotsBlocked() {
        viewModel.start(BackupAction.ALLOW_SCREENSHOTS)

        viewModel.cancel()

        assertEquals(BackupStep.Idle, viewModel.uiState.value.step)
        assertFalse(screenshots.allowed.value)
    }

    @Test
    fun aWeakeningChangeAsksForThePinAndAppliesOnlyAfterTheRightOne() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.WRONG)
        var applied = 0
        viewModel.changeAutoLock(AutoLockDelay.SECONDS_15, AutoLockDelay.MINUTE_1) { applied++ }
        assertEquals(BackupStep.Reauth(BackupAction.CONFIRM_SETTING, biometric = false), viewModel.uiState.value.step)
        assertEquals(0, applied)

        // A wrong PIN keeps the old value.
        viewModel.reauthFlow.submitPin("111111".toCharArray())
        waitForStep { it is BackupStep.Reauth && it.error != null }
        assertEquals(0, applied)

        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it == BackupStep.Idle }
        assertEquals(1, applied)
    }

    @Test
    fun cancellingAWeakeningChangeKeepsTheOldValue() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        var applied = 0
        viewModel.changeStrictKeyboard(strict = false) { applied++ }

        viewModel.cancel()
        // A later PIN entry for another flow must not release the dropped change.
        viewModel.start(BackupAction.ALLOW_SCREENSHOTS)
        viewModel.reauthFlow.submitPin("123456".toCharArray())
        waitForStep { it == BackupStep.Idle }

        assertEquals(0, applied)
    }

    @Test
    fun aLockBetweenTheRequestAndThePinDiscardsTheChange() {
        `when`(security.isLockable()).thenReturn(true)
        `when`(security.isDatabaseUnlocked()).thenReturn(true)
        `when`(security.verifyPinResult(anyCharArray())).thenAnswer {
            sessionLock.lockNow()
            PinCheck.OK
        }
        var applied = 0
        viewModel.changeNotePreviews(show = true) { applied++ }

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it == BackupStep.Idle }
        assertTrue(sessionLock.locked.value)
        assertEquals(0, applied)
    }

    @Test
    fun aLockWhileTheDialogIsOpenDropsTheChange() {
        `when`(security.isLockable()).thenReturn(true)
        `when`(security.isDatabaseUnlocked()).thenReturn(true)
        var applied = 0
        viewModel.changeNotePreviews(show = true) { applied++ }

        sessionLock.lockNow()
        assertEquals(BackupStep.Idle, viewModel.uiState.value.step)

        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        viewModel.start(BackupAction.ALLOW_SCREENSHOTS)
        viewModel.reauthFlow.submitPin("123456".toCharArray())
        waitForStep { it == BackupStep.Idle }
        assertEquals(0, applied)
    }

    @Test
    fun strengtheningChangesApplyAtOnceWithoutAPrompt() {
        viewModel.cancel()
        var applied = 0

        viewModel.changeAutoLock(AutoLockDelay.MINUTE_1, AutoLockDelay.SECONDS_15) { applied++ }
        viewModel.changeAutoLock(AutoLockDelay.MINUTE_1, AutoLockDelay.MINUTE_1) { applied++ }
        viewModel.changeStrictKeyboard(strict = true) { applied++ }
        viewModel.changeNotePreviews(show = false) { applied++ }
        viewModel.changeBiometric(enable = false) { applied++ }

        assertEquals(5, applied)
        assertEquals(BackupStep.Idle, viewModel.uiState.value.step)
    }

    @Test
    fun enablingBiometricAsksForThePinBeforeTheEnrolment() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        var enrolled = 0
        viewModel.changeBiometric(enable = true) { enrolled++ }
        assertEquals(0, enrolled)
        assertEquals(BackupStep.Reauth(BackupAction.CONFIRM_SETTING, biometric = false), viewModel.uiState.value.step)

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it == BackupStep.Idle }
        assertEquals(1, enrolled)
    }

    @Test
    fun aRefusedEraseIsReportedAsFailed() {
        `when`(security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        // The security layer refused (a decoy session): nothing was erased.
        `when`(security.wipeAndReset()).thenReturn(false)
        viewModel.start(BackupAction.ERASE)

        viewModel.reauthFlow.submitPin("123456".toCharArray())

        waitForStep { it == BackupStep.Idle && viewModel.uiState.value.message != null }
        assertEquals(BackupMessage.Text(Res.string.security_erase_failed), viewModel.uiState.value.message)
        assertEquals(false, viewModel.uiState.value.erased)
    }

    @Test
    fun aDeviceWithoutAPickerEndsTheFlowWithAMessage() {
        viewModel.onSystemPickerUnavailable()

        assertEquals(BackupStep.Idle, viewModel.uiState.value.step)
        assertEquals(
            BackupMessage.Text(Res.string.backup_error_no_picker),
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
