package com.pasich.encly.presentation.viewmodel

import android.net.Uri
import com.pasich.encly.R
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupException
import com.pasich.encly.core.common.AppStrings
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.VaultDataStore
import com.pasich.encly.data.backup.VaultSnapshot
import com.pasich.encly.data.export.MarkdownDocuments
import com.pasich.encly.data.model.Note
import com.pasich.encly.testutil.InMemoryVaultDataStore
import com.pasich.encly.testutil.anyCharArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class MarkdownExportViewModelTest {

    private lateinit var security: SecurityManager
    private lateinit var documents: MarkdownDocuments
    private lateinit var store: InMemoryVaultDataStore
    private lateinit var sessionLock: SessionLockManager
    private lateinit var viewModel: MarkdownExportViewModel
    private val written = ByteArrayOutputStream()
    private val uri: Uri = mock(Uri::class.java)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        security = mock(SecurityManager::class.java)
        `when`(security.verifyPin(anyCharArray())).thenReturn(true)
        documents = mock(MarkdownDocuments::class.java)
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.getArgument<Any>(1) as (OutputStream) -> Unit)(written)
        }.`when`(documents).write(any(Uri::class.java) ?: uri, anyBlock())
        store = InMemoryVaultDataStore().apply {
            notes += Note(id = 1, title = "Groceries", value = "[]", uid = "n1", date = 5L)
            notes += Note(id = 2, title = "Trashed", value = "[]", uid = "n2", isTrash = true)
        }
        sessionLock = SessionLockManager(security)
        viewModel = viewModelOver(store)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelOver(vaultStore: VaultDataStore) = MarkdownExportViewModel(
        securityManager = security,
        sessionLockManager = sessionLock,
        store = vaultStore,
        documents = documents,
        strings = mock(AppStrings::class.java) { "label" },
    )

    private fun anyBlock(): (OutputStream) -> Unit = any() ?: {}

    private fun step() = viewModel.uiState.value.step

    private fun authenticate() {
        viewModel.confirmWarning()
        viewModel.reauthFlow.submitPin("123456".toCharArray())
        waitUntil { step() is BackupStep.PickMarkdownTarget || step() == BackupStep.Idle }
    }

    /** Lets the work the ViewModel started on other threads finish, so none outlives the test. */
    private fun settle() = waitUntil { true }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition() || viewModel.uiState.value.busy) {
            check(System.currentTimeMillis() < deadline) { "state is ${viewModel.uiState.value}" }
            Thread.sleep(POLL_MS)
        }
    }

    private fun zipNames(): List<String> = ZipInputStream(ByteArrayInputStream(written.toByteArray())).use { zip ->
        generateSequence { zip.nextEntry }.map { it.name }.toList()
    }

    // --- the order: warning, PIN, save dialog, write -------------------------------------------------

    @Test
    fun theWarningComesFirstAndNothingIsReadOrWritten() {
        viewModel.start(MarkdownExportTarget.AllData)

        assertEquals(BackupStep.ConfirmPlainExport, step())
        verify(documents, never()).write(any(Uri::class.java) ?: uri, anyBlock())
        verify(security, never()).verifyPin(anyCharArray())
    }

    @Test
    fun continuingFromTheWarningAsksForThePinWithTheExistingReauthStep() {
        viewModel.start(MarkdownExportTarget.AllData)

        viewModel.confirmWarning()

        assertEquals(BackupStep.Reauth(BackupAction.EXPORT_MARKDOWN, biometric = false), step())
    }

    @Test
    fun theWarningCannotBeSkippedByAskingForThePinDirectly() {
        // Without start() there is no export pending: a PIN submitted now has nowhere to go.
        viewModel.confirmWarning()

        assertEquals(BackupStep.Idle, step())
    }

    @Test
    fun aWrongPinStaysOnTheReauthAndReachesNothingElse() {
        `when`(security.verifyPin(anyCharArray())).thenReturn(false)
        viewModel.start(MarkdownExportTarget.AllData)
        viewModel.confirmWarning()

        viewModel.reauthFlow.submitPin("000000".toCharArray())
        waitUntil { (step() as? BackupStep.Reauth)?.error != null }

        assertEquals(R.string.lock_wrong_pin, (step() as BackupStep.Reauth).error)
        verify(documents, never()).write(any(Uri::class.java) ?: uri, anyBlock())
    }

    @Test
    fun theExportNeverAsksForARecoveryPhraseOrSealsABackup() {
        // The encrypted export would stop at NeedsPhrase for a vault without a phrase.
        viewModel.start(MarkdownExportTarget.AllData)
        authenticate()

        assertTrue(step() is BackupStep.PickMarkdownTarget)
        verify(security, never()).hasBackupKey()
        verify(security, never()).copyBackupRootKey()
    }

    @Test
    fun theSaveDialogSuggestsADateOnlyNameNeverATitle() {
        viewModel.start(MarkdownExportTarget.AllData)
        authenticate()
        val all = step() as BackupStep.PickMarkdownTarget
        assertTrue(all.zip)
        assertTrue(all.name.matches(Regex("""encly-notes-\d{4}-\d{2}-\d{2}\.zip""")))

        viewModel.cancel()
        viewModel.start(MarkdownExportTarget.SingleNote(noteId = 1))
        authenticate()
        val one = step() as BackupStep.PickMarkdownTarget
        assertFalse(one.zip)
        assertTrue(one.name.matches(Regex("""encly-note-\d{4}-\d{2}-\d{2}\.md""")))
    }

    // --- writing ---------------------------------------------------------------------------------------

    @Test
    fun theZipIsWrittenAfterTheSaveDialogReturns() {
        viewModel.start(MarkdownExportTarget.AllData)
        authenticate()
        viewModel.onSystemPickerLaunched()

        viewModel.onTarget(uri)
        waitUntil { viewModel.uiState.value.message != null }

        assertEquals(BackupMessage.Text(R.string.markdown_export_done), viewModel.uiState.value.message)
        assertEquals(listOf("tasks.md", "Groceries.md"), zipNames())
        assertEquals(BackupStep.Idle, step())
    }

    @Test
    fun oneNoteIsWrittenAsOneMarkdownDocument() {
        viewModel.start(MarkdownExportTarget.SingleNote(noteId = 1))
        authenticate()
        viewModel.onSystemPickerLaunched()

        viewModel.onTarget(uri)
        waitUntil { viewModel.uiState.value.message != null }

        assertTrue(written.toString(Charsets.UTF_8).startsWith("---\ntitle: \"Groceries\"\n"))
    }

    @Test
    fun aCancelledSaveDialogWritesNothingAndKeepsNothing() {
        viewModel.start(MarkdownExportTarget.AllData)
        authenticate()
        viewModel.onSystemPickerLaunched()

        viewModel.onTarget(null)
        viewModel.onTarget(uri)
        settle()

        // The pending export was dropped with the first call: a second result has nothing to write.
        // The empty document it names is removed, because nothing will be written to it.
        assertEquals(0, written.size())
        verify(documents).discard(uri)
        assertEquals(BackupStep.Idle, step())
    }

    @Test
    fun cancellingAtAnyStepDropsTheExport() {
        viewModel.start(MarkdownExportTarget.AllData)
        viewModel.cancel()
        assertEquals(BackupStep.Idle, step())

        viewModel.start(MarkdownExportTarget.AllData)
        viewModel.confirmWarning()
        viewModel.cancel()
        assertEquals(BackupStep.Idle, step())

        viewModel.onTarget(uri)
        settle()
        assertEquals(0, written.size())
    }

    @Test
    fun aVaultThatClosedMeanwhileFailsInsteadOfExportingAndTheEmptyDocumentIsRemoved() {
        val closing = object : VaultDataStore by store {
            override fun bindToCurrentSession(): VaultDataStore = this

            override suspend fun snapshot(): VaultSnapshot = error("Vault locked")
        }
        viewModel = viewModelOver(closing)
        viewModel.start(MarkdownExportTarget.AllData)
        authenticate()
        viewModel.onSystemPickerLaunched()

        viewModel.onTarget(uri)
        waitUntil { viewModel.uiState.value.message != null }

        assertEquals(BackupMessage.Text(R.string.backup_error_locked), viewModel.uiState.value.message)
        verify(documents).discard(uri)
        assertEquals(0, written.size())
    }

    @Test
    fun aReLockAfterTheReadStopsTheWriteBeforeTheDocumentIsOpened() {
        val closing = object : VaultDataStore by store {
            override fun bindToCurrentSession(): VaultDataStore = this

            // The read succeeds; the vault closes right after it.
            override fun assertCurrentSession() = error("Vault locked")
        }
        viewModel = viewModelOver(closing)
        viewModel.start(MarkdownExportTarget.AllData)
        authenticate()
        viewModel.onSystemPickerLaunched()

        viewModel.onTarget(uri)
        waitUntil { viewModel.uiState.value.message != null }

        assertEquals(BackupMessage.Text(R.string.backup_error_locked), viewModel.uiState.value.message)
        verify(documents, never()).write(any(Uri::class.java) ?: uri, anyBlock())
        verify(documents).discard(uri)
        assertEquals(0, written.size())
    }

    @Test
    fun aPinThatVerifiesAfterTheFlowWasCancelledCannotSkipTheNextWarning() {
        val release = java.util.concurrent.CountDownLatch(1)
        `when`(security.verifyPin(anyCharArray())).thenAnswer {
            release.await(TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            true
        }
        viewModel.start(MarkdownExportTarget.AllData)
        viewModel.confirmWarning()
        viewModel.reauthFlow.submitPin("123456".toCharArray())

        // The user backs out and starts again while the first PIN is still being checked.
        viewModel.cancel()
        viewModel.start(MarkdownExportTarget.AllData)
        release.countDown()
        waitUntil { true }

        assertEquals(BackupStep.ConfirmPlainExport, step())
        verify(documents, never()).write(any(Uri::class.java) ?: uri, anyBlock())
    }

    @Test
    fun oneNoteIsReadWithoutReadingTheWholeVault() {
        val noteOnly = object : VaultDataStore by store {
            override fun bindToCurrentSession(): VaultDataStore = this

            override suspend fun snapshot(): VaultSnapshot = error("the whole vault must not be read")
        }
        viewModel = viewModelOver(noteOnly)
        viewModel.start(MarkdownExportTarget.SingleNote(noteId = 1))
        authenticate()
        viewModel.onSystemPickerLaunched()

        viewModel.onTarget(uri)
        waitUntil { viewModel.uiState.value.message != null }

        assertEquals(BackupMessage.Text(R.string.markdown_export_done), viewModel.uiState.value.message)
        assertTrue(written.toString(Charsets.UTF_8).contains("Groceries"))
    }

    @Test
    fun theFingerprintIsOfferedOnlyWhereTheSecurityLayerAllowsIt() {
        `when`(security.canReauthWithBiometric()).thenReturn(true)
        viewModel.start(MarkdownExportTarget.AllData)
        viewModel.confirmWarning()
        assertEquals(true, (step() as BackupStep.Reauth).biometric)

        viewModel.cancel()
        `when`(security.canReauthWithBiometric()).thenReturn(false)
        viewModel.start(MarkdownExportTarget.AllData)
        viewModel.confirmWarning()
        assertEquals(false, (step() as BackupStep.Reauth).biometric)
    }

    @Test
    fun aVaultThatIsAlreadyLockedWhenTheExportIsAuthenticatedEndsWithAMessage() {
        val locked = object : VaultDataStore by store {
            override fun bindToCurrentSession(): VaultDataStore = error("Vault locked")
        }
        viewModel = viewModelOver(locked)
        viewModel.start(MarkdownExportTarget.AllData)
        viewModel.confirmWarning()
        viewModel.reauthFlow.submitPin("123456".toCharArray())
        waitUntil { viewModel.uiState.value.message != null }

        assertEquals(BackupMessage.Text(R.string.backup_error_locked), viewModel.uiState.value.message)
        assertEquals(BackupStep.Idle, step())
    }

    @Test
    fun aProviderFailureIsReportedWithoutItsMessage() {
        doAnswer { throw BackupException(BackupError.TARGET_EXISTS) }
            .`when`(documents).write(any(Uri::class.java) ?: uri, anyBlock())
        viewModel.start(MarkdownExportTarget.AllData)
        authenticate()
        viewModel.onSystemPickerLaunched()

        viewModel.onTarget(uri)
        waitUntil { viewModel.uiState.value.message != null }

        assertEquals(BackupMessage.Text(R.string.backup_error_target_exists), viewModel.uiState.value.message)
    }

    @Test
    fun aNoteThatIsGoneMeanwhileFailsTheWholeWriteSoTheDocumentIsDiscardedByTheWriter() {
        viewModel.start(MarkdownExportTarget.SingleNote(noteId = 99))
        authenticate()
        viewModel.onSystemPickerLaunched()
        // The real writer deletes the document when its block throws; the fake only runs the block.
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            runCatching { (invocation.getArgument<Any>(1) as (OutputStream) -> Unit)(written) }
                .getOrElse { throw it }
        }.`when`(documents).write(any(Uri::class.java) ?: uri, anyBlock())

        viewModel.onTarget(uri)
        waitUntil { viewModel.uiState.value.message != null }

        assertEquals(BackupMessage.Text(R.string.backup_error_io), viewModel.uiState.value.message)
    }

    @Test
    fun theDecoyVaultExportsWhatItHolds() {
        `when`(security.isDecoySession()).thenReturn(true)
        store.notes.clear()
        store.notes += Note(id = 1, title = "Decoy note", value = "[]", uid = "d1")
        viewModel.start(MarkdownExportTarget.AllData)
        authenticate()
        viewModel.onSystemPickerLaunched()

        viewModel.onTarget(uri)
        waitUntil { viewModel.uiState.value.message != null }

        assertEquals(listOf("tasks.md", "Decoy note.md"), zipNames())
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
