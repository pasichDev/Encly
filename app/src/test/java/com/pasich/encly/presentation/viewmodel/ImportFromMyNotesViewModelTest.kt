package com.pasich.encly.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.handoff.HandoffFixtures.handoff
import com.pasich.encly.data.handoff.HandoffFixtures.handoffZip
import com.pasich.encly.data.handoff.HandoffFixtures.note
import com.pasich.encly.testutil.InMemorySharedPreferences
import com.pasich.encly.testutil.InMemoryVaultDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The hand-off's steps across a killed process: a finished import is not offered again. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ImportFromMyNotesViewModelTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var security: SecurityManager
    private lateinit var sessionLock: SessionLockManager
    private lateinit var store: InMemoryVaultDataStore
    private lateinit var savedState: SavedStateHandle

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        security = mock(SecurityManager::class.java)
        // The vault is open (within the auto-lock grace).
        `when`(security.isDatabaseUnlocked()).thenReturn(true)
        sessionLock = SessionLockManager(security)
        sessionLock.onStart(mock(LifecycleOwner::class.java))
        store = InMemoryVaultDataStore()
        savedState = SavedStateHandle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ImportFromMyNotesViewModel(
        context = context,
        securityManager = security,
        sessionLockManager = sessionLock,
        backupManager = BackupManager(security, store, InMemorySharedPreferences()),
        ioDispatcher = dispatcher,
        savedStateHandle = savedState,
    )

    @Test
    fun aFinishedHandoffShowsItsCountsAgainAfterTheProcessWasKilled() {
        val zip = handoffZip(temp.newFolder("source"), handoff(notes = listOf(note("n1"), note("n2"))))
        val uri = Uri.fromFile(zip)
        val first = viewModel()
        first.start(uri)
        assertTrue(first.step.value is HandoffStep.Preview)
        first.confirm()
        val done = first.step.value as HandoffStep.Done
        assertEquals(2, done.summary.notesAdded)

        // A new process: the activity comes back with the same intent and the saved state.
        zip.delete()
        val restored = viewModel()
        restored.start(uri)

        assertEquals(done, restored.step.value)
        // Not read or imported again: no second preview, no "0 notes added".
        assertEquals(2, runBlocking { store.snapshot() }.notes.size)
    }

    @Test
    fun aHandoffNotFinishedBeforeTheKillStartsOver() {
        val zip = handoffZip(temp.newFolder("source"), handoff(notes = listOf(note("n1"))))
        viewModel().start(Uri.fromFile(zip))

        val restored = viewModel()
        restored.start(Uri.fromFile(zip))

        assertTrue(restored.step.value is HandoffStep.Preview)
        verify(security, never()).resolveInitialStatus()
    }

    @Test
    fun theStagingDirectoryIsEmptyAfterTheRead() {
        val zip = handoffZip(temp.newFolder("source"), handoff(notes = listOf(note("n1"))))

        viewModel().start(Uri.fromFile(zip))

        assertTrue(File(context.cacheDir, "mynotes-handoff").list().isNullOrEmpty())
    }
}
