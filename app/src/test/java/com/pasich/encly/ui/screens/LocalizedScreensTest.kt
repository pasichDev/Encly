package com.pasich.encly.ui.screens

import android.content.res.Configuration
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasich.encly.R
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
    private val app by lazy { TestApp(context) }

    /** English texts that [language] translates differently: none of them may be shown. */
    private val untranslated: Set<String> by lazy {
        val english = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.ENGLISH)
            },
        ).resources
        R.string::class.java.fields.mapNotNull { field ->
            val id = field.getInt(null)
            val en = english.getString(id)
            en.takeIf { language != "en" && it.length > 3 && it != context.getString(id) }
        }.flatMap { listOf(it, it.uppercase()) }.toSet()
    }

    @Before
    fun useLanguage() {
        RuntimeEnvironment.setQualifiers("+$language")
        assertEquals(language, context.resources.configuration.locales[0].language)
        if (language != "en") assertTrue("nothing is translated into $language", untranslated.size > MIN_TRANSLATED)
    }

    private fun assertInLanguage(expected: Int) {
        waitForText(str(expected), ignoreCase = true)
        val leaks = visibleTexts().map(::textOf).filter { it in untranslated }
        assertTrue("[$language] English text on screen: $leaks", leaks.isEmpty())
        assertBodyNotEmpty()
    }

    @Test
    fun welcome() {
        setScreen(viewModels(app.onboarding(), app.authSetup())) { OnboardingScreen(onComplete = {}) }
        assertInLanguage(R.string.onboarding_welcome_headline)
    }

    @Test
    fun notes() {
        setNavScreen(viewModels(*app.home()), route = NavRoutes.HomeRoute.name) { nav -> MainRootScreen(nav) }
        assertInLanguage(R.string.empty_notes)
    }

    @Test
    fun tasks() {
        setNavScreen(viewModels(app.tasksVm()), route = TestRoutes.TASKS) { nav -> TasksScreen(nav) }
        assertInLanguage(R.string.task_empty_title)
    }

    @Test
    fun editor() {
        val editor = app.editor()
        setNavScreen(
            viewModels(editor, app.tagList()),
            route = TestRoutes.EDIT_NOTE,
            start = NavRoutes.HomeRoute.name,
        ) { nav ->
            EditNoteScreen(nav)
        }
        navigateTo("${NavRoutes.EditNoteRoute.name}/-1")
        waitFor {
            rule.onAllNodes(
                androidx.compose.ui.test.hasContentDescription(str(R.string.block_add)),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        val leaks = visibleTexts().map(::textOf).filter { it in untranslated }
        assertTrue("[$language] English text on screen: $leaks", leaks.isEmpty())
    }

    @Test
    fun settings() {
        setNavScreen(viewModels(app.settingsVm()), route = NavRoutes.SettingsRoute.name) { nav -> SettingsScreen(nav) }
        assertInLanguage(R.string.settings_appearance)
    }

    @Test
    fun appearance() {
        setNavScreen(viewModels(app.appearance()), route = NavRoutes.AppearanceRoute.name) { nav ->
            AppearanceScreen(nav)
        }
        assertInLanguage(R.string.palette_midnight)
    }

    @Test
    fun security() {
        setNavScreen(
            viewModels(app.securitySettings(), app.backup()),
            route = NavRoutes.SecuritySettingsRoute.name,
        ) { nav ->
            SecuritySettingsScreen(nav)
        }
        assertInLanguage(R.string.security_encryption_active)
    }

    @Test
    fun backup() {
        setNavScreen(viewModels(app.backup()), route = NavRoutes.BackupRoute.name) { nav -> BackupScreen(nav) }
        assertInLanguage(R.string.backup_intro_short)
    }

    @Test
    fun lockAndRecovery() {
        `when`(app.security.hasRecoverySeed()).thenReturn(true)
        setNavScreen(viewModels(app.lock()), route = NavRoutes.LockRoute.name) { nav -> LockScreen(nav) }
        assertInLanguage(R.string.lock_title)

        rule.onNodeWithText(str(R.string.lock_use_recovery_phrase)).performClick()

        assertInLanguage(R.string.lock_recover_access)
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
