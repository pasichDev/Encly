package com.pasich.encly.core.common

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.locale.AppLocales
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.date_today_at
import com.pasich.encly.testutil.TestLocale
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The in-app language reaches every way a string is read: the composition, stored text
 * ([AppStrings]) and text a ViewModel hands to the UI ([UiText]). The Compose resources read the
 * process's default locale, which [AppLocales.apply] sets, so the same must hold on Android 12 and
 * lower (where the platform alone ignores the in-app language) and on Android 13 and up.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [32, 35])
class UiTextAndStringsTest(private val language: AppLanguage, private val expectedCancel: String) {
    @get:Rule
    val rule = createComposeRule()

    @After
    fun resetLanguage() {
        AppLocales.apply(AppLanguage.SYSTEM)
        TestLocale.set(Locale.US.toLanguageTag())
    }

    @Test
    fun theLanguagePickedInTheAppIsTheOneStoredTextUses() {
        AppLocales.apply(language)

        assertEquals(expectedCancel, runBlocking { ComposeAppStrings().get(Res.string.cancel) })
    }

    @Test
    fun theLanguagePickedInTheAppIsTheOneUiTextResolvesIn() {
        AppLocales.apply(language)

        assertEquals(expectedCancel, runBlocking { UiText.of(Res.string.cancel).resolve() })
    }

    @Test
    fun theLanguagePickedInTheAppIsTheOneTheCompositionShows() {
        AppLocales.apply(language)
        rule.setContent { Text(stringResource(Res.string.cancel)) }

        rule.onNodeWithText(expectedCancel).assertExists()
    }

    @Test
    fun pickingTheSystemLanguageGoesBackToTheDevicesOne() {
        AppLocales.apply(language)
        AppLocales.apply(AppLanguage.SYSTEM)

        assertEquals("Cancel", runBlocking { ComposeAppStrings().get(Res.string.cancel) })
        assertEquals(AppLanguage.SYSTEM, AppLocales.current())
    }

    @Test
    fun storedTextTakesFormatArguments() {
        AppLocales.apply(AppLanguage.ENGLISH)

        assertEquals("Today 10:15", runBlocking { ComposeAppStrings().get(Res.string.date_today_at, "10:15") })
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun languages(): List<Array<Any>> = listOf(
            arrayOf(AppLanguage.ENGLISH, "Cancel"),
            arrayOf(AppLanguage.UKRAINIAN, "Скасувати"),
            arrayOf(AppLanguage.GERMAN, "Abbrechen"),
            arrayOf(AppLanguage.FRENCH, "Annuler"),
            arrayOf(AppLanguage.SPANISH, "Cancelar"),
            arrayOf(AppLanguage.ITALIAN, "Annulla"),
            arrayOf(AppLanguage.POLISH, "Anuluj"),
            arrayOf(AppLanguage.PORTUGUESE, "Cancelar"),
            arrayOf(AppLanguage.DUTCH, "Annuleren"),
        )
    }
}
