package com.pasich.encly.platform

import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.R
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.security.ActivityBiometricHost
import com.pasich.encly.core.security.ScreenshotAllowance
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [AndroidHostServices.Provide] gives the shared UI, under one Activity, every service it asks for through a
 * `Local...` value: the Activity's window, links and biometric host, and the app-wide toasts, clipboard, language
 * controller, build facts, document dialogs and ViewModel factory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidHostProvideTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<ProvideHostActivity>? = null
    private val factory = mock(MetroViewModelFactory::class.java)
    private val allowance = ScreenshotAllowance()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.packageManager).addOrUpdateActivity(
            ActivityInfo().apply {
                name = ProvideHostActivity::class.java.name
                packageName = context.packageName
            },
        )
        ProvideHostActivity.captured = null
        ProvideHostActivity.services = AndroidHostServices(
            toaster = FakeToaster,
            clipboard = FakeClipboard,
            locales = FakeLocales,
            info = AppInfo.Default,
            viewModelFactory = factory,
            screenshots = allowance,
        )
    }

    @After
    fun tearDown() {
        scenario?.close()
        ProvideHostActivity.services = null
    }

    @Test
    fun everyLocalIsTheServiceOfThisActivityOrTheApp() {
        val launched = ActivityScenario.launch(ProvideHostActivity::class.java).also { scenario = it }
        rule.waitForIdle()
        var activity: ProvideHostActivity? = null
        launched.onActivity { activity = it }

        val seen = checkNotNull(ProvideHostActivity.captured)
        assertTrue(seen.window is AndroidAppWindow)
        assertTrue(seen.links is AndroidExternalLinks)
        assertSame(FakeToaster, seen.toaster)
        assertSame(FakeClipboard, seen.clipboard)
        assertSame(FakeLocales, seen.locales)
        assertSame(AppInfo.Default, seen.info)
        assertSame(AndroidDocumentPickers, seen.pickers)
        assertSame(factory, seen.factory)
        val host = checkNotNull(activity)
        assertEquals(ScreenBiometricHost(ActivityBiometricHost(host), host), seen.biometric)
        assertSame(allowance, seen.screenshots)
    }

    @Test
    fun theWindowIsCapturableOnlyWhileScreenshotsAreAllowedAndNoSecretIsShowing() {
        val launched = ActivityScenario.launch(ProvideHostActivity::class.java).also { scenario = it }
        rule.waitForIdle()
        var activity: ProvideHostActivity? = null
        launched.onActivity { activity = it }
        val window = checkNotNull(activity).window
        fun secure() = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

        // Protected from the first frame, and while nothing is allowed.
        assertTrue(secure())

        rule.runOnIdle { allowance.allow() }
        assertFalse(secure())

        // A PIN or recovery-words screen protects the window again, and gives it back when it goes.
        val release = rule.runOnIdle { allowance.protect() }
        assertTrue(secure())
        rule.runOnIdle { release() }
        assertFalse(secure())

        // The theme re-asserts the protection on every frame; it must not undo the allowance.
        rule.runOnIdle { checkNotNull(ProvideHostActivity.captured).window.enforceSecure() }
        assertFalse(secure())

        // Every lock ends it.
        rule.runOnIdle { allowance.revoke() }
        assertTrue(secure())
    }

    @Test
    fun anAllowedWindowIsProtectedAgainOnceTheActivityLeavesTheForeground() {
        val launched = ActivityScenario.launch(ProvideHostActivity::class.java).also { scenario = it }
        rule.waitForIdle()
        var activity: ProvideHostActivity? = null
        launched.onActivity { activity = it }
        val window = checkNotNull(activity).window
        fun secure() = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        rule.runOnIdle { allowance.allow() }
        assertFalse(secure())

        launched.moveToState(Lifecycle.State.STARTED)
        assertTrue(secure())

        launched.moveToState(Lifecycle.State.RESUMED)
        assertFalse(secure())
    }
}

/** What the content under [AndroidHostServices.Provide] read from the composition. */
class SeenServices(
    val window: AppWindow,
    val links: ExternalLinks,
    val toaster: Toaster,
    val clipboard: SensitiveClipboard,
    val locales: AppLocaleController,
    val info: AppInfo,
    val pickers: DocumentPickers,
    val biometric: ScreenBiometricHost?,
    val factory: MetroViewModelFactory?,
    val screenshots: ScreenshotAllowance?,
)

class ProvideHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Encly)
        super.onCreate(savedInstanceState)
        val host = checkNotNull(services)
        setContent {
            host.Provide(this, strictKeyboard = false) { Probe() }
        }
    }

    @Composable
    private fun Probe() {
        captured = SeenServices(
            window = LocalAppWindow.current,
            links = LocalExternalLinks.current,
            toaster = LocalToaster.current,
            clipboard = LocalSensitiveClipboard.current,
            locales = LocalAppLocales.current,
            info = LocalAppInfo.current,
            pickers = LocalDocumentPickers.current,
            biometric = LocalBiometricHost.current,
            factory = LocalMetroViewModelFactory.current,
            screenshots = LocalScreenshotAllowance.current,
        )
    }

    companion object {
        var services: AndroidHostServices? = null
        var captured: SeenServices? = null
    }
}

private object FakeToaster : Toaster {
    override fun show(text: org.jetbrains.compose.resources.StringResource, duration: ToastDuration, vararg args: Any) =
        Unit
}

private object FakeClipboard : SensitiveClipboard {
    override val systemConfirmsCopy = true

    override fun copy(label: String, text: String) = true

    override fun clear() = Unit
}

private object FakeLocales : AppLocaleController {
    override fun current() = AppLanguage.SYSTEM

    override fun apply(language: AppLanguage) = Unit
}
