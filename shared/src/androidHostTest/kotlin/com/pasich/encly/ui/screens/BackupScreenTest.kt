package com.pasich.encly.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.backup_count_notes
import com.pasich.encly.core.resources.backup_decrypt
import com.pasich.encly.core.resources.backup_enter_phrase_title
import com.pasich.encly.core.resources.backup_error_io
import com.pasich.encly.core.resources.backup_error_wrong_phrase
import com.pasich.encly.core.resources.backup_export
import com.pasich.encly.core.resources.backup_import
import com.pasich.encly.core.resources.backup_import_desc
import com.pasich.encly.core.resources.backup_import_done
import com.pasich.encly.core.resources.backup_import_mode_title
import com.pasich.encly.core.resources.backup_intro_short
import com.pasich.encly.core.resources.backup_intro_title
import com.pasich.encly.core.resources.backup_merge
import com.pasich.encly.core.resources.backup_never_exported
import com.pasich.encly.core.resources.backup_reauth_subtitle
import com.pasich.encly.core.resources.backup_reauth_title
import com.pasich.encly.core.resources.backup_replace_all
import com.pasich.encly.core.resources.backup_replace_confirm_title
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.markdown_export
import com.pasich.encly.core.resources.markdown_export_desc
import com.pasich.encly.core.resources.markdown_export_warning_body
import com.pasich.encly.core.resources.markdown_export_warning_title
import com.pasich.encly.core.resources.recovery_phrase_checksum
import com.pasich.encly.core.security.Mnemonic
import com.pasich.encly.data.model.Note
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.backup.BackupScreen
import com.pasich.encly.presentation.viewmodel.BackupStep
import com.pasich.encly.presentation.viewmodel.BackupViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.verifyNoInteractions

class BackupScreenTest : ComposeScreenTest() {
    private val words = String(Mnemonic.generate())
    private val app by lazy { TestApp().withWorkingVault(words) }
    private lateinit var backup: BackupViewModel

    private fun show() {
        backup = app.backup()
        setNavScreen(viewModels(backup, app.markdownExport()), route = NavRoutes.BackupRoute.name) { nav ->
            BackupScreen(nav)
        }
    }

    private fun step() = backup.uiState.value.step

    /** Import, the PIN, then the picked file: the 12-word step. */
    private fun openPhraseStep(vararg notes: Note = arrayOf(Note(title = "Plan", value = "[]", uid = "n-plan"))) {
        rule.onNode(hasText(str(Res.string.backup_import)) and hasClickAction()).performClick()
        waitForText(str(Res.string.backup_reauth_title))
        typePin("123456")
        waitFor(describe = { "${backup.uiState.value}" }) { step() == BackupStep.Idle && !backup.uiState.value.busy }
        val file = runBlocking { app.backupFile(words, *notes) }
        rule.runOnIdle { backup.importFlow.onFile(app.document("backup.enclybak", file)) }
        waitFor(describe = { "${backup.uiState.value}" }) { step() is BackupStep.EnterImportPhrase }
    }

    @Test
    fun showsExportAndImportWithTheirExplanations() {
        show()

        rule.onNodeWithText(str(Res.string.backup_intro_short)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.backup_export)).assertIsEnabled()
        rule.onNodeWithText(str(Res.string.backup_never_exported)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.backup_import_desc)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.backup_intro_title)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 6)
    }

    @Test
    fun exportAsksForThePinFirst() {
        show()

        rule.onNodeWithText(str(Res.string.backup_export)).performClick()

        waitForText(str(Res.string.backup_reauth_title))
        rule.onNodeWithText(str(Res.string.backup_reauth_subtitle)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.cancel)).performClick()
        waitFor { step() == BackupStep.Idle }
    }

    @Test
    fun markdownExportWarnsThatTheFileIsNotEncryptedBeforeAnythingElse() {
        show()
        rule.onNodeWithText(str(Res.string.markdown_export_desc)).assertIsDisplayed()

        rule.onNode(hasText(str(Res.string.markdown_export)) and hasClickAction()).performClick()

        waitForText(str(Res.string.markdown_export_warning_title))
        rule.onNodeWithText(str(Res.string.markdown_export_warning_body)).assertIsDisplayed()
        // No PIN prompt and nothing written yet.
        assertEquals(0, countText(str(Res.string.backup_reauth_title)))
        verifyNoInteractions(app.markdownDocuments)
        rule.onNodeWithText(str(Res.string.cancel)).performClick()
        waitFor { countText(str(Res.string.markdown_export_warning_title)) == 0 }
    }

    @Test
    fun markdownExportAsksForThePinAfterTheWarning() {
        show()

        rule.onNode(hasText(str(Res.string.markdown_export)) and hasClickAction()).performClick()
        waitForText(str(Res.string.markdown_export_warning_title))
        rule.onNodeWithText(str(Res.string.action_continue)).performClick()

        waitForText(str(Res.string.backup_reauth_title))
        rule.onNodeWithText(str(Res.string.backup_reauth_subtitle)).assertIsDisplayed()
        verifyNoInteractions(app.markdownDocuments)
        // The encrypted backup flow was not started by it.
        assertEquals(BackupStep.Idle, step())
    }

    @Test
    fun theImportPhraseStepHasTwelveCellsAndWaitsForAValidPhrase() {
        show()
        openPhraseStep()

        rule.onNodeWithText(str(Res.string.backup_enter_phrase_title)).assertIsDisplayed()
        (1..12).forEach { phraseCell(it).assertExists() }
        rule.onNodeWithText(str(Res.string.backup_decrypt)).assertIsNotEnabled()

        phraseCell(1).performTextReplacement(words.split(' ').take(6).joinToString(" "))
        assertEquals(words.split(' ').take(6), phraseCells().take(6))
        rule.onNodeWithText(str(Res.string.backup_decrypt)).assertIsNotEnabled()

        phraseCell(7).performTextReplacement(words.split(' ').drop(6).joinToString(" "))
        assertEquals(words.split(' '), phraseCells())
        rule.onNodeWithText(str(Res.string.backup_decrypt)).assertIsEnabled()
    }

    @Test
    fun twelveKnownWordsWithABadChecksumAreFlagged() {
        show()
        openPhraseStep()

        // Twelve real words, but "abandon" twelve times has no valid checksum.
        phraseCell(1).performTextReplacement(List(12) { "abandon" }.joinToString(" "))

        rule.onNodeWithText(str(Res.string.recovery_phrase_checksum)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.backup_decrypt)).assertIsNotEnabled()
    }

    @Test
    fun aDifferentPhraseDoesNotOpenTheBackupAndKeepsTheWords() {
        show()
        openPhraseStep()
        val other = PHRASE

        phraseCell(1).performTextReplacement(other)
        rule.onNodeWithText(str(Res.string.backup_decrypt)).performClick()

        waitForText(str(Res.string.backup_error_wrong_phrase), timeoutMillis = LONG_WAIT_MS)
        assertEquals(other.split(' '), phraseCells())

        phraseCell(12).performTextInput("x")
        waitFor { countText(str(Res.string.backup_error_wrong_phrase)) == 0 }
    }

    @Test
    fun theRightPhraseOffersMergeOrReplace() {
        show()
        openPhraseStep(Note(title = "One", value = "[]", uid = "a"), Note(title = "Two", value = "[]", uid = "b"))
        phraseCell(1).performTextReplacement(words)

        rule.onNodeWithText(str(Res.string.backup_decrypt)).performClick()

        waitForText(str(Res.string.backup_import_mode_title), timeoutMillis = LONG_WAIT_MS)
        rule.onNodeWithText(plural(Res.plurals.backup_count_notes, 2, 2), substring = true).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.backup_merge)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.backup_replace_all)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.cancel)).assertIsDisplayed()
    }

    @Test
    fun cancellingReplaceReturnsToTheChoice() {
        show()
        openPhraseStep()
        phraseCell(1).performTextReplacement(words)
        rule.onNodeWithText(str(Res.string.backup_decrypt)).performClick()
        waitForText(str(Res.string.backup_import_mode_title), timeoutMillis = LONG_WAIT_MS)

        rule.onNodeWithText(str(Res.string.backup_replace_all)).performClick()
        waitForText(str(Res.string.backup_replace_confirm_title))
        rule.onNode(hasText(str(Res.string.cancel)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

        waitFor { step() is BackupStep.ChooseImportMode }
        rule.onNodeWithText(str(Res.string.backup_merge)).assertIsDisplayed()
    }

    @Test
    fun mergeImportsAndReportsIt() {
        show()
        openPhraseStep()
        phraseCell(1).performTextReplacement(words)
        rule.onNodeWithText(str(Res.string.backup_decrypt)).performClick()
        waitForText(str(Res.string.backup_import_mode_title), timeoutMillis = LONG_WAIT_MS)

        rule.onNodeWithText(str(Res.string.backup_merge)).performClick()

        waitFor(LONG_WAIT_MS, describe = { "${backup.uiState.value} ${runBlocking { app.store.snapshot() }}" }) {
            runBlocking { app.store.snapshot() }.notes.any { it.title == "Plan" }
        }
        waitForText(str(Res.string.backup_import_done, 1, 0, 0, 0), timeoutMillis = LONG_WAIT_MS)
    }

    private companion object {
        const val LONG_WAIT_MS = 15_000L
    }

    @Test
    fun exportInTheDecoyVaultFailsLikeAStorageError() {
        app.inDecoySession()
        show()

        rule.onNodeWithText(str(Res.string.backup_export)).performClick()
        waitForText(str(Res.string.backup_reauth_title))
        typePin("123456")

        waitForText(str(Res.string.backup_error_io))
        assertEquals(BackupStep.Idle, step())
        assertEquals(NavRoutes.BackupRoute.name, currentRoute())
    }

    @Test
    fun importInTheDecoyVaultFailsAfterThePhraseLikeAStorageError() {
        app.inDecoySession()
        show()
        openPhraseStep()

        rule.runOnIdle { backup.importFlow.submitPhrase(words.toCharArray()) }

        waitForText(str(Res.string.backup_error_io))
        assertEquals(BackupStep.Idle, step())
        assertEquals(NavRoutes.BackupRoute.name, currentRoute())
    }
}
