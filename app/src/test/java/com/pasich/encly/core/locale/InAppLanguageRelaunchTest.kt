package com.pasich.encly.core.locale

import android.Manifest
import android.app.Application
import android.os.LocaleList
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.MainActivity
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.testutil.TestLocale
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Pick a language, kill the app, open it again: the language stored by AppCompat is applied to a
 * new process, whose default locale is the device's. The strings read that default locale, so the
 * launch itself has to put the stored language there before anything is composed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35])
class InAppLanguageRelaunchTest {
    @Before
    fun grantWhatTheManifestDeclares() {
        // Granted at install on a device; Robolectric grants nothing by itself.
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.HIDE_OVERLAY_WINDOWS)
    }

    @After
    fun resetLanguage() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        TestLocale.set(Locale.US.toLanguageTag())
    }

    @Test
    fun aRelaunchedActivityShowsTheStoredLanguage() {
        // The language is picked in a running activity (AppCompat only stores it then), which
        // recreates itself, as it does in the app.
        val picking = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("uk"))
            shadowOf(Looper.getMainLooper()).idle()

            // A new process comes up in the device's language and opens the activity again.
            TestLocale.set("en-US")
            assertEquals("Cancel", runBlocking { getString(Res.string.cancel) })
            val relaunched = Robolectric.buildActivity(MainActivity::class.java).create()
            try {
                assertEquals("uk", Locale.getDefault().language)
                assertEquals("uk", LocaleList.getDefault()[0].language)
                assertEquals("Скасувати", runBlocking { getString(Res.string.cancel) })
            } finally {
                relaunched.destroy()
            }
        } finally {
            picking.destroy()
        }
    }

    @Test
    fun withoutAStoredLanguageTheDeviceOneStays() {
        TestLocale.set("en-US")

        val activity = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            assertEquals("Cancel", runBlocking { getString(Res.string.cancel) })
        } finally {
            activity.destroy()
        }
    }
}
