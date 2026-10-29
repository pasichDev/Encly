package com.pasich.encly.core.common

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Text kept as a resource id, or resolved for storing, follows the language picked in the app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32]) // Android 12 and lower: the platform alone ignores the in-app language
class UiTextAndStringsTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @After
    fun resetLocales() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun aResourceTextResolvesToItsString() {
        assertEquals("Cancel", UiText.of(R.string.cancel).asString(context))
        assertEquals(UiText.Resource(R.string.cancel), UiText.of(R.string.cancel))
    }

    @Test
    fun withoutAnAppLanguageStoredTextUsesTheSystemOne() {
        assertEquals("Cancel", AndroidAppStrings(context).get(R.string.cancel))
    }

    @Test
    fun storedTextFollowsTheLanguagePickedInTheApp() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("uk"))

        assertEquals("Скасувати", AndroidAppStrings(context).get(R.string.cancel))
    }

    @Test
    fun storedTextTakesFormatArguments() {
        assertEquals("Today 10:15", AndroidAppStrings(context).get(R.string.date_today_at, "10:15"))
    }
}
