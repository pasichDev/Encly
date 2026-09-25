package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.LifecycleOwner
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.BackupMapper
import com.pasich.encly.data.backup.PendingRestore
import com.pasich.encly.data.model.Note
import com.pasich.encly.testutil.InMemorySharedPreferences
import com.pasich.encly.testutil.InMemoryVaultDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** First-run setup with a backup staged by onboarding's "Restore from backup". */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthSetupViewModelTest {

    private lateinit var security: SecurityManager
    private lateinit var store: InMemoryVaultDataStore
    private lateinit var pending: PendingRestore
    private lateinit var viewModel: AuthSetupViewModel
    private var notesWhenCommitted = -1

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(StandardTestDispatcher())
        security = mock(SecurityManager::class.java)
        `when`(security.openInitialVault()).thenReturn(true)
        `when`(security.commitInitialSetup()).thenAnswer {
            notesWhenCommitted = store.notes.size
            true
        }
        store = InMemoryVaultDataStore()
        pending = PendingRestore(BackupManager(security, store, InMemorySharedPreferences()))
        val source = InMemoryVaultDataStore().apply { insertNote(Note(title = "Plan", value = "[]", uid = "n-plan")) }
        pending.stage(BackupMapper.toPayload(source.snapshot(), exportedAt = 1))

        val sessionLock = SessionLockManager(security)
        sessionLock.onStart(mock(LifecycleOwner::class.java))
        viewModel = AuthSetupViewModel(security, sessionLock, pending)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun finish(skip: Boolean = false): AuthSetupViewModel.FinishResult {
        val result = CompletableDeferred<AuthSetupViewModel.FinishResult>()
        if (skip) viewModel.skipRestore { result.complete(it) } else viewModel.finishSetup { result.complete(it) }
        return result.await()
    }

    @Test
    fun theBackupIsImportedBeforeOnboardingIsCommitted() = runTest {
        val result = finish()

        assertTrue(result.ok)
        assertFalse(result.restoreFailed)
        assertEquals("committed only once the notes are in", 1, notesWhenCommitted)
        val order = inOrder(security)
        order.verify(security).openInitialVault()
        order.verify(security).commitInitialSetup()
    }

    @Test
    fun aFailedRestoreCommitsNothingClosesTheVaultAndCanBeRetried() = runTest {
        store.failOnInsert = 1 // e.g. the import was interrupted

        val failed = finish()

        assertTrue(failed.restoreFailed)
        assertFalse(failed.ok)
        assertFalse(failed.backgrounded)
        verify(security, never()).commitInitialSetup()
        verify(security).lock()
        assertTrue(store.notes.isEmpty())

        // The backup is still staged: a retry restores it.
        store.failOnInsert = null
        val retried = finish()

        assertTrue(retried.ok)
        assertEquals(listOf("n-plan"), store.notes.map { it.uid })
        assertEquals(1, notesWhenCommitted)
    }

    @Test
    fun skippingAFailedRestoreCommitsAnEmptyVault() = runTest {
        store.failOnInsert = 1
        assertTrue(finish().restoreFailed)

        val skipped = finish(skip = true)

        assertTrue(skipped.ok)
        assertTrue(store.notes.isEmpty())
        verify(security).commitInitialSetup()
    }

    @Test
    fun aVaultThatDoesNotOpenImportsAndCommitsNothing() = runTest {
        `when`(security.openInitialVault()).thenReturn(false)

        val result = finish()

        assertFalse(result.ok)
        assertFalse(result.restoreFailed)
        verify(security, never()).commitInitialSetup()
        assertTrue(store.notes.isEmpty())
    }

    @Test
    fun theFailureFlagsLiveInTheViewModelAndClearOnRetry() = runTest {
        store.failOnInsert = 1
        assertTrue(finish().restoreFailed)
        assertTrue(viewModel.restoreFailed.value)
        assertFalse(viewModel.finishFailed.value)

        store.failOnInsert = null
        val retried = CompletableDeferred<AuthSetupViewModel.FinishResult>()
        viewModel.retryRestore { retried.complete(it) }

        assertTrue(retried.await().ok)
        assertFalse(viewModel.restoreFailed.value)
        assertEquals(listOf("n-plan"), store.notes.map { it.uid })
    }

    @Test
    fun retryingARestoreThatIsNoLongerStagedCommitsNothing() = runTest {
        // e.g. the process died: the in-memory staged backup is gone.
        pending.clear()

        val result = CompletableDeferred<AuthSetupViewModel.FinishResult>()
        viewModel.retryRestore { result.complete(it) }

        assertFalse(result.await().ok)
        assertTrue(viewModel.finishFailed.value)
        assertFalse(viewModel.restoreFailed.value)
        verify(security, never()).openInitialVault()
        verify(security, never()).commitInitialSetup()
    }
}
