package com.pasich.encly.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import com.pasich.encly.R
import com.pasich.encly.core.security.BiometricStatus
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.PinCodeConfigScreen
import com.pasich.encly.presentation.screen.settings.SecuritySettingsScreen
import com.pasich.encly.presentation.viewmodel.BackupStep
import com.pasich.encly.presentation.viewmodel.BackupViewModel
import com.pasich.encly.testutil.anyCharArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`

class SecurityScreensTest : ComposeScreenTest() {
    private val words = String(MnemonicCode(WordCount.COUNT_12).chars)
    private val app by lazy { TestApp(context).withWorkingVault(words) }
    private lateinit var vault: BackupViewModel

    private fun showSecurity(recoveryPhrase: Boolean, biometric: BiometricStatus = BiometricStatus.UNAVAILABLE) {
        app.withAuth(recoveryPhrase, biometric)
        vault = app.backup()
        setNavScreen(viewModels(app.securitySettings(), vault), route = NavRoutes.SecuritySettingsRoute.name) { nav ->
            SecuritySettingsScreen(nav)
        }
        waitForText(str(R.string.security_encryption_active))
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

        rule.onNodeWithText(str(R.string.auth_method_pin_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.security_recovery_missing)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.security_encryption_pin_plain)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 8)
    }

    @Test
    fun withAPhraseSecurityShowsItIsSetUp() {
        showSecurity(recoveryPhrase = true)

        rule.onNodeWithText(str(R.string.security_recovery_status_set)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.security_encryption_seed_plain)).assertIsDisplayed()
        assertEquals(0, countText(str(R.string.security_recovery_missing)))
    }

    @Test
    fun thePinRowOpensThePinChange() {
        showSecurity(recoveryPhrase = true)

        rule.onNodeWithText(str(R.string.auth_method_pin_title)).performClick()

        waitFor { currentRoute() == NavRoutes.PinCodeConfig.name }
    }

    @Test
    fun settingUpAPhraseShowsTwelveWordsThenAsksForThree() {
        showSecurity(recoveryPhrase = false)

        rule.onNodeWithText(str(R.string.security_recovery_missing)).performClick()
        waitForText(str(R.string.backup_reauth_title))
        typePin("123456")

        waitFor(describe = { "${vault.uiState.value}" }) { vault.uiState.value.step is BackupStep.ShowNewPhrase }
        words.split(' ').forEach { word -> waitForText(word) }
        rule.onNodeWithText(str(R.string.backup_phrase_written)).performClick()

        waitForText(str(R.string.onboarding_verify_title))
        val step = vault.uiState.value.step as BackupStep.CheckNewPhrase
        step.positions.forEach { rule.onNodeWithText(str(R.string.onboarding_verify_label, it + 1)).assertExists() }
    }

    @Test
    fun aPhraseThatAlreadyExistsIsReported() {
        showSecurity(recoveryPhrase = false)
        `when`(app.security.hasRecoverySeed()).thenReturn(true)

        rule.onNodeWithText(str(R.string.security_recovery_missing)).performClick()
        waitForText(str(R.string.backup_reauth_title))
        typePin("123456")

        waitForText(str(R.string.backup_phrase_exists))
    }

    @Test
    fun theEraseRowNeedsThePinFirst() {
        showSecurity(recoveryPhrase = true)

        scrollToText(str(R.string.security_erase_desc))
        rule.onNodeWithText(str(R.string.security_erase_desc)).assertIsDisplayed()
    }

    @Test
    fun changingThePinStartsWithTheCurrentOne() {
        showPinChange()

        rule.onNodeWithText(str(R.string.pin_change_current_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.pin_change_bar_title)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 10)
    }

    @Test
    fun aWrongCurrentPinIsRefused() {
        `when`(app.security.verifyPin(anyCharArray())).thenReturn(false)
        showPinChange()

        typePin("000000")

        waitForText(str(R.string.pin_current_wrong))
        rule.onNodeWithText(str(R.string.pin_change_current_title)).assertIsDisplayed()
    }

    @Test
    fun aNewPinIsConfirmedAndSaved() {
        val saved = mutableListOf<String>()
        val passed = mutableListOf<CharArray>()
        `when`(app.security.configurePin(anyCharArray())).thenAnswer {
            val pin = it.getArgument<CharArray>(0)
            passed += pin
            saved += String(pin)
            true
        }
        showPinChange()

        typePin("123456")
        waitForText(str(R.string.pin_create_title))
        typePin("654321")
        waitForText(str(R.string.pin_change_confirm_title))
        typePin("654321")

        waitFor { rule.onAllNodesWithContentDescription(str(R.string.pin_changed)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("654321"), saved)
        waitFor { currentRoute() == NavRoutes.SecuritySettingsRoute.name }
        // The PIN handed over was wiped once stored.
        assertTrue(passed.single().all { it == '\u0000' })
    }

    @Test
    fun theBiometricSwitchIsOffLimitsWithoutUsableHardware() {
        showSecurity(recoveryPhrase = true, biometric = BiometricStatus.UNAVAILABLE)

        biometricSwitch().assertIsNotEnabled()
        rule.onNodeWithText(str(R.string.security_biometric_unavailable)).assertExists()
    }

    @Test
    fun theBiometricSwitchIsOffLimitsWithoutAnEnrolledFingerprint() {
        showSecurity(recoveryPhrase = true, biometric = BiometricStatus.NOT_ENROLLED)

        biometricSwitch().assertIsNotEnabled()
        rule.onNodeWithText(str(R.string.security_biometric_not_enrolled)).assertExists()
    }

    @Test
    fun theBiometricSwitchWorksWithUsableHardware() {
        showSecurity(recoveryPhrase = true, biometric = BiometricStatus.AVAILABLE)

        biometricSwitch().assertIsEnabled()
    }

    private fun biometricSwitch() = rule.onNode(isToggleable() and hasText(str(R.string.security_biometric_title)))

    @Test
    fun aMismatchedConfirmationStartsTheNewPinAgain() {
        showPinChange()

        typePin("123456")
        waitForText(str(R.string.pin_create_title))
        typePin("111111")
        waitForText(str(R.string.pin_change_confirm_title))
        typePin("222222")

        waitForText(str(R.string.pin_mismatch_retry))
        rule.onNodeWithText(str(R.string.pin_create_title)).assertIsDisplayed()
    }

    @Test
    fun afterARecoveryUnlockTheNewPinComesFirstWithNoWayBack() {
        showPinChange(requiresCurrent = false)

        rule.onNodeWithText(str(R.string.pin_create_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.pin_reset_subtitle)).assertIsDisplayed()
        assertEquals(0, countText(str(R.string.pin_change_bar_title)))
    }
}
