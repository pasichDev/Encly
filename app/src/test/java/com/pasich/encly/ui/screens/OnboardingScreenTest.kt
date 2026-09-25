package com.pasich.encly.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.presentation.screen.onboarding.OnboardingScreen
import com.pasich.encly.presentation.viewmodel.OnboardingStep
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingScreenTest : ComposeScreenTest() {
    private val words = String(MnemonicCode(WordCount.COUNT_12).chars)
    private val app by lazy { TestApp(context).withWorkingVault(words) }
    private lateinit var onboarding: OnboardingViewModel
    private var completed = false

    private fun show() {
        onboarding = app.onboarding()
        setScreen(viewModels(onboarding, app.authSetup())) { OnboardingScreen(onComplete = { completed = true }) }
    }

    private fun primary(id: Int) = rule.onNodeWithText(str(id))

    private fun choosePin(pin: String = "246810") {
        waitForText(str(R.string.onboarding_pin_title))
        primary(R.string.action_continue).assertIsNotEnabled()
        typePin(pin)
        primary(R.string.action_continue).assertIsEnabled().performClick()
        waitForText(str(R.string.onboarding_pin_again_title))
        typePin(pin)
        primary(R.string.action_continue).performClick()
    }

    @Test
    fun welcomeShowsTheFactsAndBothWaysIn() {
        show()

        rule.onNodeWithText(str(R.string.onboarding_welcome_headline)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.onboarding_fact_offline_title)).assertIsDisplayed()
        primary(R.string.onboarding_get_started).assertIsEnabled()
        primary(R.string.onboarding_have_backup).assertIsEnabled()
        assertBodyNotEmpty(minTexts = 8)
    }

    @Test
    fun theLanguagePillOpensTheLanguageChoice() {
        show()

        rule.onNodeWithContentDescription(str(R.string.language_dialog_title), substring = true).performClick()

        waitForText(str(R.string.language_name_uk))
        rule.onNodeWithText(str(R.string.done)).assertIsDisplayed()
    }

    @Test
    fun theWholeCreatePathEndsReady() {
        show()
        primary(R.string.onboarding_get_started).performClick()
        choosePin()

        waitForText(str(R.string.onboarding_recovery_title))
        primary(R.string.onboarding_recovery_create).performClick()

        waitForText(str(R.string.onboarding_phrase_title))
        words.split(' ').forEach { word -> assertTrue(word, countText(word) > 0) }
        primary(R.string.onboarding_phrase_done).performClick()

        waitForText(str(R.string.onboarding_verify_title))
        primary(R.string.onboarding_verify_confirm).assertIsNotEnabled()
        val fields = rule.onAllNodes(hasSetTextAction())
        onboarding.uiState.value.verificationWords.forEachIndexed { position, (_, word) ->
            fields[position].performTextInput(word)
        }
        primary(R.string.onboarding_verify_confirm).assertIsEnabled().performClick()

        waitForText(str(R.string.onboarding_ready_title), timeoutMillis = LONG_WAIT_MS)
        rule.onNodeWithText(str(R.string.onboarding_ready_phrase_title)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 5)
    }

    @Test
    fun aWrongVerificationWordIsFlagged() {
        show()
        primary(R.string.onboarding_get_started).performClick()
        choosePin()
        waitForText(str(R.string.onboarding_recovery_title))
        primary(R.string.onboarding_recovery_create).performClick()
        waitForText(str(R.string.onboarding_phrase_title))
        primary(R.string.onboarding_phrase_done).performClick()
        waitForText(str(R.string.onboarding_verify_title))

        val (index, word) = onboarding.uiState.value.verificationWords.first()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("z".repeat(word.length))

        rule.onNodeWithText(str(R.string.onboarding_verify_wrong, index + 1)).assertIsDisplayed()
        primary(R.string.onboarding_verify_confirm).assertIsNotEnabled()
    }

    @Test
    fun skippingThePhraseEndsReadyWithoutOne() {
        show()
        primary(R.string.onboarding_get_started).performClick()
        choosePin()
        waitForText(str(R.string.onboarding_recovery_skip))

        primary(R.string.onboarding_recovery_skip).performClick()

        waitForText(str(R.string.onboarding_ready_no_phrase_title), timeoutMillis = LONG_WAIT_MS)
    }

    @Test
    fun mismatchedPinsStartThePinAgain() {
        show()
        primary(R.string.onboarding_get_started).performClick()
        waitForText(str(R.string.onboarding_pin_title))
        typePin("111111")
        primary(R.string.action_continue).performClick()
        waitForText(str(R.string.onboarding_pin_again_title))

        typePin("222222")
        primary(R.string.action_continue).performClick()

        waitForText(str(R.string.onboarding_pin_title))
    }

    @Test
    fun restoreHasTwelveCellsAndWaitsForAFileAndAValidPhrase() {
        show()
        primary(R.string.onboarding_have_backup).performClick()

        waitForText(str(R.string.onboarding_restore_title))
        (1..12).forEach { phraseCell(it).assertExists() }
        primary(R.string.restore).assertIsNotEnabled()

        phraseCell(1).performTextReplacement(words)
        assertEquals(words.split(' '), phraseCells())
        primary(R.string.restore).assertIsNotEnabled()

        pickBackup()
        rule.onNodeWithText("notes.enclybak").assertIsDisplayed()
        primary(R.string.restore).assertIsEnabled()
    }

    @Test
    fun restoreTypedWordByWordMovesAcrossTheCells() {
        show()
        primary(R.string.onboarding_have_backup).performClick()
        waitForText(str(R.string.onboarding_restore_title))

        phraseCell(1).performTextInput("${words.split(' ')[0]} ")
        rule.waitForIdle()

        assertEquals(words.split(' ')[0], phraseCells()[0])
        assertTrue(phraseCells().drop(1).all(String::isEmpty))
    }

    @Test
    fun aRestoredBackupGoesOnToThePin() {
        show()
        primary(R.string.onboarding_have_backup).performClick()
        waitForText(str(R.string.onboarding_restore_title))
        pickBackup()
        phraseCell(1).performTextReplacement(words)

        primary(R.string.restore).performClick()

        waitFor(LONG_WAIT_MS) { onboarding.uiState.value.step == OnboardingStep.PIN }
        waitForText(str(R.string.onboarding_pin_title))
    }

    @Test
    fun backFromThePinReturnsToWelcome() {
        show()
        primary(R.string.onboarding_get_started).performClick()
        waitForText(str(R.string.onboarding_pin_title))

        rule.runOnIdle { onboarding.back() }

        waitForText(str(R.string.onboarding_welcome_headline))
        assertTrue(!completed)
    }

    private fun pickBackup() {
        val file = runBlocking { app.backupFile(words, Note(title = "Plan", value = "[]", uid = "n-plan")) }
        rule.runOnIdle { onboarding.onRestoreFilePicked(app.document("notes.enclybak", file)) }
        waitFor(describe = { "${onboarding.uiState.value}" }) { onboarding.uiState.value.restoreFileReady }
    }

    private companion object {
        const val LONG_WAIT_MS = 15_000L
    }
}
