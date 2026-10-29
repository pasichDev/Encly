package com.pasich.encly.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.locale.AppLocales
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.appearance_mode_note_dark
import com.pasich.encly.core.resources.appearance_mode_note_midnight
import com.pasich.encly.core.resources.appearance_text_desc
import com.pasich.encly.core.resources.appearance_text_section
import com.pasich.encly.core.resources.backup_title
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.done
import com.pasich.encly.core.resources.font_size_title
import com.pasich.encly.core.resources.font_size_value
import com.pasich.encly.core.resources.font_style_tech
import com.pasich.encly.core.resources.language_dialog_title
import com.pasich.encly.core.resources.language_name_uk
import com.pasich.encly.core.resources.note_line_spacing_title
import com.pasich.encly.core.resources.palette_forest
import com.pasich.encly.core.resources.palette_graphite
import com.pasich.encly.core.resources.palette_midnight
import com.pasich.encly.core.resources.palette_ocean
import com.pasich.encly.core.resources.palette_paper
import com.pasich.encly.core.resources.security_title
import com.pasich.encly.core.resources.settings_appearance
import com.pasich.encly.core.resources.settings_backup
import com.pasich.encly.core.resources.settings_general
import com.pasich.encly.core.resources.settings_language
import com.pasich.encly.core.resources.settings_notes_and_tasks
import com.pasich.encly.core.resources.settings_privacy
import com.pasich.encly.core.resources.settings_show_tasks
import com.pasich.encly.core.resources.settings_swipe_enabled
import com.pasich.encly.core.resources.settings_swipe_left
import com.pasich.encly.core.resources.settings_swipe_right
import com.pasich.encly.core.resources.settings_swipe_section
import com.pasich.encly.core.resources.swipe_choice_color
import com.pasich.encly.core.resources.swipe_choice_pin
import com.pasich.encly.core.resources.theme_type_dark
import com.pasich.encly.core.resources.theme_type_light
import com.pasich.encly.core.resources.theme_type_system
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.SwipeAction
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
    private val app by lazy { TestApp() }
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
            Res.string.settings_general,
            Res.string.settings_notes_and_tasks,
            Res.string.settings_privacy,
            Res.string.settings_backup,
        ).forEach { scrollToText(str(it), ignoreCase = true) }
        scrollToText(str(Res.string.settings_appearance))
        scrollToText(str(Res.string.settings_language))
        assertBodyNotEmpty(minTexts = 6)
    }

    @Test
    fun theDecoyVaultListsTheSameSectionsAsTheRealOne() {
        app.inDecoySession()
        showSettings()

        listOf(
            Res.string.settings_general,
            Res.string.settings_notes_and_tasks,
            Res.string.settings_privacy,
            Res.string.settings_backup,
        ).forEach { scrollToText(str(it), ignoreCase = true) }
        scrollToText(str(Res.string.backup_title))
        rule.onNodeWithText(str(Res.string.backup_title)).performClick()

        waitFor { currentRoute() == NavRoutes.BackupRoute.name }
    }

    @Test
    fun theAppearanceRowOpensAppearance() {
        showSettings()

        rule.onNodeWithText(str(Res.string.settings_appearance)).performClick()

        waitFor { currentRoute() == NavRoutes.AppearanceRoute.name }
    }

    @Test
    fun theSecurityRowOpensSecurity() {
        showSettings()
        scrollToText(str(Res.string.security_title))

        rule.onNodeWithText(str(Res.string.security_title)).performClick()

        waitFor { currentRoute() == NavRoutes.SecuritySettingsRoute.name }
    }

    @Test
    fun theShowTasksSwitchIsSaved() {
        showSettings()
        scrollToText(str(Res.string.settings_show_tasks))

        rule.onNodeWithText(str(Res.string.settings_show_tasks)).performClick()

        waitFor { !app.settings.showTasks.value }
    }

    @Test
    fun theSwipeSectionListsTheMasterSwitchAndBothDirections() {
        showSettings()

        scrollToText(str(Res.string.settings_swipe_section), ignoreCase = true)
        scrollToText(str(Res.string.settings_swipe_enabled))
        scrollToText(str(Res.string.settings_swipe_right))
        scrollToText(str(Res.string.settings_swipe_left))
        // The defaults: pin on the right, archive on the left.
        scrollToText(str(Res.string.swipe_choice_pin))
    }

    @Test
    fun theSwipeSwitchIsSavedAndHidesTheDirectionRows() {
        showSettings()
        scrollToText(str(Res.string.settings_swipe_enabled))

        rule.onNodeWithText(str(Res.string.settings_swipe_enabled)).performClick()

        waitFor { !app.settings.swipe.value.enabled }
        waitFor { countText(str(Res.string.settings_swipe_right)) == 0 }
    }

    @Test
    fun theActionOfADirectionIsChosenInADialogAndSaved() {
        showSettings()
        scrollToText(str(Res.string.settings_swipe_left))

        rule.onNodeWithText(str(Res.string.settings_swipe_left)).performClick()
        waitForText(str(Res.string.swipe_choice_color))
        rule.onNodeWithText(str(Res.string.swipe_choice_color)).performClick()
        rule.onNodeWithText(str(Res.string.done)).performClick()

        waitFor { app.settings.swipe.value.left == SwipeAction.NEXT_COLOR }
        assertEquals(SwipeAction.PIN, app.settings.swipe.value.right)
    }

    // Android 12L: AppCompat keeps the per-app language itself (13+ hands it to LocaleManager,
    // which Robolectric does not persist).
    @Config(sdk = [32])
    @Test
    fun theLanguageIsSwitchedInTheApp() {
        showSettings()
        scrollToText(str(Res.string.settings_language))

        rule.onNodeWithText(str(Res.string.settings_language)).performClick()
        waitForText(str(Res.string.language_dialog_title))
        rule.onNodeWithText(str(Res.string.language_name_uk)).performClick()
        rule.onNodeWithText(str(Res.string.done)).performClick()

        waitFor { AppLocales.current() == AppLanguage.UKRAINIAN }
        assertEquals(0, countText(str(Res.string.language_dialog_title)))
    }

    @Test
    fun cancellingTheLanguageChoiceKeepsTheLanguage() {
        showSettings()
        scrollToText(str(Res.string.settings_language))
        rule.onNodeWithText(str(Res.string.settings_language)).performClick()
        waitForText(str(Res.string.language_dialog_title))

        rule.onNodeWithText(str(Res.string.language_name_uk)).performClick()
        rule.onNodeWithText(str(Res.string.cancel)).performClick()

        waitFor { countText(str(Res.string.language_dialog_title)) == 0 }
        assertEquals(AppLanguage.SYSTEM, AppLocales.current())
    }

    @Test
    fun appearanceOffersTheDefaultTextSizeAndLineSpacingOfNotes() {
        app.settings.fontSize.value = 22
        showAppearance()

        scrollToText(str(Res.string.appearance_text_section), ignoreCase = true)
        scrollToText(str(Res.string.font_size_title))
        scrollToText(str(Res.string.note_line_spacing_title))
        scrollToText(str(Res.string.font_size_value, 22))
        scrollToText(str(Res.string.appearance_text_desc))
    }

    @Test
    fun appearanceShowsEveryThemeModeAndFont() {
        showAppearance()

        listOf(
            Res.string.palette_paper,
            Res.string.palette_forest,
            Res.string.palette_ocean,
            Res.string.palette_graphite,
            Res.string.palette_midnight,
            Res.string.theme_type_system,
            Res.string.theme_type_light,
            Res.string.theme_type_dark,
        ).forEach { rule.onNodeWithText(str(it)).assertExists() }
        scrollToText(str(Res.string.font_style_tech))
        assertBodyNotEmpty(minTexts = 10)
    }

    @Test
    fun choosingAThemeRecolorsTheApp() {
        showAppearance()
        assertEquals(colorSchemeFor(ThemePalette.PAPER, false).surface, surface)

        rule.onNodeWithText(str(Res.string.palette_ocean)).performClick()

        waitFor { app.settings.theme.value.palette == ThemePalette.OCEAN }
        assertEquals(colorSchemeFor(ThemePalette.OCEAN, false).surface, surface)
    }

    @Test
    fun darkModeRecolorsTheApp() {
        showAppearance()

        rule.onNodeWithText(str(Res.string.theme_type_dark)).performClick()

        waitFor { app.settings.theme.value.type == ThemeType.DARK }
        assertEquals(colorSchemeFor(ThemePalette.PAPER, true).surface, surface)
        rule.onNodeWithText(str(Res.string.appearance_mode_note_dark)).assertIsDisplayed()
    }

    @Test
    fun midnightIsAlwaysDarkAndLocksTheMode() {
        showAppearance()

        rule.onNodeWithText(str(Res.string.palette_midnight)).performClick()

        waitFor { app.settings.theme.value.palette == ThemePalette.MIDNIGHT }
        assertEquals(colorSchemeFor(ThemePalette.MIDNIGHT, true).surface, surface)
        rule.onNodeWithText(str(Res.string.appearance_mode_note_midnight)).assertIsDisplayed()
        rule.onNodeWithText(str(Res.string.theme_type_light)).assertIsNotEnabled()
    }

    @Test
    fun theFontChoiceIsSaved() {
        showAppearance()
        scrollToText(str(Res.string.font_style_tech))

        rule.onNodeWithText(str(Res.string.font_style_tech)).performClick()

        waitFor { app.settings.theme.value.fontStyle == FontStyleType.TECH_MINIMAL }
        assertFalse(
            rule.onNodeWithText(
                str(Res.string.font_style_tech),
            ).fetchSemanticsNode().config.contains(SemanticsProperties.Disabled),
        )
    }
}
