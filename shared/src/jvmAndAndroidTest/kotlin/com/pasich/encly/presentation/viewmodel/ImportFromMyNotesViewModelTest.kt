package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.handoff.HandoffFixtures.handoff
import com.pasich.encly.data.handoff.HandoffFixtures.handoffZip
import com.pasich.encly.data.handoff.HandoffFixtures.note
import com.pasich.encly.platform.CacheDirectory
import com.pasich.encly.platform.DocumentRef
import com.pasich.encly.testutil.FakeDocuments
import com.pasich.encly.testutil.InMemoryKeyValueStore
import com.pasich.encly.testutil.InMemoryVaultDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.io.files.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

/** The hand-off's steps across a killed process: a finished import is not offered again. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportFromMyNotesViewModelTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val documents = FakeDocuments()
    private val cacheDir: File by lazy { temp.newFolder("cache") }
    private val cache = object : CacheDirectory {
        override val path get() = Path(cacheDir.path)
    }
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

    /** The zip as a picked document the way a provider would hand it out. */
    private fun doc(zip: File) = DocumentRef(zip.path).also { documents.files[it] = zip.readBytes() }

    private fun viewModel() = ImportFromMyNotesViewModel(
        documents = documents,
        cache = cache,
        securityManager = security,
        sessionLockManager = sessionLock,
        backupManager = BackupManager(security, store, InMemoryKeyValueStore()),
        ioDispatcher = dispatcher,
        savedStateHandle = savedState,
    )

    @Test
    fun aFinishedHandoffShowsItsCountsAgainAfterTheProcessWasKilled() {
        val zip = handoffZip(temp.newFolder("source"), handoff(notes = listOf(note("n1"), note("n2"))))
        val uri = doc(zip)
        val first = viewModel()
        first.start(uri)
        assertTrue(first.step.value is HandoffStep.Preview)
        first.confirm()
        val done = first.step.value as HandoffStep.Done
        assertEquals(2, done.summary.notesAdded)

        // A new process: the activity comes back with the same intent and the saved state.
        zip.delete()
        documents.files.clear()
        val restored = viewModel()
        restored.start(uri)

        assertEquals(done, restored.step.value)
        // Not read or imported again: no second preview, no "0 notes added".
        assertEquals(2, runBlocking { store.snapshot() }.notes.size)
    }

    @Test
    fun aHandoffNotFinishedBeforeTheKillStartsOver() {
        val zip = handoffZip(temp.newFolder("source"), handoff(notes = listOf(note("n1"))))
        viewModel().start(doc(zip))

        val restored = viewModel()
        restored.start(doc(zip))

        assertTrue(restored.step.value is HandoffStep.Preview)
        verify(security, never()).resolveInitialStatus()
    }

    @Test
    fun theStagingDirectoryIsEmptyAfterTheRead() {
        val zip = handoffZip(temp.newFolder("source"), handoff(notes = listOf(note("n1"))))

        viewModel().start(doc(zip))

        assertTrue(File(cacheDir, "mynotes-handoff").list().isNullOrEmpty())
    }
}
