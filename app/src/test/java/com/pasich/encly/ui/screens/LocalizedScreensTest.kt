package com.pasich.encly.ui.screens

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.allStringResources
import com.pasich.encly.core.resources.backup_intro_short
import com.pasich.encly.core.resources.block_more_tools
import com.pasich.encly.core.resources.empty_notes
import com.pasich.encly.core.resources.lock_recover_access
import com.pasich.encly.core.resources.lock_title
import com.pasich.encly.core.resources.lock_use_recovery_phrase
import com.pasich.encly.core.resources.onboarding_welcome_headline
import com.pasich.encly.core.resources.palette_midnight
import com.pasich.encly.core.resources.security_encryption_active
import com.pasich.encly.core.resources.settings_appearance
import com.pasich.encly.core.resources.task_empty_title
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.LockScreen
import com.pasich.encly.presentation.screen.MainRootScreen
import com.pasich.encly.presentation.screen.TasksScreen
import com.pasich.encly.presentation.screen.backup.BackupScreen
import com.pasich.encly.presentation.screen.editnote.EditNoteScreen
import com.pasich.encly.presentation.screen.onboarding.OnboardingScreen
import com.pasich.encly.presentation.screen.settings.AppearanceScreen
import com.pasich.encly.presentation.screen.settings.SecuritySettingsScreen
import com.pasich.encly.presentation.screen.settings.SettingsScreen
import com.pasich.encly.testutil.TestLocale
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

/**
 * Every main screen in each of the nine languages: it renders, shows its own text, and shows no
 * English string that this language translates (a hardcoded or untranslated string).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class LocalizedScreensTest(private val language: String) : ComposeScreenTest() {
    private val app by lazy { TestApp() }

    /** English texts that [language] translates differently: none of them may be shown. */
    private val untranslated: Set<String> by lazy {
        val english = TestLocale.within("en") { everyString() }
        val translated = everyString()
        english.mapNotNull { (key, en) ->
            en.takeIf { language != "en" && it.length > 3 && it != translated[key] }
        }.flatMap { listOf(it, it.uppercase()) }.toSet()
    }

    /** Every string resource in the current locale, by key. */
    private fun everyString(): Map<String, String> =
        runBlocking { Res.allStringResources.mapValues { getString(it.value) } }

    @After
    fun restoreLanguage() = TestLocale.set(Locale.US.toLanguageTag())

    @Before
    fun useLanguage() {
        RuntimeEnvironment.setQualifiers("+$language")
        TestLocale.set(language)
        assertEquals(language, context.resources.configuration.locales[0].language)
        if (language != "en") assertTrue("nothing is translated into $language", untranslated.size > MIN_TRANSLATED)
    }

    private fun assertInLanguage(expected: StringResource) {
        waitForText(str(expected), ignoreCase = true)
        val leaks = visibleTexts().map(::textOf).filter { it in untranslated }
        assertTrue("[$language] English text on screen: $leaks", leaks.isEmpty())
        assertBodyNotEmpty()
    }

    @Test
    fun welcome() {
        setScreen(viewModels(app.onboarding(), app.authSetup())) { OnboardingScreen(onComplete = {}) }
        assertInLanguage(Res.string.onboarding_welcome_headline)
    }

    @Test
    fun notes() {
        setNavScreen(viewModels(*app.home()), route = NavRoutes.HomeRoute.name) { nav -> MainRootScreen(nav) }
        assertInLanguage(Res.string.empty_notes)
    }

    @Test
    fun tasks() {
        setNavScreen(viewModels(app.tasksVm()), route = TestRoutes.TASKS) { nav -> TasksScreen(nav) }
        assertInLanguage(Res.string.task_empty_title)
    }

    @Test
    fun editor() {
        val editor = app.editor()
        setNavScreen(
            viewModels(editor, app.tagList(), app.markdownExport()),
            route = TestRoutes.EDIT_NOTE,
            start = NavRoutes.HomeRoute.name,
        ) { nav ->
            EditNoteScreen(nav)
        }
        navigateTo("${NavRoutes.EditNoteRoute.name}/-1")
        waitFor {
            rule.onAllNodes(
                androidx.compose.ui.test.hasContentDescription(str(Res.string.block_more_tools)),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        val leaks = visibleTexts().map(::textOf).filter { it in untranslated }
        assertTrue("[$language] English text on screen: $leaks", leaks.isEmpty())
    }

    @Test
    fun settings() {
        setNavScreen(viewModels(app.settingsVm()), route = NavRoutes.SettingsRoute.name) { nav -> SettingsScreen(nav) }
        assertInLanguage(Res.string.settings_appearance)
    }

    @Test
    fun appearance() {
        setNavScreen(viewModels(app.appearance()), route = NavRoutes.AppearanceRoute.name) { nav ->
            AppearanceScreen(nav)
        }
        assertInLanguage(Res.string.palette_midnight)
    }

    @Test
    fun security() {
        setNavScreen(
            viewModels(app.securitySettings(), app.backup()),
            route = NavRoutes.SecuritySettingsRoute.name,
        ) { nav ->
            SecuritySettingsScreen(nav)
        }
        assertInLanguage(Res.string.security_encryption_active)
    }

    @Test
    fun backup() {
        setNavScreen(viewModels(app.backup(), app.markdownExport()), route = NavRoutes.BackupRoute.name) { nav ->
            BackupScreen(nav)
        }
        assertInLanguage(Res.string.backup_intro_short)
    }

    @Test
    fun lockAndRecovery() {
        `when`(app.security.hasRecoverySeed()).thenReturn(true)
        setNavScreen(viewModels(app.lock()), route = NavRoutes.LockRoute.name) { nav -> LockScreen(nav) }
        assertInLanguage(Res.string.lock_title)

        rule.onNodeWithText(str(Res.string.lock_use_recovery_phrase)).performClick()

        assertInLanguage(Res.string.lock_recover_access)
        (1..PHRASE_WORDS).forEach { phraseCell(it).assertExists() }
    }

    companion object {
        /** Far fewer translated strings than this means the language did not load. */
        private const val MIN_TRANSLATED = 300

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun languages(): List<Array<Any>> =
            listOf("en", "uk", "de", "es", "fr", "it", "nl", "pl", "pt").map { arrayOf(it) }
    }
}
