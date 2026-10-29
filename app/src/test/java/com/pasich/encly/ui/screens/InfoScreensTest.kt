package com.pasich.encly.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.pasich.encly.BuildConfig
import com.pasich.encly.core.LINK_REPOSITORY
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.about_data_protection
import com.pasich.encly.core.resources.about_privacy_policy
import com.pasich.encly.core.resources.about_rate_app
import com.pasich.encly.core.resources.about_source_code
import com.pasich.encly.core.resources.about_source_code_desc
import com.pasich.encly.core.resources.about_support_dev_desc
import com.pasich.encly.core.resources.about_write_developer
import com.pasich.encly.core.resources.donation_title
import com.pasich.encly.core.resources.faq_forgot_pin_a
import com.pasich.encly.core.resources.faq_forgot_pin_q
import com.pasich.encly.core.resources.legacy_vault_title
import com.pasich.encly.core.resources.licenses_fonts
import com.pasich.encly.core.resources.licenses_heading
import com.pasich.encly.core.resources.licenses_libraries
import com.pasich.encly.core.resources.licenses_title
import com.pasich.encly.core.resources.loss_hold_to_wipe
import com.pasich.encly.core.resources.loss_title
import com.pasich.encly.core.resources.support_browser_note
import com.pasich.encly.core.resources.support_method_kofi_hint
import com.pasich.encly.core.resources.vault_reset_restore_hint
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

        rule.onNodeWithText(str(Res.string.about_data_protection)).assertIsDisplayed()
        scrollToText(str(Res.string.about_privacy_policy))
        scrollToText(str(Res.string.about_write_developer))
        scrollToText(str(Res.string.licenses_title))
        assertTrue(visibleTexts().map(::textOf).any { it.contains(BuildConfig.VERSION_NAME) })
        assertBodyNotEmpty(minTexts = 6)
    }

    @Test
    fun aboutLinksTheSourceCodeInEveryBuild() {
        setNavScreen(route = NavRoutes.AboutRoute.name) { nav -> AboutScreen(nav) }

        scrollToText(str(Res.string.about_source_code))
        scrollToText(str(Res.string.about_source_code_desc))
        assertEquals("https://github.com/pasichDev/Encly", LINK_REPOSITORY)
    }

    @Test
    fun aboutOffersARatingOnlyInThePlayBuild() {
        setNavScreen(route = NavRoutes.AboutRoute.name) { nav -> AboutScreen(nav) }

        assertEquals(BuildConfig.STORE_RATING_ENABLED, countText(str(Res.string.about_rate_app)) > 0)
    }

    @Test
    fun aboutLinksTheSupportPageOnlyInTheFdroidBuild() {
        setNavScreen(route = NavRoutes.AboutRoute.name) { nav -> AboutScreen(nav) }

        assertEquals(BuildConfig.DONATIONS_ENABLED, countText(str(Res.string.about_support_dev_desc)) > 0)
    }

    @Test
    fun aboutLeadsToTheLicenses() {
        setNavScreen(route = NavRoutes.AboutRoute.name) { nav -> AboutScreen(nav) }
        scrollToText(str(Res.string.licenses_title))

        rule.onNodeWithText(str(Res.string.licenses_title)).performClick()

        waitFor { currentRoute() == NavRoutes.LicensesRoute.name }
    }

    @Test
    fun licensesListLibrariesAndFonts() {
        setNavScreen(route = NavRoutes.LicensesRoute.name) { nav -> LicensesScreen(nav) }

        rule.onNodeWithText(str(Res.string.licenses_heading)).assertIsDisplayed()
        scrollToText(str(Res.string.licenses_libraries), ignoreCase = true)
        scrollToText(str(Res.string.licenses_fonts), ignoreCase = true)
        assertBodyNotEmpty(minTexts = 8)
    }

    @Test
    fun faqAnswersOpenOnTap() {
        setNavScreen(route = NavRoutes.FaqRoute.name) { nav -> FaqScreen(nav) }
        scrollToText(str(Res.string.faq_forgot_pin_q))
        assertFalse(countText(str(Res.string.faq_forgot_pin_a)) > 0 && isShown(str(Res.string.faq_forgot_pin_a)))

        rule.onNodeWithText(str(Res.string.faq_forgot_pin_q)).performClick()

        waitFor { isShown(str(Res.string.faq_forgot_pin_a)) }
        assertBodyNotEmpty(minTexts = 6)
    }

    @Test
    fun supportExplainsTheDonation() {
        setNavScreen(route = NavRoutes.SupportRoute.name) { nav -> SupportScreen(nav) }

        rule.onNodeWithText(str(Res.string.donation_title)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 4)
    }

    @Test
    fun supportOffersKofi() {
        setNavScreen(route = NavRoutes.SupportRoute.name) { nav -> SupportScreen(nav) }

        scrollToText("Ko-fi")
        scrollToText(str(Res.string.support_method_kofi_hint))
        scrollToText(str(Res.string.support_browser_note))
    }

    @Test
    fun damagedDataExplainsAndWipesOnlyAfterAHold() {
        var wiped = false
        setScreen { LossRecoveryScreen(onRecoveryConfirm = { wiped = true }, reason = LossReason.DAMAGED) }

        rule.onNodeWithText(str(Res.string.loss_title)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.vault_reset_restore_hint)).assertIsDisplayed()
        val button = rule.onNodeWithText(str(Res.string.loss_hold_to_wipe))
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

        rule.onNodeWithText(str(Res.string.legacy_vault_title)).assertIsDisplayed()
        assertBodyNotEmpty(minTexts = 3)
    }

    private fun isShown(text: String): Boolean = rule.onAllNodes(androidx.compose.ui.test.hasText(text))
        .fetchSemanticsNodes().any { it.boundsInRoot.height > 0f }

    private companion object {
        const val HOLD_MS = 4_000L
    }
}
