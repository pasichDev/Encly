package com.pasich.encly.core.resources

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** The resources are packaged as Android assets and read back through the Android reader. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidAssetsTest {
    private val saved = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(saved)

    @Test
    fun theDefaultLanguageIsEnglish() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Cancel", runBlocking { getString(Res.string.cancel) })
    }

    @Test
    fun theDefaultLocaleSelectsTheTranslation() {
        Locale.setDefault(Locale.forLanguageTag("uk"))
        assertEquals("Скасувати", runBlocking { getString(Res.string.cancel) })
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Today 10:15", runBlocking { getString(Res.string.date_today_at, "10:15") })
    }

    @Test
    fun pluralsPickTheirCategory() {
        Locale.setDefault(Locale.forLanguageTag("uk"))
        assertEquals("Вибрано 3", runBlocking { getPluralString(Res.plurals.trash_selected, 3, 3) })
    }
}
