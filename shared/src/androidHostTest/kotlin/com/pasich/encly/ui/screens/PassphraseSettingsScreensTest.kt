package com.pasich.encly.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasich.encly.core.common.ComposeAppStrings
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.auth_method_passphrase_desc
import com.pasich.encly.core.resources.auth_method_passphrase_title
import com.pasich.encly.core.resources.auth_method_pin_title
import com.pasich.encly.core.resources.backup_import
import com.pasich.encly.core.resources.backup_needs_phrase_body_passphrase
import com.pasich.encly.core.resources.backup_reauth_passphrase_subtitle
import com.pasich.encly.core.resources.backup_reauth_title
import com.pasich.encly.core.resources.duress_passphrase_confirm_title
import com.pasich.encly.core.resources.duress_passphrase_intro
import com.pasich.encly.core.resources.duress_passphrase_new_title
import com.pasich.encly.core.resources.duress_passphrase_remove
import com.pasich.encly.core.resources.duress_passphrase_remove_confirm_title
import com.pasich.encly.core.resources.duress_passphrase_row_desc
import com.pasich.encly.core.resources.duress_passphrase_same_as_passphrase
import com.pasich.encly.core.resources.duress_passphrase_saved
import com.pasich.encly.core.resources.duress_passphrase_set
import com.pasich.encly.core.resources.duress_passphrase_turned_off
import com.pasich.encly.core.resources.faq_passphrase_length_q
import com.pasich.encly.core.resources.faq_pin_length_q
import com.pasich.encly.core.resources.lock_wrong_passphrase
import com.pasich.encly.core.resources.markdown_export_warning_body_passphrase
import com.pasich.encly.core.resources.passphrase_change_confirm_title
import com.pasich.encly.core.resources.passphrase_change_current_title
import com.pasich.encly.core.resources.passphrase_changed
import com.pasich.encly.core.resources.passphrase_create_title
import com.pasich.encly.core.resources.passphrase_current_wrong
import com.pasich.encly.core.resources.passphrase_in_use
import com.pasich.encly.core.resources.passphrase_label
import com.pasich.encly.core.resources.passphrase_mismatch_retry
import com.pasich.encly.core.resources.passphrase_reset_subtitle
import com.pasich.encly.core.resources.passphrase_suggest
import com.pasich.encly.core.resources.passphrase_too_short
import com.pasich.encly.core.resources.pdf_export_warning_body_passphrase
import com.pasich.encly.core.resources.print_warning_body_passphrase
import com.pasich.encly.core.resources.recovery_choice_body_passphrase
import com.pasich.encly.core.resources.recovery_keep_passphrase
import com.pasich.encly.core.resources.recovery_set_new_passphrase
import com.pasich.encly.core.resources.security_encryption_active
import com.pasich.encly.core.resources.security_recovery_replace_confirm
import com.pasich.encly.core.resources.security_recovery_replace_warning_body_passphrase
import com.pasich.encly.core.resources.wipe_passphrase_confirm_title
import com.pasich.encly.core.resources.wipe_passphrase_intro
import com.pasich.encly.core.resources.wipe_passphrase_new_title
import com.pasich.encly.core.resources.wipe_passphrase_row_desc
import com.pasich.encly.core.resources.wipe_passphrase_same_as_passphrase
import com.pasich.encly.core.resources.wipe_passphrase_saved
import com.pasich.encly.core.resources.wipe_passphrase_set
import com.pasich.encly.core.resources.wipe_passphrase_turned_off
import com.pasich.encly.core.security.AuthSettings
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.DuressPinChange
import com.pasich.encly.core.security.PinChange
import com.pasich.encly.core.security.PinCheck
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.WipePinChange
import com.pasich.encly.data.backup.BackupPhraseSetup
import com.pasich.encly.platform.HostPlatform
import com.pasich.encly.platform.LocalHostPlatform
import com.pasich.encly.platform.NoteDocumentRenderer
import com.pasich.encly.presentation.navigation.LocalVaultLocked
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.FaqScreen
import com.pasich.encly.presentation.screen.PinCodeConfigScreen
import com.pasich.encly.presentation.screen.RecoveryChoiceScreen
import com.pasich.encly.presentation.screen.backup.BackupScreen
import com.pasich.encly.presentation.screen.backup.MarkdownExportHost
import com.pasich.encly.presentation.screen.settings.DuressPinScreen
import com.pasich.encly.presentation.screen.settings.SecuritySettingsScreen
import com.pasich.encly.presentation.screen.settings.WipePinScreen
import com.pasich.encly.presentation.viewmodel.BackupAction
import com.pasich.encly.presentation.viewmodel.BackupStep
import com.pasich.encly.presentation.viewmodel.BackupViewModel
import com.pasich.encly.presentation.viewmodel.DocumentFormat
import com.pasich.encly.presentation.viewmodel.MarkdownExportTarget
import com.pasich.encly.presentation.viewmodel.MarkdownExportViewModel
import com.pasich.encly.testutil.anyCharArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** Changing the passphrase, and the first-time choice after a recovery phrase unlock. */
@OptIn(ExperimentalTestApi::class)
internal class PassphraseChangeScreenTest : PassphraseScreenTest() {
    private fun showChange(requiresCurrent: Boolean = true) {
        `when`(app.security.canResetPinWithoutCurrent()).thenReturn(!requiresCurrent)
        setNavScreen(
            viewModels(passphraseSettings()),
            route = NavRoutes.PinCodeConfig.name,
            start = NavRoutes.SecuritySettingsRoute.name,
        ) { nav ->
            CompositionLocalProvider(LocalVaultLocked provides vaultLocked) { PinCodeConfigScreen(nav) }
        }
        navigateTo(NavRoutes.PinCodeConfig.name)
    }

    private var vaultLocked by mutableStateOf(false)

    @Test
    fun whileTheCurrentPassphraseIsBeingCheckedNothingMoreIsTakenAndItIsCheckedOnce() {
        val release = java.util.concurrent.CountDownLatch(1)
        `when`(app.security.verifyPinResult(anyCharArray())).thenAnswer {
            release.await(10, java.util.concurrent.TimeUnit.SECONDS)
            PinCheck.OK
        }
        showChange()

        enterPassphrase(good)

        waitFor {
            rule.onAllNodesWithContentDescription(str(Res.string.passphrase_label)).fetchSemanticsNodes().size ==
                1
        }
        rule.onNodeWithContentDescription(str(Res.string.passphrase_label)).assertIsNotEnabled()
        release.countDown()
        waitForText(str(Res.string.passphrase_create_title))
        verify(app.security, times(1)).verifyPinResult(anyCharArray())
    }

    @Test
    fun aLockInTheMiddleDropsTheTypedPassphraseAtOnceAndTheFieldTakesNoKeys() {
        showChange(requiresCurrent = false)
        typePassphrase("half typed passphrase")

        rule.runOnIdle { vaultLocked = true }
        rule.waitForIdle()

        assertTrue(fieldIsEmpty())
        rule.onNodeWithContentDescription(str(Res.string.passphrase_label)).assertIsNotEnabled()
    }

    @Test
    fun theCurrentPassphraseIsAskedFirstAndAWrongOneIsRefusedAndCleared() {
        `when`(app.security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.WRONG)
        showChange()
        rule.onNodeWithText(str(Res.string.passphrase_change_current_title)).assertIsDisplayed()

        enterPassphrase("not the passphrase")

        waitForText(str(Res.string.passphrase_current_wrong))
        assertTrue(fieldIsEmpty())
        assertEquals(0, countText(str(Res.string.passphrase_create_title)))
    }

    @Test
    fun aRightOneOpensTheNewPassphraseStepWithTheSuggestion() {
        `when`(app.security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        showChange()

        enterPassphrase(good)

        waitForText(str(Res.string.passphrase_create_title))
        rule.onNodeWithText(str(Res.string.passphrase_suggest)).assertIsDisplayed()
    }

    @Test
    fun afterTheRecoveryPhraseTheNewPassphraseIsAskedWithoutTheCurrentOne() {
        showChange(requiresCurrent = false)

        rule.onNodeWithText(str(Res.string.passphrase_create_title)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.passphrase_reset_subtitle)).assertIsDisplayed()
    }

    @Test
    fun aTooShortNewPassphraseIsRefusedWithTheReasonAndKeepsItsText() {
        showChange(requiresCurrent = false)

        enterPassphrase("short")

        waitForText(str(Res.string.passphrase_too_short))
        assertTrue(!fieldIsEmpty())
        assertEquals(0, countText(str(Res.string.passphrase_change_confirm_title)))
    }

    @Test
    fun aNewPassphraseIsConfirmedAndSaved() {
        val saved = mutableListOf<String>()
        // Read when it is called: the screen wipes the array once the call is done.
        `when`(app.security.changePin(anyCharArray())).thenAnswer {
            saved += String(it.getArgument<CharArray>(0))
            PinChange.SET
        }
        showChange(requiresCurrent = false)

        enterPassphrase(good)
        waitForText(str(Res.string.passphrase_change_confirm_title))
        enterPassphrase(good)

        waitFor { currentRoute() != NavRoutes.PinCodeConfig.name || countText(str(Res.string.passphrase_changed)) > 0 }
        assertEquals(listOf(good), saved)
    }

    @Test
    fun aDifferentSecondEntryStartsOverWithTheMismatchMessage() {
        showChange(requiresCurrent = false)
        enterPassphrase(good)
        waitForText(str(Res.string.passphrase_change_confirm_title))

        enterPassphrase("correct horse batterz")

        waitForText(str(Res.string.passphrase_mismatch_retry))
        verify(app.security, never()).changePin(anyCharArray())
    }

    @Test
    fun aPassphraseInUseByTheDuressSecretIsRefusedWithTheExistingWording() {
        `when`(app.security.changePin(anyCharArray())).thenReturn(PinChange.IN_USE)
        showChange(requiresCurrent = false)
        enterPassphrase(good)
        waitForText(str(Res.string.passphrase_change_confirm_title))

        enterPassphrase(good)

        waitForText(str(Res.string.passphrase_in_use))
    }

    @Test
    fun theSuggestionFillsTheFieldAndPassesTheRule() {
        `when`(app.security.changePin(anyCharArray())).thenReturn(PinChange.SET)
        showChange(requiresCurrent = false)

        press(str(Res.string.passphrase_suggest))
        pressContinue()

        waitForText(str(Res.string.passphrase_change_confirm_title))
        assertEquals(1, policy.suggestCalls)
    }

    @Test
    fun theChoiceAfterARecoveryPhraseSpeaksOfThePassphrase() {
        setNavScreen(
            viewModels(passphraseLock()),
            route = NavRoutes.RecoveryChoiceRoute.name,
        ) { nav -> RecoveryChoiceScreen(nav) }

        rule.onNodeWithText(str(Res.string.recovery_choice_body_passphrase)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.recovery_keep_passphrase)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.recovery_set_new_passphrase)).assertIsDisplayed()
    }
}

/** The wipe secret and the duress secret, set as passphrases. */
@OptIn(ExperimentalTestApi::class)
internal class PassphraseWipeAndDuressScreenTest : PassphraseScreenTest() {
    private fun showWipe() {
        `when`(app.security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        setNavScreen(
            viewModels(passphraseSettings()),
            route = NavRoutes.WipePinRoute.name,
            start = NavRoutes.SecuritySettingsRoute.name,
        ) { nav -> WipePinScreen(nav) }
        navigateTo(NavRoutes.WipePinRoute.name)
    }

    private fun showDuress() {
        `when`(app.security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        setNavScreen(
            viewModels(passphraseSettings()),
            route = NavRoutes.DuressPinRoute.name,
            start = NavRoutes.SecuritySettingsRoute.name,
        ) { nav -> DuressPinScreen(nav) }
        navigateTo(NavRoutes.DuressPinRoute.name)
    }

    @Test
    fun aWipePassphraseIsChosenConfirmedAndSaved() {
        val saved = mutableListOf<String>()
        `when`(app.security.configureWipePin(anyCharArray())).thenAnswer {
            saved += String(it.getArgument<CharArray>(0))
            WipePinChange.SET
        }
        showWipe()
        enterPassphrase(good)
        waitForText(str(Res.string.wipe_passphrase_intro))
        scrollToText(str(Res.string.wipe_passphrase_set))
        press(str(Res.string.wipe_passphrase_set))
        waitForText(str(Res.string.wipe_passphrase_new_title))

        enterPassphrase("another long passphrase")
        waitForText(str(Res.string.wipe_passphrase_confirm_title))
        enterPassphrase("another long passphrase")

        waitForText(str(Res.string.wipe_passphrase_saved))
        assertEquals(listOf("another long passphrase"), saved)
    }

    @Test
    fun aWipePassphraseEqualToTheMainOneIsRefusedWithTheExistingMessage() {
        `when`(app.security.configureWipePin(anyCharArray())).thenReturn(WipePinChange.SAME_AS_PIN)
        showWipe()
        enterPassphrase(good)
        waitForText(str(Res.string.wipe_passphrase_intro))
        scrollToText(str(Res.string.wipe_passphrase_set))
        press(str(Res.string.wipe_passphrase_set))
        enterPassphrase(good)
        waitForText(str(Res.string.wipe_passphrase_confirm_title))
        enterPassphrase(good)

        waitForText(str(Res.string.wipe_passphrase_same_as_passphrase))
        rule.onNodeWithText(str(Res.string.wipe_passphrase_new_title)).assertIsDisplayed()
    }

    @Test
    fun aTooShortWipePassphraseIsRefusedBeforeItIsAskedAgain() {
        showWipe()
        enterPassphrase(good)
        waitForText(str(Res.string.wipe_passphrase_intro))
        scrollToText(str(Res.string.wipe_passphrase_set))
        press(str(Res.string.wipe_passphrase_set))

        enterPassphrase("short")

        waitForText(str(Res.string.passphrase_too_short))
        assertEquals(0, countText(str(Res.string.wipe_passphrase_confirm_title)))
    }

    @Test
    fun theSuggestionIsOfferedWhereAWipePassphraseIsChosenAndFillsTheField() {
        showWipe()
        enterPassphrase(good)
        waitForText(str(Res.string.wipe_passphrase_intro))
        scrollToText(str(Res.string.wipe_passphrase_set))
        press(str(Res.string.wipe_passphrase_set))
        waitForText(str(Res.string.wipe_passphrase_new_title))

        press(str(Res.string.passphrase_suggest))
        pressContinue()

        waitForText(str(Res.string.wipe_passphrase_confirm_title))
        assertEquals(1, policy.suggestCalls)
    }

    @Test
    fun aLockInTheMiddleOfAWipePassphraseDropsWhatWasTypedAndKept() {
        var locked by mutableStateOf(false)
        `when`(app.security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        setNavScreen(
            viewModels(passphraseSettings()),
            route = NavRoutes.WipePinRoute.name,
            start = NavRoutes.SecuritySettingsRoute.name,
        ) { nav -> CompositionLocalProvider(LocalVaultLocked provides locked) { WipePinScreen(nav) } }
        navigateTo(NavRoutes.WipePinRoute.name)
        enterPassphrase(good)
        waitForText(str(Res.string.wipe_passphrase_intro))
        scrollToText(str(Res.string.wipe_passphrase_set))
        press(str(Res.string.wipe_passphrase_set))
        enterPassphrase(good)
        waitForText(str(Res.string.wipe_passphrase_confirm_title))
        typePassphrase("half typed")

        rule.runOnIdle { locked = true }
        rule.waitForIdle()

        // Back where the secret is asked for from the start, with nothing kept and nothing typed.
        waitForText(str(Res.string.wipe_passphrase_intro))
    }

    @Test
    fun aWipePassphraseTurnedOffByAKeyResetIsSaidSo() {
        `when`(app.security.getSettingsAuth())
            .thenReturn(AuthSettings(AuthType.PIN, false, true, wipePinTurnedOff = true))
        showWipe()
        enterPassphrase(good)

        waitForText(str(Res.string.wipe_passphrase_turned_off))
    }

    @Test
    fun aDuressPassphraseTurnedOffByAKeyResetIsSaidSo() {
        `when`(app.security.getSettingsAuth())
            .thenReturn(AuthSettings(AuthType.PIN, false, true, hasDuressPin = true, duressPinTurnedOff = true))
        showDuress()
        enterPassphrase(good)

        waitForText(str(Res.string.duress_passphrase_turned_off))
    }

    @Test
    fun turningOffTheDuressPassphraseAsksFirstInThePassphraseWording() {
        `when`(app.security.getSettingsAuth())
            .thenReturn(AuthSettings(AuthType.PIN, false, true, hasDuressPin = true))
        showDuress()
        enterPassphrase(good)
        waitForText(str(Res.string.duress_passphrase_intro))

        scrollToText(str(Res.string.duress_passphrase_remove))
        press(str(Res.string.duress_passphrase_remove))

        waitForText(str(Res.string.duress_passphrase_remove_confirm_title))
    }

    @Test
    fun aDuressPassphraseIsSavedAndOneEqualToTheMainOneIsRefused() {
        `when`(app.security.configureDuressPin(anyCharArray())).thenReturn(DuressPinChange.SAME_AS_PIN)
        showDuress()
        enterPassphrase(good)
        waitForText(str(Res.string.duress_passphrase_intro))
        press(str(Res.string.duress_passphrase_set))
        waitForText(str(Res.string.duress_passphrase_new_title))
        enterPassphrase(good)
        waitForText(str(Res.string.duress_passphrase_confirm_title))
        enterPassphrase(good)

        waitForText(str(Res.string.duress_passphrase_same_as_passphrase))
        `when`(app.security.configureDuressPin(anyCharArray())).thenReturn(DuressPinChange.SET_NEW_VAULT)
        enterPassphrase(good)
        waitForText(str(Res.string.duress_passphrase_confirm_title))
        enterPassphrase(good)
        waitForText(str(Res.string.duress_passphrase_saved))
    }
}

/** The pages that name the secret: settings, the re-authentication dialog and the FAQ. */
@OptIn(ExperimentalTestApi::class)
internal class PassphraseWordingScreenTest : PassphraseScreenTest() {
    private fun backupViewModel() = BackupViewModel(
        backupManager = app.backupManager,
        phraseSetup = BackupPhraseSetup(app.security),
        securityManager = app.security,
        sessionLockManager = app.sessionLock,
        documents = app.documents,
        screenshots = app.screenshots,
        policy = policy,
    )

    @Test
    fun settingsSpeakOfThePassphraseEverywhereThePinWasNamed() {
        app.withAuth(recoveryPhrase = true)
        setNavScreen(
            viewModels(passphraseSettings(), app.notePreviewsVm(), app.toolbarVm(), backupViewModel()),
            route = NavRoutes.SecuritySettingsRoute.name,
        ) { nav -> SecuritySettingsScreen(nav) }
        waitForText(str(Res.string.security_encryption_active))

        rule.onNodeWithText(str(Res.string.auth_method_passphrase_title)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.auth_method_passphrase_desc)).assertIsDisplayed()
        scrollToText(str(Res.string.wipe_passphrase_row_desc))
        scrollToText(str(Res.string.duress_passphrase_row_desc))
        assertEquals(0, countText(str(Res.string.auth_method_pin_title)))
    }

    @Test
    fun theReauthenticationAsksForThePassphraseInAField() {
        `when`(app.security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.WRONG)
        val backup = backupViewModel()
        setNavScreen(viewModels(backup, app.markdownExport()), route = NavRoutes.BackupRoute.name) { nav ->
            BackupScreen(nav)
        }

        rule.onNode(hasText(str(Res.string.backup_import)) and hasClickAction()).performClick()
        waitForText(str(Res.string.backup_reauth_title))
        rule.onNodeWithText(str(Res.string.backup_reauth_passphrase_subtitle)).assertIsDisplayed()
        assertNoKeypad()
        enterPassphrase("not the passphrase")

        waitForText(str(Res.string.lock_wrong_passphrase))
        assertTrue(fieldIsEmpty())
        assertTrue(backup.uiState.value.step is BackupStep.Reauth)
    }

    @Test
    fun theFaqAnswersAboutThePassphraseWhereThatIsTheSecret() {
        setNavScreen(route = NavRoutes.FaqRoute.name) { nav -> FaqScreen(nav, secretKind = SecretKind.PASSWORD) }

        scrollToText(str(Res.string.faq_passphrase_length_q))
        assertEquals(0, countText(str(Res.string.faq_pin_length_q)))
    }

    @Test
    fun aDesktopHostGetsThePassphraseFaqWithoutBeingTold() {
        setNavScreen(route = NavRoutes.FaqRoute.name) { nav ->
            CompositionLocalProvider(LocalHostPlatform provides HostPlatform.DESKTOP) { FaqScreen(nav) }
        }

        scrollToText(str(Res.string.faq_passphrase_length_q))
    }

    @Test
    fun theExportWarningAndItsReauthenticationSpeakOfThePassphrase() {
        val export = MarkdownExportViewModel(
            app.security,
            app.sessionLock,
            app.store,
            app.markdownDocuments,
            ComposeAppStrings(),
            policy,
        )
        setScreen(viewModels(export)) { MarkdownExportHost(onMessage = {}, viewModel = export) }

        rule.runOnIdle { export.start(MarkdownExportTarget.AllData) }
        waitForText(str(Res.string.markdown_export_warning_body_passphrase))
        rule.onNodeWithText(str(Res.string.action_continue)).performClick()

        waitForText(str(Res.string.backup_reauth_passphrase_subtitle))
    }

    @Test
    fun thePrintAndPdfWarningsSpeakOfThePassphrase() {
        val export = MarkdownExportViewModel(
            app.security,
            app.sessionLock,
            app.store,
            app.markdownDocuments,
            ComposeAppStrings(),
            policy,
        )
        val renderer = mock(NoteDocumentRenderer::class.java)
        setScreen(viewModels(export)) { MarkdownExportHost(onMessage = {}, viewModel = export) }

        rule.runOnIdle { export.start(MarkdownExportTarget.NoteAsDocument(1L, DocumentFormat.PRINT, renderer)) }
        waitForText(str(Res.string.print_warning_body_passphrase))
        rule.runOnIdle { export.start(MarkdownExportTarget.NoteAsDocument(1L, DocumentFormat.PDF, renderer)) }
        waitForText(str(Res.string.pdf_export_warning_body_passphrase))
    }

    @Test
    fun anExportWithoutARecoveryPhraseExplainsItInThePassphraseWording() {
        `when`(app.security.verifyPinResult(anyCharArray())).thenReturn(PinCheck.OK)
        `when`(app.security.hasBackupKey()).thenReturn(false)
        val backup = backupViewModel()
        setNavScreen(viewModels(backup, app.markdownExport()), route = NavRoutes.BackupRoute.name) { nav ->
            BackupScreen(nav)
        }

        rule.runOnIdle { backup.start(BackupAction.EXPORT) }
        waitForText(str(Res.string.backup_reauth_title))
        enterPassphrase(good)

        waitForText(str(Res.string.backup_needs_phrase_body_passphrase))
    }

    @Test
    fun replacingTheRecoveryPhraseWarnsInThePassphraseWording() {
        app.withWorkingVault(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        )
        `when`(app.security.hasBackupKey()).thenReturn(true)
        `when`(app.security.hasRecoverySeed()).thenReturn(true)
        val backup = backupViewModel()
        setNavScreen(viewModels(backup, app.markdownExport()), route = NavRoutes.BackupRoute.name) { nav ->
            BackupScreen(nav)
        }

        rule.runOnIdle { backup.start(BackupAction.REPLACE_PHRASE) }
        waitForText(str(Res.string.backup_reauth_title))
        enterPassphrase(good)
        press(str(Res.string.security_recovery_replace_confirm))

        waitForText(str(Res.string.security_recovery_replace_warning_body_passphrase))
    }

    @Test
    fun whileTheReauthenticationChecksTheFieldWaitsAndAWrongAnswerIsAnnouncedAgainAfterAnEdit() {
        val release = java.util.concurrent.CountDownLatch(1)
        `when`(app.security.verifyPinResult(anyCharArray())).thenAnswer {
            release.await(10, java.util.concurrent.TimeUnit.SECONDS)
            PinCheck.WRONG
        }
        val backup = backupViewModel()
        setNavScreen(viewModels(backup, app.markdownExport()), route = NavRoutes.BackupRoute.name) { nav ->
            BackupScreen(nav)
        }
        rule.runOnIdle { backup.start(BackupAction.IMPORT) }
        waitForText(str(Res.string.backup_reauth_title))

        enterPassphrase("not the passphrase")

        waitFor { backup.uiState.value.busy }
        rule.onNodeWithContentDescription(str(Res.string.passphrase_label)).assertIsNotEnabled()
        release.countDown()
        waitForText(str(Res.string.lock_wrong_passphrase))
        verify(app.security, times(1)).verifyPinResult(anyCharArray())

        typePassphrase("n")

        assertEquals(0, countText(str(Res.string.lock_wrong_passphrase)))
    }

    @Test
    fun aLockoutOnTheReauthenticationShowsTheCountdownAndTakesNoInput() {
        `when`(app.security.pinLockoutRemainingMillis()).thenReturn(30_000L)
        val backup = backupViewModel()
        setNavScreen(viewModels(backup, app.markdownExport()), route = NavRoutes.BackupRoute.name) { nav ->
            BackupScreen(nav)
        }

        rule.runOnIdle { backup.start(BackupAction.IMPORT) }
        waitForText(str(Res.string.backup_reauth_title))

        assertEquals(0, countText(str(Res.string.backup_reauth_passphrase_subtitle)))
    }

    @Test
    fun theFaqKeepsThePinWordingByDefaultOnAPhone() {
        setNavScreen(route = NavRoutes.FaqRoute.name) { nav -> FaqScreen(nav) }

        scrollToText(str(Res.string.faq_pin_length_q))
        assertEquals(0, countText(str(Res.string.faq_passphrase_length_q)))
    }
}
