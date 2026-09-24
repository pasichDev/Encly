package com.pasich.encly.presentation.viewmodel

import android.net.Uri
import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import com.pasich.encly.R
import com.pasich.encly.core.backup.BackupError
import com.pasich.encly.core.backup.BackupKeys
import com.pasich.encly.core.security.RecoveryWords
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.backup.BackupDocuments
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.backup.BackupPhraseSetup
import com.pasich.encly.data.backup.ImportSummary
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import com.pasich.encly.presentation.designsystem.RecoveryPhraseState
import com.pasich.encly.presentation.screen.backup.backupErrorMessage
import com.pasich.encly.testutil.InMemorySharedPreferences
import com.pasich.encly.testutil.InMemoryVaultDataStore
import com.pasich.encly.testutil.anyByteArray
import com.pasich.encly.testutil.anyCharArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Settings → Backup end to end: export seals the vault to the recovery phrase, and import on
 * another vault opens that file with the words typed into the 12 cells.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupFlowsTest {
    private val phrase = String(MnemonicCode(WordCount.COUNT_12).chars)
    private val otherPhrase = String(MnemonicCode(WordCount.COUNT_12).chars)
    private val uri: Uri = mock(Uri::class.java)
    private var written: ByteArray? = null

    private lateinit var security: SecurityManager
    private lateinit var documents: BackupDocuments
    private lateinit var source: InMemoryVaultDataStore
    private lateinit var prefs: InMemorySharedPreferences
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        security = mock(SecurityManager::class.java)
        `when`(security.verifyPin(anyCharArray())).thenAnswer { String(it.getArgument<CharArray>(0)) == PIN }
        `when`(security.hasRecoverySeed()).thenReturn(true)
        `when`(security.hasBackupKey()).thenReturn(true)
        // A fresh copy per call: createBackup wipes the key it was handed.
        `when`(security.copyBackupRootKey()).thenAnswer { BackupKeys.rootFromMnemonic(phrase.toCharArray()) }
        `when`(security.isValidRecoveryPhrase(anyChars())).thenAnswer {
            RecoveryWords.isValidPhrase((it.arguments[0] as CharArray).copyOf())
        }
        documents = mock(BackupDocuments::class.java)
        doAnswer {
            written = (it.arguments[1] as ByteArray).copyOf()
            null
        }.`when`(documents).write(anyUri(), anyByteArray())
        `when`(documents.read(anyUri())).thenAnswer { checkNotNull(written).copyOf() }

        source = InMemoryVaultDataStore()
        runBlocking {
            source.insertTag(Tag(nameTag = "Work", uid = "t-work"))
            source.insertNote(Note(title = "Plan", value = "[]", uid = "n-plan", tagId = 1))
            source.insertNote(Note(title = "Ideas", value = "[]", uid = "n-ideas"))
            source.insertTask(Task(title = "Call", uid = "k-call"))
        }
        prefs = InMemorySharedPreferences()
        viewModel = viewModelFor(source, prefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- export -------------------------------------------------------------------------------

    @Test
    fun exportSealsBeforeThePickerAndWritesWhereTheUserChose() {
        assertNull(viewModel.uiState.value.lastExportAt)

        export()

        assertNotNull(written)
        assertEquals(BackupStep.Idle, state().step)
        assertEquals(BackupMessage.Text(R.string.backup_export_done), state().message)
        assertNotNull(state().lastExportAt)
        assertEquals(
            "remembered across screens",
            state().lastExportAt,
            viewModelFor(source, prefs).uiState.value.lastExportAt,
        )
    }

    @Test
    fun cancellingThePickerWritesNothing() {
        startAndAuthenticate(BackupAction.EXPORT)
        waitFor { it == BackupStep.PickExportTarget }

        viewModel.onSystemPickerLaunched()
        viewModel.exportFlow.onTarget(null)
        viewModel.exportFlow.onTarget(uri) // the sealed file is gone with the cancelled picker

        verify(documents, never()).write(anyUri(), anyByteArray())
        assertNull(state().lastExportAt)
    }

    @Test
    fun aWrongPinNeverSealsTheVault() {
        viewModel.start(BackupAction.EXPORT)

        viewModel.reauthFlow.submitPin("000000")

        waitFor { (it as? BackupStep.Reauth)?.error != null }
        verify(security, never()).copyBackupRootKey()
    }

    @Test
    fun aLockedVaultCannotBeExported() {
        `when`(security.copyBackupRootKey()).thenReturn(null)

        startAndAuthenticate(BackupAction.EXPORT)

        waitFor { it == BackupStep.Idle }
        assertEquals(BackupMessage.Text(backupErrorMessage(BackupError.VAULT_UNAVAILABLE)), state().message)
    }

    @Test
    fun cancelDropsTheSealedFile() {
        startAndAuthenticate(BackupAction.EXPORT)
        waitFor { it == BackupStep.PickExportTarget }

        viewModel.cancel()
        viewModel.exportFlow.onTarget(uri)

        assertEquals(BackupStep.Idle, state().step)
        verify(documents, never()).write(anyUri(), anyByteArray())
    }

    // --- import -------------------------------------------------------------------------------

    @Test
    fun theExportedFileMergesIntoAnotherVaultWithTheTwelveTypedWords() {
        export()
        val target = InMemoryVaultDataStore()
        viewModel = viewModelFor(target)

        pickBackup()
        viewModel.importFlow.submitPhrase(typedIntoCells(phrase))
        waitFor { it is BackupStep.ChooseImportMode }
        assertEquals(BackupStep.ChooseImportMode(notes = 2, tasks = 1, tags = 1), state().step)

        viewModel.importFlow.merge()

        waitFor { it == BackupStep.Idle }
        assertEquals(BackupMessage.Imported(ImportSummary(2, 1, 1, 0)), state().message)
        assertEquals(setOf("n-plan", "n-ideas"), target.notes.map { it.uid }.toSet())
        assertEquals(listOf("Work"), target.tags.map { it.nameTag })
        assertEquals(listOf("Call"), target.tasks.map { it.title })
        val plan = target.notes.single { it.uid == "n-plan" }
        assertEquals("the note keeps its tag", target.tags.single().id, plan.tagId)
    }

    @Test
    fun thePastedPhraseInAnyCaseAndSpacingOpensTheFile() {
        export()
        viewModel = viewModelFor(InMemoryVaultDataStore())
        pickBackup()
        val cells = RecoveryPhraseState()
        cells.onValueChange(5, "\n  " + phrase.uppercase().replace(" ", " \t ") + "  ")

        viewModel.importFlow.submitPhrase(cells.toCharArray())

        waitFor { it is BackupStep.ChooseImportMode }
    }

    @Test
    fun anotherValidPhraseIsAWrongPhraseAndCanBeRetyped() {
        export()
        viewModel = viewModelFor(InMemoryVaultDataStore())
        pickBackup()

        viewModel.importFlow.submitPhrase(typedIntoCells(otherPhrase))

        waitFor { (it as? BackupStep.EnterImportPhrase)?.error != null }
        assertEquals(
            BackupStep.EnterImportPhrase(backupErrorMessage(BackupError.WRONG_SECRET)),
            state().step,
        )

        viewModel.importFlow.clearError()
        assertEquals(BackupStep.EnterImportPhrase(), state().step)

        viewModel.importFlow.submitPhrase(typedIntoCells(phrase))
        waitFor { it is BackupStep.ChooseImportMode }
    }

    @Test
    fun twelveWordsWithABadChecksumAreAnInvalidPhrase() {
        export()
        viewModel = viewModelFor(InMemoryVaultDataStore())
        pickBackup()

        viewModel.importFlow.submitPhrase(List(12) { "abandon" }.joinToString(" ").toCharArray())

        waitFor { (it as? BackupStep.EnterImportPhrase)?.error != null }
        assertEquals(
            backupErrorMessage(BackupError.INVALID_PHRASE),
            (state().step as BackupStep.EnterImportPhrase).error,
        )
    }

    @Test
    fun theTypedWordsAreWipedAfterTheAttempt() {
        export()
        viewModel = viewModelFor(InMemoryVaultDataStore())
        pickBackup()
        val typed = typedIntoCells(phrase)

        viewModel.importFlow.submitPhrase(typed)
        waitFor { it is BackupStep.ChooseImportMode }

        assertArrayEquals(CharArray(typed.size), typed)
    }

    @Test
    fun aFileThatIsNotABackupEndsTheFlowBeforeAskingForWords() {
        written = "just some text, not a backup".toByteArray()
        startAndAuthenticate(BackupAction.IMPORT)
        waitFor { it == BackupStep.PickImportFile }

        viewModel.importFlow.onFile(uri)

        waitFor { it == BackupStep.Idle }
        assertEquals(BackupMessage.Text(backupErrorMessage(BackupError.NOT_A_BACKUP)), state().message)
    }

    @Test
    fun noFileChosenStaysPut() {
        startAndAuthenticate(BackupAction.IMPORT)
        waitFor { it == BackupStep.PickImportFile }

        viewModel.importFlow.onFile(null)

        assertEquals(BackupStep.PickImportFile, state().step)
        verify(documents, never()).read(anyUri())
    }

    @Test
    fun replaceNeedsAConfirmationAndCancelGoesBackToTheChoice() {
        export()
        val target = InMemoryVaultDataStore()
        runBlocking { target.insertNote(Note(title = "Local only", value = "[]", uid = "n-local")) }
        viewModel = viewModelFor(target)
        pickBackup()
        viewModel.importFlow.submitPhrase(typedIntoCells(phrase))
        waitFor { it is BackupStep.ChooseImportMode }
        val choice = state().step

        viewModel.importFlow.askReplace()
        assertEquals(BackupStep.ConfirmReplace, state().step)
        viewModel.importFlow.backToChoice()
        assertEquals(choice, state().step)
        assertEquals(listOf("n-local"), target.notes.map { it.uid })

        viewModel.importFlow.askReplace()
        viewModel.importFlow.confirmReplace()

        waitFor { it == BackupStep.Idle }
        assertEquals(setOf("n-plan", "n-ideas"), target.notes.map { it.uid }.toSet())
    }

    @Test
    fun mergeAndReplaceAreIgnoredOutsideTheirStep() {
        viewModel.importFlow.merge()
        viewModel.importFlow.confirmReplace()
        viewModel.importFlow.askReplace()
        viewModel.importFlow.backToChoice()

        assertEquals(BackupStep.Idle, state().step)
    }

    @Test
    fun wordsSubmittedWithoutAFileAreWipedAndIgnored() {
        val typed = typedIntoCells(phrase)

        viewModel.importFlow.submitPhrase(typed)

        assertArrayEquals(CharArray(typed.size), typed)
        assertEquals(BackupStep.Idle, state().step)
    }

    // --- recovery phrase ----------------------------------------------------------------------

    @Test
    fun exportWithoutAPhraseAsksToCreateOneThenChecksThreeWordsAndExports() {
        `when`(security.hasRecoverySeed()).thenReturn(false)
        `when`(security.generateMnemonicCode()).thenAnswer { phrase.toCharArray() }
        `when`(security.addRecoverySeed(anyChars())).thenReturn(true)
        startAndAuthenticate(BackupAction.EXPORT)
        waitFor { it == BackupStep.NeedsPhrase }

        viewModel.phraseFlow.create()
        assertEquals(phrase.split(' '), (state().step as BackupStep.ShowNewPhrase).words)
        viewModel.phraseFlow.writtenDown()
        val check = state().step as BackupStep.CheckNewPhrase
        assertEquals(3, check.positions.size)
        assertEquals(check.positions.sorted(), check.positions)

        viewModel.phraseFlow.submitCheck(listOf("wrong", "words", "here"))
        assertEquals(R.string.backup_phrase_check_wrong, (state().step as BackupStep.CheckNewPhrase).error)
        viewModel.phraseFlow.clearError()
        assertNull((state().step as BackupStep.CheckNewPhrase).error)

        viewModel.phraseFlow.submitCheck(check.expected.map { " ${it.uppercase()} " })

        waitFor { it == BackupStep.PickExportTarget }
        verify(security).addRecoverySeed(anyChars())
        assertEquals(BackupMessage.Text(R.string.backup_phrase_added), state().message)
    }

    @Test
    fun tooFewAnswersAreWrong() {
        `when`(security.generateMnemonicCode()).thenAnswer { phrase.toCharArray() }
        viewModel.phraseFlow.create()
        viewModel.phraseFlow.writtenDown()
        val check = state().step as BackupStep.CheckNewPhrase

        viewModel.phraseFlow.submitCheck(check.expected.take(2))

        assertEquals(R.string.backup_phrase_check_wrong, (state().step as BackupStep.CheckNewPhrase).error)
        verify(security, never()).addRecoverySeed(anyChars())
    }

    @Test
    fun aPhraseThatCannotBeStoredEndsTheFlowWithAMessage() {
        `when`(security.generateMnemonicCode()).thenAnswer { phrase.toCharArray() }
        `when`(security.addRecoverySeed(anyChars())).thenReturn(false)
        viewModel.phraseFlow.create()
        viewModel.phraseFlow.writtenDown()
        val check = state().step as BackupStep.CheckNewPhrase

        viewModel.phraseFlow.submitCheck(check.expected)

        waitFor { it == BackupStep.Idle }
        assertEquals(BackupMessage.Text(R.string.backup_error_phrase_setup), state().message)
    }

    @Test
    fun anOlderVaultConfirmsItsWordsOnceBeforeItsFirstExport() {
        `when`(security.hasBackupKey()).thenReturn(false)
        `when`(security.createBackupKey(anyChars())).thenAnswer { String(it.arguments[0] as CharArray) == phrase }
        startAndAuthenticate(BackupAction.EXPORT)
        waitFor { it == BackupStep.ConfirmExistingPhrase() }

        viewModel.phraseFlow.submitExisting(typedIntoCells(otherPhrase))
        waitFor { it == BackupStep.ConfirmExistingPhrase(R.string.backup_error_phrase_mismatch) }
        viewModel.phraseFlow.clearError()
        assertEquals(BackupStep.ConfirmExistingPhrase(), state().step)

        viewModel.phraseFlow.submitExisting(typedIntoCells(phrase))

        waitFor { it == BackupStep.PickExportTarget }
    }

    @Test
    fun confirmingWordsOutsideThatStepOnlyWipesThem() {
        val typed = typedIntoCells(phrase)

        viewModel.phraseFlow.submitExisting(typed)

        assertArrayEquals(CharArray(typed.size), typed)
        verify(security, never()).createBackupKey(anyChars())
    }

    @Test
    fun aNewPhraseCreatedFromSecurityReturnsToIdleNotToExport() {
        `when`(security.hasRecoverySeed()).thenReturn(false)
        `when`(security.generateMnemonicCode()).thenAnswer { phrase.toCharArray() }
        `when`(security.addRecoverySeed(anyChars())).thenReturn(true)
        startAndAuthenticate(BackupAction.CREATE_PHRASE)
        waitFor { it is BackupStep.ShowNewPhrase }
        viewModel.phraseFlow.writtenDown()

        viewModel.phraseFlow.submitCheck((state().step as BackupStep.CheckNewPhrase).expected)

        waitFor { it == BackupStep.Idle }
        verify(security, never()).copyBackupRootKey()
    }

    @Test
    fun clearingTheMessageKeepsTheStep() {
        viewModel.onSystemPickerUnavailable()

        viewModel.clearMessage()

        assertNull(state().message)
        assertFalse(state().busy)
    }

    // --- helpers ------------------------------------------------------------------------------

    private fun viewModelFor(
        store: InMemoryVaultDataStore,
        prefs: InMemorySharedPreferences = InMemorySharedPreferences(),
    ) = BackupViewModel(
        backupManager = BackupManager(security, store, prefs),
        phraseSetup = BackupPhraseSetup(security),
        securityManager = security,
        sessionLockManager = SessionLockManager(security),
        documents = documents,
    )

    private fun state() = viewModel.uiState.value

    private fun startAndAuthenticate(action: BackupAction) {
        viewModel.start(action)
        viewModel.reauthFlow.submitPin(PIN)
    }

    private fun export() {
        startAndAuthenticate(BackupAction.EXPORT)
        waitFor { it == BackupStep.PickExportTarget }
        viewModel.onSystemPickerLaunched()
        viewModel.exportFlow.onTarget(uri)
        waitFor { it == BackupStep.Idle }
        assertTrue(state().message == BackupMessage.Text(R.string.backup_export_done))
    }

    private fun pickBackup() {
        startAndAuthenticate(BackupAction.IMPORT)
        waitFor { it == BackupStep.PickImportFile }
        viewModel.onSystemPickerLaunched()
        viewModel.importFlow.onFile(uri)
        waitFor { it == BackupStep.EnterImportPhrase() }
    }

    /** The words as the 12 cells hand them over: typed one per cell, each moved on with a space. */
    private fun typedIntoCells(words: String): CharArray {
        val cells = RecoveryPhraseState()
        words.split(' ').forEachIndexed { index, word -> cells.onValueChange(index, "$word ") }
        return cells.toCharArray()
    }

    /** The flows run on real Default/IO threads; waits for the step and for the busy block to end. */
    private fun waitFor(matches: (BackupStep) -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!matches(state().step) || state().busy) {
            check(System.currentTimeMillis() < deadline) { "step is ${state().step}" }
            Thread.sleep(POLL_MS)
        }
    }

    private fun anyUri(): Uri = ArgumentMatchers.any(Uri::class.java) ?: uri

    private fun anyChars(): CharArray = ArgumentMatchers.any(CharArray::class.java) ?: CharArray(0)

    private companion object {
        const val PIN = "482915"
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 10L
    }
}
