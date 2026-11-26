package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.lock_wrong_pin
import com.pasich.encly.core.security.ScreenshotAllowance
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.BackupDocuments
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.BackupPhraseSetup
import com.pasich.encly.data.backup.NoBackupPreferences
import com.pasich.encly.testutil.FakeDocuments
import com.pasich.encly.testutil.FakePassphrasePolicy
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.testutil.InMemoryVaultDataStore
import com.pasich.encly.testutil.anyCharArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** The re-authentication before an export where the secret is a passphrase: the same flow, sent from a field. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReauthPassphraseTest {
    private lateinit var security: SecurityManager

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        security = mock(SecurityManager::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(withPolicy: Boolean): BackupViewModel {
        val screenshots = ScreenshotAllowance()
        val backupManager =
            BackupManager(security, InMemoryVaultDataStore(), InMemoryKeyValueStore(), NoBackupPreferences)
        val sessionLock = SessionLockManager(security, screenshots = screenshots)
        val documents = BackupDocuments(FakeDocuments())
        val phraseSetup = BackupPhraseSetup(security)
        return if (withPolicy) {
            BackupViewModel(
                backupManager,
                phraseSetup,
                security,
                sessionLock,
                documents,
                screenshots,
                FakePassphrasePolicy(),
            )
        } else {
            BackupViewModel(backupManager, phraseSetup, security, sessionLock, documents, screenshots)
        }
    }

    @Test
    fun theFlowKnowsTheKindOfTheSecret() {
        assertEquals(SecretKind.PASSWORD, viewModel(withPolicy = true).reauthFlow.secretKind)
        assertEquals(SecretKind.PIN, viewModel(withPolicy = false).reauthFlow.secretKind)
    }

    @Test
    fun aWrongPassphraseIsReportedAndWiped() {
        `when`(security.verifyPin(anyCharArray())).thenReturn(false)
        val vm = viewModel(withPolicy = true)
        vm.start(BackupAction.IMPORT)
        val typed = FakePassphrasePolicy.GOOD.toCharArray()

        vm.reauthFlow.submitPin(typed)

        val deadline = System.currentTimeMillis() + WAIT_MS
        while ((vm.uiState.value.step as? BackupStep.Reauth)?.error == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MS)
        }
        assertEquals(Res.string.lock_wrong_pin, (vm.uiState.value.step as BackupStep.Reauth).error)
        assertArrayEquals(CharArray(typed.size), typed)
    }

    @Test
    fun aSecondPassphraseWhileOneIsBeingCheckedIsWipedAndNeverChecked() {
        val release = java.util.concurrent.CountDownLatch(1)
        `when`(security.verifyPin(anyCharArray())).thenAnswer {
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            false
        }
        val vm = viewModel(withPolicy = true)
        vm.start(BackupAction.IMPORT)
        vm.reauthFlow.submitPin(FakePassphrasePolicy.GOOD.toCharArray())
        val second = "another long passphrase".toCharArray()

        vm.reauthFlow.submitPin(second)
        release.countDown()
        waitUntilError(vm)

        assertArrayEquals(CharArray(second.size), second)
        verify(security, times(1)).verifyPin(anyCharArray())
    }

    @Test
    fun aSecondPinWhileOneIsBeingCheckedIsWipedAndNeverChecked() {
        val release = java.util.concurrent.CountDownLatch(1)
        `when`(security.verifyPin(anyCharArray())).thenAnswer {
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            false
        }
        val vm = viewModel(withPolicy = false)
        vm.start(BackupAction.IMPORT)
        vm.reauthFlow.submitPin("123456".toCharArray())
        val second = "654321".toCharArray()

        vm.reauthFlow.submitPin(second)
        release.countDown()
        waitUntilError(vm)

        assertArrayEquals(CharArray(second.size), second)
        verify(security, times(1)).verifyPin(anyCharArray())
    }

    @Test
    fun anEditTakesTheLastErrorAway() {
        `when`(security.verifyPin(anyCharArray())).thenReturn(false)
        val vm = viewModel(withPolicy = true)
        vm.start(BackupAction.IMPORT)
        vm.reauthFlow.submitPin(FakePassphrasePolicy.GOOD.toCharArray())
        waitUntilError(vm)

        vm.reauthFlow.clearError()

        assertEquals(null, (vm.uiState.value.step as BackupStep.Reauth).error)
        vm.reauthFlow.clearError()
        assertEquals(null, (vm.uiState.value.step as BackupStep.Reauth).error)
    }

    private fun waitUntilError(vm: BackupViewModel) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while ((vm.uiState.value.step as? BackupStep.Reauth)?.error == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
