package com.pasich.encly.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasich.encly.R
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.locale.AppLocales
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.settings.AppearanceScreen
import com.pasich.encly.presentation.screen.settings.SettingsScreen
import com.pasich.encly.ui.theme.colorSchemeFor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.robolectric.annotation.Config

class SettingsScreensTest : ComposeScreenTest() {
    private val app by lazy { TestApp(context) }
    private var surface: Color = Color.Unspecified

    @After
    fun resetLanguage() {
        AppLocales.apply(AppLanguage.SYSTEM)
    }

    private fun showSettings() {
        setNavScreen(viewModels(app.settingsVm()), route = NavRoutes.SettingsRoute.name) { nav -> SettingsScreen(nav) }
    }

    private fun showAppearance() {
        setNavScreen(
            viewModels(app.appearance()),
            route = NavRoutes.AppearanceRoute.name,
            liveTheme = app.settings.theme,
        ) { nav ->
            val shown = MaterialTheme.colorScheme.surface
            SideEffect { surface = shown }
            AppearanceScreen(nav)
        }
    }

    @Test
    fun settingsListsEverySection() {
        showSettings()

        listOf(
            R.string.settings_general,
            R.string.settings_notes_and_tasks,
            R.string.settings_privacy,
            R.string.settings_backup,
        ).forEach { scrollToText(str(it), ignoreCase = true) }
        scrollToText(str(R.string.settings_appearance))
        scrollToText(str(R.string.settings_language))
        assertBodyNotEmpty(minTexts = 6)
    }

    @Test
    fun theAppearanceRowOpensAppearance() {
        showSettings()

        rule.onNodeWithText(str(R.string.settings_appearance)).performClick()

        waitFor { currentRoute() == NavRoutes.AppearanceRoute.name }
    }

    @Test
    fun theSecurityRowOpensSecurity() {
        showSettings()
        scrollToText(str(R.string.security_title))

        rule.onNodeWithText(str(R.string.security_title)).performClick()

        waitFor { currentRoute() == NavRoutes.SecuritySettingsRoute.name }
    }

    @Test
    fun theShowTasksSwitchIsSaved() {
        showSettings()
        scrollToText(str(R.string.settings_show_tasks))

        rule.onNodeWithText(str(R.string.settings_show_tasks)).performClick()

        waitFor { !app.settings.showTasks.value }
    }

    // Android 12L: AppCompat keeps the per-app language itself (13+ hands it to LocaleManager,
    // which Robolectric does not persist).
    @Config(sdk = [32])
    @Test
    fun theLanguageIsSwitchedInTheApp() {
        showSettings()
        scrollToText(str(R.string.settings_language))

        rule.onNodeWithText(str(R.string.settings_language)).performClick()
        waitForText(str(R.string.language_dialog_title))
        rule.onNodeWithText(str(R.string.language_name_uk)).performClick()
        rule.onNodeWithText(str(R.string.done)).performClick()

        waitFor { AppLocales.current() == AppLanguage.UKRAINIAN }
        assertEquals(0, countText(str(R.string.language_dialog_title)))
    }

    @Test
    fun cancellingTheLanguageChoiceKeepsTheLanguage() {
        showSettings()
        scrollToText(str(R.string.settings_language))
        rule.onNodeWithText(str(R.string.settings_language)).performClick()
        waitForText(str(R.string.language_dialog_title))

        rule.onNodeWithText(str(R.string.language_name_uk)).performClick()
        rule.onNodeWithText(str(R.string.cancel)).performClick()

        waitFor { countText(str(R.string.language_dialog_title)) == 0 }
        assertEquals(AppLanguage.SYSTEM, AppLocales.current())
    }

    @Test
    fun appearanceShowsEveryThemeModeAndFont() {
        showAppearance()

        listOf(
            R.string.palette_paper,
            R.string.palette_forest,
            R.string.palette_ocean,
            R.string.palette_graphite,
            R.string.palette_midnight,
            R.string.theme_type_system,
            R.string.theme_type_light,
            R.string.theme_type_dark,
        ).forEach { rule.onNodeWithText(str(it)).assertExists() }
        scrollToText(str(R.string.font_style_tech))
        assertBodyNotEmpty(minTexts = 10)
    }

    @Test
    fun choosingAThemeRecolorsTheApp() {
        showAppearance()
        assertEquals(colorSchemeFor(ThemePalette.PAPER, false).surface, surface)

        rule.onNodeWithText(str(R.string.palette_ocean)).performClick()

        waitFor { app.settings.theme.value.palette == ThemePalette.OCEAN }
        assertEquals(colorSchemeFor(ThemePalette.OCEAN, false).surface, surface)
    }

    @Test
    fun darkModeRecolorsTheApp() {
        showAppearance()

        rule.onNodeWithText(str(R.string.theme_type_dark)).performClick()

        waitFor { app.settings.theme.value.type == ThemeType.DARK }
        assertEquals(colorSchemeFor(ThemePalette.PAPER, true).surface, surface)
        rule.onNodeWithText(str(R.string.appearance_mode_note_dark)).assertIsDisplayed()
    }

    @Test
    fun midnightIsAlwaysDarkAndLocksTheMode() {
        showAppearance()

        rule.onNodeWithText(str(R.string.palette_midnight)).performClick()

        waitFor { app.settings.theme.value.palette == ThemePalette.MIDNIGHT }
        assertEquals(colorSchemeFor(ThemePalette.MIDNIGHT, true).surface, surface)
        rule.onNodeWithText(str(R.string.appearance_mode_note_midnight)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.theme_type_light)).assertIsNotEnabled()
    }

    @Test
    fun theFontChoiceIsSaved() {
        showAppearance()
        scrollToText(str(R.string.font_style_tech))

        rule.onNodeWithText(str(R.string.font_style_tech)).performClick()

        waitFor { app.settings.theme.value.fontStyle == FontStyleType.TECH_MINIMAL }
        assertFalse(
            rule.onNodeWithText(
                str(R.string.font_style_tech),
            ).fetchSemanticsNode().config.contains(SemanticsProperties.Disabled),
        )
    }
}
