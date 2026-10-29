package com.pasich.encly.platform

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.BuildConfig
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** The Android implementations of the platform interfaces `:shared` declares (all but the document ones). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidHostServicesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun resetLanguage() {
        AndroidAppLocaleController().apply(AppLanguage.SYSTEM)
    }

    // --- links ---------------------------------------------------------------------------

    @Test
    fun aUrlIsOpenedInTheDefaultAppOrThroughTheChooser() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val links = AndroidExternalLinks(activity)

        assertTrue(links.openUrl("https://example.org/page"))
        val view = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals("https://example.org/page", view.data.toString())

        assertTrue(links.openUrl("https://example.org/page", chooser = true))
        assertEquals(Intent.ACTION_CHOOSER, shadowOf(activity).nextStartedActivity.action)
    }

    @Test
    fun noAppToOpenTheUrlIsFalseNotACrash() {
        val host = mock(Context::class.java)
        doThrow(ActivityNotFoundException()).`when`(host).startActivity(any(Intent::class.java))

        assertFalse(AndroidExternalLinks(host).openUrl("https://example.org"))
        assertFalse(AndroidExternalLinks(host).writeMail("dev@example.org"))
    }

    @Test
    fun aSystemRefusalIsFalseNotACrash() {
        val host = mock(Context::class.java)
        doThrow(SecurityException("not allowed")).`when`(host).startActivity(any(Intent::class.java))

        assertFalse(AndroidExternalLinks(host).openUrl("https://example.org", chooser = true))
    }

    @Test
    fun anEmailIsStartedAsASendToIntent() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        assertTrue(AndroidExternalLinks(activity).writeMail("dev@example.org"))

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:dev@example.org", intent.data.toString())
    }

    // --- toasts --------------------------------------------------------------------------

    @Test
    fun aToastShowsTheResourceInTheInAppLanguageForTheChosenTime() {
        AndroidToaster(context).show(Res.string.cancel, ToastDuration.LONG)
        waitForToast()

        assertEquals("Cancel", ShadowToast.getTextOfLatestToast())
        assertEquals(Toast.LENGTH_LONG, ShadowToast.getLatestToast().duration)

        ShadowToast.reset()
        AndroidToaster(context).show(Res.string.cancel, ToastDuration.SHORT)
        waitForToast()

        assertEquals(Toast.LENGTH_SHORT, ShadowToast.getLatestToast().duration)
    }

    /** The text is a Compose resource, read off the main thread: let it arrive. */
    private fun waitForToast() {
        val deadline = System.currentTimeMillis() + TOAST_WAIT_MS
        while (ShadowToast.getLatestToast() == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
        assertNotNull("no toast was shown", ShadowToast.getLatestToast())
    }

    // --- clipboard -----------------------------------------------------------------------

    @Test
    fun theClipboardCopiesSensitiveAndClearsOnRequest() {
        val clipboard = AndroidSensitiveClipboard(context)
        val manager = context.getSystemService(ClipboardManager::class.java)

        assertTrue(clipboard.copy("label", "secret"))

        val clip = checkNotNull(manager.primaryClip)
        assertEquals("secret", clip.getItemAt(0).text.toString())
        assertTrue(clip.description.extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))

        clipboard.clear()
        assertNull(manager.primaryClip)
    }

    @Test
    @Config(sdk = [35])
    fun newerAndroidConfirmsACopyItself() {
        assertTrue(AndroidSensitiveClipboard(context).systemConfirmsCopy)
    }

    @Test
    @Config(sdk = [32])
    fun olderAndroidShowsNoConfirmationOfItsOwn() {
        assertFalse(AndroidSensitiveClipboard(context).systemConfirmsCopy)
    }

    // --- the rest of the services ------------------------------------------------------------

    @Test
    @Config(sdk = [32])
    fun theInAppLanguageIsSetAndReadBackThroughAppCompat() {
        val locales = AndroidAppLocaleController()

        locales.apply(AppLanguage.GERMAN)
        assertEquals(AppLanguage.GERMAN, locales.current())

        locales.apply(AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, locales.current())
    }

    @Test
    fun theBuildFactsComeFromTheBuild() {
        val info = AndroidAppInfo(context)

        assertTrue(info.appName.startsWith("Encly"))
        assertEquals(BuildConfig.VERSION_NAME, info.versionName)
        assertEquals(BuildConfig.DONATIONS_ENABLED, info.donationsEnabled)
        assertEquals(BuildConfig.STORE_RATING_ENABLED, info.storeRatingEnabled)
        assertEquals("market://details?id=${context.packageName}", info.storePageLinks.first())
        assertTrue(info.storePageLinks.last().startsWith("https://play.google.com/"))
    }

    @Test
    fun theCacheDirectoryIsTheAppsCache() {
        assertEquals(context.cacheDir.path, AndroidCacheDirectory(context).path.toString())
    }

    @Test
    @Config(sdk = [31])
    fun dynamicColoursNeedAndroid12() {
        assertTrue(AndroidDeviceCapabilities().supportsDynamicColors())
    }

    @Test
    @Config(sdk = [30])
    fun noDynamicColoursBeforeAndroid12() {
        assertFalse(AndroidDeviceCapabilities().supportsDynamicColors())
    }

    // --- the window ----------------------------------------------------------------------

    @Test
    fun theWindowIsMadeSecureLightBarredAndPaintedOnRequest() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = AndroidAppWindow(activity, activity.window.decorView)

        window.enforceSecure()
        window.setLightStatusBar(light = true)
        window.setBackgroundColor(0xFF112233.toInt())
        window.hideKeyboard()
        window.showKeyboard()

        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertTrue(controller.isAppearanceLightStatusBars)
        assertEquals(0xFF112233.toInt(), (activity.window.decorView.background as ColorDrawable).color)
        assertFalse(window.isChangingConfigurations)
    }

    private companion object {
        const val TOAST_WAIT_MS = 5_000L
        const val POLL_MS = 20L
    }
}
