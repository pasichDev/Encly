package com.pasich.encly.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.auth_method_pin_title
import com.pasich.encly.core.resources.auto_lock_title
import com.pasich.encly.core.resources.backup_error_phrase_setup
import com.pasich.encly.core.resources.backup_needs_phrase_title
import com.pasich.encly.core.resources.backup_phrase_exists
import com.pasich.encly.core.resources.backup_phrase_written
import com.pasich.encly.core.resources.backup_reauth_title
import com.pasich.encly.core.resources.duress_pin_confirm_title
import com.pasich.encly.core.resources.duress_pin_current_subtitle
import com.pasich.encly.core.resources.duress_pin_failed
import com.pasich.encly.core.resources.duress_pin_intro
import com.pasich.encly.core.resources.duress_pin_new_title
import com.pasich.encly.core.resources.duress_pin_remove
import com.pasich.encly.core.resources.duress_pin_remove_confirm
import com.pasich.encly.core.resources.duress_pin_remove_confirm_body
import com.pasich.encly.core.resources.duress_pin_removed
import com.pasich.encly.core.resources.duress_pin_row_desc
import com.pasich.encly.core.resources.duress_pin_same_as_pin
import com.pasich.encly.core.resources.duress_pin_saved
import com.pasich.encly.core.resources.duress_pin_saved_kept
import com.pasich.encly.core.resources.duress_pin_set
import com.pasich.encly.core.resources.duress_pin_title
import com.pasich.encly.core.resources.onboarding_verify_label
import com.pasich.encly.core.resources.onboarding_verify_title
import com.pasich.encly.core.resources.pin_change_bar_title
import com.pasich.encly.core.resources.pin_change_confirm_title
import com.pasich.encly.core.resources.pin_change_current_title
import com.pasich.encly.core.resources.pin_changed
import com.pasich.encly.core.resources.pin_create_title
import com.pasich.encly.core.resources.pin_current_wrong
import com.pasich.encly.core.resources.pin_in_use
import com.pasich.encly.core.resources.pin_mismatch_retry
import com.pasich.encly.core.resources.pin_reset_subtitle
import com.pasich.encly.core.resources.pin_update_failed
import com.pasich.encly.core.resources.security_biometric_not_enrolled
import com.pasich.encly.core.resources.security_biometric_title
import com.pasich.encly.core.resources.security_biometric_unavailable
import com.pasich.encly.core.resources.security_encryption_active
import com.pasich.encly.core.resources.security_encryption_pin_plain
import com.pasich.encly.core.resources.security_encryption_seed_plain
import com.pasich.encly.core.resources.security_erase_desc
import com.pasich.encly.core.resources.security_erase_failed
import com.pasich.encly.core.resources.security_erase_title
import com.pasich.encly.core.resources.security_keyboard_strict_title
import com.pasich.encly.core.resources.security_protection_section
import com.pasich.encly.core.resources.security_recovery_missing
import com.pasich.encly.core.resources.security_recovery_replace_title
import com.pasich.encly.core.resources.security_recovery_status_set
import com.pasich.encly.core.resources.wipe_pin_backups
import com.pasich.encly.core.resources.wipe_pin_confirm_title
import com.pasich.encly.core.resources.wipe_pin_current_subtitle
import com.pasich.encly.core.resources.wipe_pin_failed
import com.pasich.encly.core.resources.wipe_pin_intro
import com.pasich.encly.core.resources.wipe_pin_new_title
import com.pasich.encly.core.resources.wipe_pin_remove
import com.pasich.encly.core.resources.wipe_pin_removed
import com.pasich.encly.core.resources.wipe_pin_row_desc
import com.pasich.encly.core.resources.wipe_pin_same_as_pin
import com.pasich.encly.core.resources.wipe_pin_saved
import com.pasich.encly.core.resources.wipe_pin_set
import com.pasich.encly.core.resources.wipe_pin_title
import com.pasich.encly.core.security.AuthSettings
import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.BiometricStatus
import com.pasich.encly.core.security.DuressPinChange
import com.pasich.encly.core.security.Mnemonic
import com.pasich.encly.core.security.PinChange
import com.pasich.encly.core.security.WipePinChange
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.PinCodeConfigScreen
import com.pasich.encly.presentation.screen.settings.DuressPinScreen
import com.pasich.encly.presentation.screen.settings.SecuritySettingsScreen
import com.pasich.encly.presentation.screen.settings.WipePinScreen
import com.pasich.encly.presentation.viewmodel.BackupAction
import com.pasich.encly.presentation.viewmodel.BackupStep
import com.pasich.encly.presentation.viewmodel.BackupViewModel
import com.pasich.encly.testutil.anyCharArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class SecurityScreensTest : ComposeScreenTest() {
    private val words = String(Mnemonic.generate())
    private val app by lazy { TestApp().withWorkingVault(words) }
    private lateinit var vault: BackupViewModel

    private fun showSecurity(recoveryPhrase: Boolean, biometric: BiometricStatus = BiometricStatus.UNAVAILABLE) {
        app.withAuth(recoveryPhrase, biometric)
        vault = app.backup()
        setNavScreen(viewModels(app.securitySettings(), vault), route = NavRoutes.SecuritySettingsRoute.name) { nav ->
            SecuritySettingsScreen(nav)
        }
        waitForText(str(Res.string.security_encryption_active))
    }

    private fun showPinChange(requiresCurrent: Boolean = true) {
        `when`(app.security.canResetPinWithoutCurrent()).thenReturn(!requiresCurrent)
        setNavScreen(
            viewModels(app.securitySettings()),
            route = NavRoutes.PinCodeConfig.name,
            start = NavRoutes.SecuritySettingsRoute.name,
        ) { nav -> PinCodeConfigScreen(nav) }
        navigateTo(NavRoutes.PinCodeConfig.name)
    }

    @Test
    fun withoutAPhraseSecurityOffersToSetOneUp() {
        showSecurity(recoveryPhrase = false)

        rule.onNodeWithText(str(Res.string.auth_method_pin_title)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.security_recovery_missing)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.security_encryption_pin_plain)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 8)
    }

    @Test
    fun withAPhraseSecurityShowsItIsSetUp() {
        showSecurity(recoveryPhrase = true)

        rule.onNodeWithText(str(Res.string.security_recovery_status_set)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.security_encryption_seed_plain)).assertIsDisplayed()
        assertEquals(0, countText(str(Res.string.security_recovery_missing)))
    }

    @Test
    fun thePinRowOpensThePinChange() {
        showSecurity(recoveryPhrase = true)

        rule.onNodeWithText(str(Res.string.auth_method_pin_title)).performClick()

        waitFor { currentRoute() == NavRoutes.PinCodeConfig.name }
    }

    @Test
    fun settingUpAPhraseShowsTwelveWordsThenAsksForThree() {
        showSecurity(recoveryPhrase = false)

        rule.onNodeWithText(str(Res.string.security_recovery_missing)).performClick()
        waitForText(str(Res.string.backup_reauth_title))
        typePin("123456")

        waitFor(describe = { "${vault.uiState.value}" }) { vault.uiState.value.step is BackupStep.ShowNewPhrase }
        words.split(' ').forEach { word -> waitForText(word) }
        rule.onNodeWithText(str(Res.string.backup_phrase_written)).performClick()

        waitForText(str(Res.string.onboarding_verify_title))
        val step = vault.uiState.value.step as BackupStep.CheckNewPhrase
        step.positions.forEach { rule.onNodeWithText(str(Res.string.onboarding_verify_label, it + 1)).assertExists() }
    }

    @Test
    fun aPhraseThatAlreadyExistsIsReported() {
        showSecurity(recoveryPhrase = false)
        `when`(app.security.hasRecoverySeed()).thenReturn(true)

        rule.onNodeWithText(str(Res.string.security_recovery_missing)).performClick()
        waitForText(str(Res.string.backup_reauth_title))
        typePin("123456")

        waitForText(str(Res.string.backup_phrase_exists))
    }

    @Test
    fun theEraseRowNeedsThePinFirst() {
        showSecurity(recoveryPhrase = true)

        scrollToText(str(Res.string.security_erase_desc))
        rule.onNodeWithText(str(Res.string.security_erase_desc)).assertIsDisplayed()
    }

    @Test
    fun changingThePinStartsWithTheCurrentOne() {
        showPinChange()

        rule.onNodeWithText(str(Res.string.pin_change_current_title)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.pin_change_bar_title)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 10)
    }

    @Test
    fun aWrongCurrentPinIsRefused() {
        `when`(app.security.verifyPin(anyCharArray())).thenReturn(false)
        showPinChange()

        typePin("000000")

        waitForText(str(Res.string.pin_current_wrong))
        rule.onNodeWithText(str(Res.string.pin_change_current_title)).assertIsDisplayed()
    }

    @Test
    fun aNewPinIsConfirmedAndSaved() {
        val saved = mutableListOf<String>()
        val passed = mutableListOf<CharArray>()
        `when`(app.security.changePin(anyCharArray())).thenAnswer {
            val pin = it.getArgument<CharArray>(0)
            passed += pin
            saved += String(pin)
            PinChange.SET
        }
        showPinChange()

        typePin("123456")
        waitForText(str(Res.string.pin_create_title))
        typePin("654321")
        waitForText(str(Res.string.pin_change_confirm_title))
        typePin("654321")

        waitFor {
            rule.onAllNodesWithContentDescription(str(Res.string.pin_changed)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf("654321"), saved)
        waitFor { currentRoute() == NavRoutes.SecuritySettingsRoute.name }
        // The PIN handed over was wiped once stored.
        assertTrue(passed.single().all { it == '\u0000' })
    }

    @Test
    fun theBiometricSwitchIsOffLimitsWithoutUsableHardware() {
        showSecurity(recoveryPhrase = true, biometric = BiometricStatus.UNAVAILABLE)

        biometricSwitch().assertIsNotEnabled()
        rule.onNodeWithText(str(Res.string.security_biometric_unavailable)).assertExists()
    }

    @Test
    fun theBiometricSwitchIsOffLimitsWithoutAnEnrolledFingerprint() {
        showSecurity(recoveryPhrase = true, biometric = BiometricStatus.NOT_ENROLLED)

        biometricSwitch().assertIsNotEnabled()
        rule.onNodeWithText(str(Res.string.security_biometric_not_enrolled)).assertExists()
    }

    @Test
    fun theBiometricSwitchWorksWithUsableHardware() {
        showSecurity(recoveryPhrase = true, biometric = BiometricStatus.AVAILABLE)

        biometricSwitch().assertIsEnabled()
    }

    private fun biometricSwitch() = rule.onNode(isToggleable() and hasText(str(Res.string.security_biometric_title)))

    @Test
    fun aMismatchedConfirmationStartsTheNewPinAgain() {
        showPinChange()

        typePin("123456")
        waitForText(str(Res.string.pin_create_title))
        typePin("111111")
        waitForText(str(Res.string.pin_change_confirm_title))
        typePin("222222")

        waitForText(str(Res.string.pin_mismatch_retry))
        rule.onNodeWithText(str(Res.string.pin_create_title)).assertIsDisplayed()
    }

    @Test
    fun afterARecoveryUnlockTheNewPinComesFirstWithNoWayBack() {
        showPinChange(requiresCurrent = false)

        rule.onNodeWithText(str(Res.string.pin_create_title)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.pin_reset_subtitle)).assertIsDisplayed()
        assertEquals(0, countText(str(Res.string.pin_change_bar_title)))
    }

    // --- wipe PIN -------------------------------------------------------------------------

    private fun showWipePin(pinIsRight: Boolean = true) {
        `when`(app.security.verifyPin(anyCharArray())).thenReturn(pinIsRight)
        setNavScreen(
            viewModels(app.securitySettings()),
            route = NavRoutes.WipePinRoute.name,
            start = NavRoutes.SecuritySettingsRoute.name,
        ) { nav -> WipePinScreen(nav) }
        navigateTo(NavRoutes.WipePinRoute.name)
    }

    @Test
    fun theWipePinRowOpensTheWipePinPage() {
        showSecurity(recoveryPhrase = true)

        scrollToText(str(Res.string.wipe_pin_row_desc))
        rule.onNodeWithText(str(Res.string.wipe_pin_row_desc)).performClick()

        waitFor { currentRoute() == NavRoutes.WipePinRoute.name }
    }

    @Test
    fun theWipePinPageAsksForThePinFirst() {
        showWipePin(pinIsRight = false)

        rule.onNodeWithText(str(Res.string.wipe_pin_current_subtitle)).assertIsDisplayed()
        typePin("000000")

        waitForText(str(Res.string.pin_current_wrong))
        assertEquals(0, countText(str(Res.string.wipe_pin_set)))
    }

    @Test
    fun aWipePinIsConfirmedAndSaved() {
        val saved = mutableListOf<String>()
        val passed = mutableListOf<CharArray>()
        showWipePin()
        `when`(app.security.configureWipePin(anyCharArray())).thenAnswer {
            val pin = it.getArgument<CharArray>(0)
            passed += pin
            saved += String(pin)
            pin.fill('\u0000')
            WipePinChange.SET
        }

        typePin("123456")
        waitForText(str(Res.string.wipe_pin_intro))
        rule.onNodeWithText(str(Res.string.wipe_pin_backups)).assertIsDisplayed()
        scrollToText(str(Res.string.wipe_pin_set))
        rule.onNodeWithText(str(Res.string.wipe_pin_set)).performClick()
        waitForText(str(Res.string.wipe_pin_new_title))
        typePin("864200")
        waitForText(str(Res.string.wipe_pin_confirm_title))
        typePin("864200")

        waitForText(str(Res.string.wipe_pin_saved))
        assertEquals(listOf("864200"), saved)
        assertTrue(passed.single().all { it == '\u0000' })
    }

    @Test
    fun aWipePinEqualToThePinIsRefused() {
        showWipePin()
        `when`(app.security.configureWipePin(anyCharArray())).thenReturn(WipePinChange.SAME_AS_PIN)

        typePin("123456")
        waitForText(str(Res.string.wipe_pin_intro))
        scrollToText(str(Res.string.wipe_pin_set))
        rule.onNodeWithText(str(Res.string.wipe_pin_set)).performClick()
        typePin("123456")
        waitForText(str(Res.string.wipe_pin_confirm_title))
        typePin("123456")

        waitForText(str(Res.string.wipe_pin_same_as_pin))
        rule.onNodeWithText(str(Res.string.wipe_pin_new_title)).assertIsDisplayed()
    }

    @Test
    fun theWipePinCanBeTurnedOff() {
        showWipePin()
        `when`(app.security.removeWipePin()).thenReturn(true)

        typePin("123456")
        waitForText(str(Res.string.wipe_pin_intro))
        scrollToText(str(Res.string.wipe_pin_remove))
        rule.onNodeWithText(str(Res.string.wipe_pin_remove)).performClick()

        waitForText(str(Res.string.wipe_pin_removed))
        verify(app.security, never()).configureWipePin(anyCharArray())
    }

    // --- duress PIN -----------------------------------------------------------------------

    private fun showDuressPin(isSet: Boolean = false) {
        `when`(app.security.verifyPin(anyCharArray())).thenReturn(true)
        `when`(app.security.getSettingsAuth()).thenReturn(AuthSettings(AuthType.PIN, false, true, hasDuressPin = isSet))
        setNavScreen(
            viewModels(app.securitySettings()),
            route = NavRoutes.DuressPinRoute.name,
            start = NavRoutes.SecuritySettingsRoute.name,
        ) { nav -> DuressPinScreen(nav) }
        navigateTo(NavRoutes.DuressPinRoute.name)
    }

    private fun enterDuressPin(result: DuressPinChange) {
        `when`(app.security.configureDuressPin(anyCharArray())).thenReturn(result)
        typePin("123456")
        waitForText(str(Res.string.duress_pin_intro))
        scrollToText(str(Res.string.duress_pin_set))
        rule.onNodeWithText(str(Res.string.duress_pin_set)).performClick()
        waitForText(str(Res.string.duress_pin_new_title))
        typePin("864200")
        waitForText(str(Res.string.duress_pin_confirm_title))
        typePin("864200")
    }

    @Test
    fun theDuressPinRowOpensTheDuressPinPage() {
        showSecurity(recoveryPhrase = true)

        scrollToText(str(Res.string.duress_pin_row_desc))
        rule.onNodeWithText(str(Res.string.duress_pin_row_desc)).performClick()

        waitFor { currentRoute() == NavRoutes.DuressPinRoute.name }
    }

    @Test
    fun theDuressPinPageAsksForThePinFirst() {
        showDuressPin()
        `when`(app.security.verifyPin(anyCharArray())).thenReturn(false)

        rule.onNodeWithText(str(Res.string.duress_pin_current_subtitle)).assertIsDisplayed()
        typePin("000000")

        waitForText(str(Res.string.pin_current_wrong))
        assertEquals(0, countText(str(Res.string.duress_pin_set)))
    }

    @Test
    fun aNewDuressPinIsSaved() {
        showDuressPin()

        enterDuressPin(DuressPinChange.SET_NEW_VAULT)

        waitForText(str(Res.string.duress_pin_saved))
    }

    @Test
    fun aChangedDuressPinSaysTheDecoyNotesStay() {
        showDuressPin()

        enterDuressPin(DuressPinChange.SET)

        waitForText(str(Res.string.duress_pin_saved_kept))
    }

    @Test
    fun aDuressPinEqualToThePinIsRefused() {
        showDuressPin()

        enterDuressPin(DuressPinChange.SAME_AS_PIN)

        waitForText(str(Res.string.duress_pin_same_as_pin))
        rule.onNodeWithText(str(Res.string.duress_pin_new_title)).assertIsDisplayed()
    }

    @Test
    fun aDuressPinInUseIsRefusedGenerically() {
        showDuressPin()

        enterDuressPin(DuressPinChange.IN_USE)

        waitForText(str(Res.string.pin_in_use))
    }

    @Test
    fun turningTheDuressPinOffAsksForConfirmation() {
        showDuressPin(isSet = true)
        `when`(app.security.removeDuressPin()).thenReturn(true)

        typePin("123456")
        waitForText(str(Res.string.duress_pin_intro))
        scrollToText(str(Res.string.duress_pin_remove))
        rule.onNodeWithText(str(Res.string.duress_pin_remove)).performClick()
        waitForText(str(Res.string.duress_pin_remove_confirm_body))
        verify(app.security, never()).removeDuressPin()
        rule.onNodeWithText(str(Res.string.duress_pin_remove_confirm)).performClick()

        waitForText(str(Res.string.duress_pin_removed))
    }

    @Test
    fun aPinChangeToTheDuressPinIsRefused() {
        `when`(app.security.changePin(anyCharArray())).thenReturn(PinChange.IN_USE)
        showPinChange()

        typePin("123456")
        waitForText(str(Res.string.pin_create_title))
        typePin("654321")
        waitForText(str(Res.string.pin_change_confirm_title))
        typePin("654321")

        waitForText(str(Res.string.pin_in_use))
    }

    // --- decoy session --------------------------------------------------------------------

    /** What Security lists for a PIN vault with a recovery phrase, in the real vault and the decoy one alike. */
    private val securityRows = listOf(
        Res.string.security_encryption_active,
        Res.string.auth_method_pin_title,
        Res.string.security_biometric_title,
        Res.string.wipe_pin_title,
        Res.string.duress_pin_title,
        Res.string.auto_lock_title,
        Res.string.backup_needs_phrase_title,
        Res.string.security_recovery_replace_title,
        Res.string.security_keyboard_strict_title,
        Res.string.security_protection_section,
        Res.string.security_erase_title,
    )

    private fun assertSecurityRowsListed() = securityRows.forEach {
        // Section headings are drawn in capitals.
        scrollToText(str(it), ignoreCase = true)
        assertTrue(str(it), countText(str(it), ignoreCase = true) > 0)
    }

    @Test
    fun theRealSecurityPageListsTheSharedRows() {
        showSecurity(recoveryPhrase = true)

        assertSecurityRowsListed()
    }

    @Test
    fun theDecoySecurityPageListsTheSameRowsAsTheRealVault() {
        app.inDecoySession()
        showSecurity(recoveryPhrase = true)

        assertSecurityRowsListed()
    }

    @Test
    fun aPinChangeInTheDecoyVaultFailsLikeAStorageError() {
        app.inDecoySession()
        showPinChange()

        typePin("123456")
        waitForText(str(Res.string.pin_create_title))
        typePin("654321")
        waitForText(str(Res.string.pin_change_confirm_title))
        typePin("654321")

        waitForText(str(Res.string.pin_update_failed))
        assertEquals(NavRoutes.PinCodeConfig.name, currentRoute())
    }

    @Test
    fun aWipePinInTheDecoyVaultFailsLikeAStorageError() {
        app.inDecoySession()
        showWipePin()

        typePin("123456")
        waitForText(str(Res.string.wipe_pin_intro))
        scrollToText(str(Res.string.wipe_pin_set))
        rule.onNodeWithText(str(Res.string.wipe_pin_set)).performClick()
        typePin("864200")
        waitForText(str(Res.string.wipe_pin_confirm_title))
        typePin("864200")

        waitForText(str(Res.string.wipe_pin_failed))
    }

    @Test
    fun turningTheWipePinOffInTheDecoyVaultFailsLikeAStorageError() {
        app.inDecoySession()
        showWipePin()

        typePin("123456")
        waitForText(str(Res.string.wipe_pin_intro))
        scrollToText(str(Res.string.wipe_pin_remove))
        rule.onNodeWithText(str(Res.string.wipe_pin_remove)).performClick()

        waitForText(str(Res.string.wipe_pin_failed))
    }

    @Test
    fun aDuressPinInTheDecoyVaultFailsLikeAStorageError() {
        app.inDecoySession()
        showDuressPin()

        enterDuressPin(DuressPinChange.FAILED)

        waitForText(str(Res.string.duress_pin_failed))
    }

    @Test
    fun eraseInTheDecoyVaultFailsAndKeepsTheVault() {
        app.inDecoySession()
        showSecurity(recoveryPhrase = true)

        rule.runOnIdle { vault.start(BackupAction.ERASE) }
        waitForText(str(Res.string.backup_reauth_title))
        typePin("123456")

        waitForText(str(Res.string.security_erase_failed))
        assertEquals(false, vault.uiState.value.erased)
        assertEquals(NavRoutes.SecuritySettingsRoute.name, currentRoute())
        assertTrue(countText(str(Res.string.security_erase_title)) > 0)
    }

    @Test
    fun aNewRecoveryPhraseInTheDecoyVaultFailsAtTheEnd() {
        app.inDecoySession(recoveryPhrase = false)
        showSecurity(recoveryPhrase = false)

        rule.onNodeWithText(str(Res.string.security_recovery_missing)).performClick()
        waitForText(str(Res.string.backup_reauth_title))
        typePin("123456")
        waitFor(describe = { "${vault.uiState.value}" }) { vault.uiState.value.step is BackupStep.ShowNewPhrase }
        rule.onNodeWithText(str(Res.string.backup_phrase_written)).performClick()
        waitFor(describe = { "${vault.uiState.value}" }) { vault.uiState.value.step is BackupStep.CheckNewPhrase }
        val step = vault.uiState.value.step as BackupStep.CheckNewPhrase
        rule.runOnIdle { vault.phraseFlow.submitCheck(step.expected) }

        waitForText(str(Res.string.backup_error_phrase_setup))
    }
}
