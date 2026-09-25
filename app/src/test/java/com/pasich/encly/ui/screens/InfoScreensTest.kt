package com.pasich.encly.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.pasich.encly.BuildConfig
import com.pasich.encly.R
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.AboutScreen
import com.pasich.encly.presentation.screen.FaqScreen
import com.pasich.encly.presentation.screen.LicensesScreen
import com.pasich.encly.presentation.screen.LossReason
import com.pasich.encly.presentation.screen.LossRecoveryScreen
import com.pasich.encly.presentation.screen.SupportScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoScreensTest : ComposeScreenTest() {

    @Test
    fun aboutShowsTheAppItsProtectionAndItsLinks() {
        setNavScreen(route = NavRoutes.AboutRoute.name) { nav -> AboutScreen(nav) }

        rule.onNodeWithText(str(R.string.about_data_protection)).assertIsDisplayed()
        scrollToText(str(R.string.about_privacy_policy))
        scrollToText(str(R.string.about_write_developer))
        scrollToText(str(R.string.licenses_title))
        assertTrue(visibleTexts().map(::textOf).any { it.contains(BuildConfig.VERSION_NAME) })
        assertBodyNotEmpty(minTexts = 6)
    }

    @Test
    fun aboutOffersARatingOnlyInThePlayBuild() {
        setNavScreen(route = NavRoutes.AboutRoute.name) { nav -> AboutScreen(nav) }

        assertEquals(BuildConfig.STORE_RATING_ENABLED, countText(str(R.string.about_rate_app)) > 0)
    }

    @Test
    fun aboutLeadsToTheLicenses() {
        setNavScreen(route = NavRoutes.AboutRoute.name) { nav -> AboutScreen(nav) }
        scrollToText(str(R.string.licenses_title))

        rule.onNodeWithText(str(R.string.licenses_title)).performClick()

        waitFor { currentRoute() == NavRoutes.LicensesRoute.name }
    }

    @Test
    fun licensesListLibrariesAndFonts() {
        setNavScreen(route = NavRoutes.LicensesRoute.name) { nav -> LicensesScreen(nav) }

        rule.onNodeWithText(str(R.string.licenses_heading)).assertIsDisplayed()
        scrollToText(str(R.string.licenses_libraries), ignoreCase = true)
        scrollToText(str(R.string.licenses_fonts), ignoreCase = true)
        assertBodyNotEmpty(minTexts = 8)
    }

    @Test
    fun faqAnswersOpenOnTap() {
        setNavScreen(route = NavRoutes.FaqRoute.name) { nav -> FaqScreen(nav) }
        scrollToText(str(R.string.faq_forgot_pin_q))
        assertFalse(countText(str(R.string.faq_forgot_pin_a)) > 0 && isShown(str(R.string.faq_forgot_pin_a)))

        rule.onNodeWithText(str(R.string.faq_forgot_pin_q)).performClick()

        waitFor { isShown(str(R.string.faq_forgot_pin_a)) }
        assertBodyNotEmpty(minTexts = 6)
    }

    @Test
    fun supportExplainsTheDonation() {
        setNavScreen(route = NavRoutes.SupportRoute.name) { nav -> SupportScreen(nav) }

        rule.onNodeWithText(str(R.string.donation_title)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 4)
    }

    @Test
    fun damagedDataExplainsAndWipesOnlyAfterAHold() {
        var wiped = false
        setScreen { LossRecoveryScreen(onRecoveryConfirm = { wiped = true }, reason = LossReason.DAMAGED) }

        rule.onNodeWithText(str(R.string.loss_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.vault_reset_restore_hint)).assertIsDisplayed()
        val button = rule.onNodeWithText(str(R.string.loss_hold_to_wipe))
        button.performClick()
        rule.mainClock.advanceTimeBy(HOLD_MS)
        assertFalse("a tap must not wipe", wiped)

        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(HOLD_MS + 500)
        button.performTouchInput { up() }
        rule.waitForIdle()

        assertTrue(wiped)
    }

    @Test
    fun dataFromAnOlderVersionSaysSo() {
        setScreen { LossRecoveryScreen(onRecoveryConfirm = {}, reason = LossReason.OLDER_VERSION) }

        rule.onNodeWithText(str(R.string.legacy_vault_title)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 3)
    }

    private fun isShown(text: String): Boolean = rule.onAllNodes(androidx.compose.ui.test.hasText(text))
        .fetchSemanticsNodes().any { it.boundsInRoot.height > 0f }

    private companion object {
        const val HOLD_MS = 4_000L
    }
}
