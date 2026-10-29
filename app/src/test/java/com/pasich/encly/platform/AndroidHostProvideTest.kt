package com.pasich.encly.platform

import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.R
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.security.ActivityBiometricHost
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import org.junit.After
import org.junit.Assert.assertEquals
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
