package com.pasich.encly.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.pasich.encly.BuildConfig
import com.pasich.encly.R
import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.di.ApplicationContext
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.locale.AppLocales
import com.pasich.encly.core.security.ActivityBiometricHost
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** Hands the shared UI what it needs from Android, once per Activity, around its whole content. */
@Inject
class AndroidHostServices(
    private val toaster: Toaster,
    private val clipboard: SensitiveClipboard,
    private val locales: AppLocaleController,
    private val info: AppInfo,
    private val viewModelFactory: MetroViewModelFactory,
) {
    /**
     * Provides the `Local...` services of `:shared` for [activity] to [content], inside the keyboard and
     * clipboard policy ([strictKeyboard] is "Strict keyboard privacy" in Settings).
     */
    @Composable
    fun Provide(activity: AppCompatActivity, strictKeyboard: Boolean, content: @Composable () -> Unit) {
        val view = LocalView.current
        val window = remember(activity, view) { AndroidAppWindow(activity, view) }
        val links = remember(activity) { AndroidExternalLinks(activity) }
        val biometricHost = remember(activity) { ScreenBiometricHost(ActivityBiometricHost(activity), activity) }
        CompositionLocalProvider(
            LocalMetroViewModelFactory provides viewModelFactory,
            LocalAppWindow provides window,
            LocalExternalLinks provides links,
            LocalToaster provides toaster,
            LocalSensitiveClipboard provides clipboard,
            LocalBiometricHost provides biometricHost,
            LocalAppLocales provides locales,
            LocalAppInfo provides info,
            LocalDocumentPickers provides AndroidDocumentPickers,
        ) {
            SecureTextInputBoundary(strict = strictKeyboard) {
                SensitiveClipboardBoundary(content)
            }
        }
    }
}

/**
 * The Activity's window as [AppWindow]. [view] is the Compose view, which the keyboard is shown and hidden
 * through on API 29 and older.
 */
class AndroidAppWindow(private val activity: Activity, private val view: View) : AppWindow {
    private val window get() = activity.window

    override val isChangingConfigurations: Boolean get() = activity.isChangingConfigurations

    override fun enforceSecure() = window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

    override fun setLightStatusBar(light: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = light
    }

    override fun setBackgroundColor(argb: Int) = window.setBackgroundDrawable(argb.toDrawable())

    override fun hideKeyboard() {
        WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())
    }

    override fun showKeyboard() {
        WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.ime())
    }
}

/** [ExternalLinks] that start the activity of another app from [context] (an Activity). */
class AndroidExternalLinks(private val context: Context) : ExternalLinks {

    @Suppress("TooGenericExceptionCaught") // Another app's failure to take the link must never crash Encly.
    override fun openUrl(url: String, chooser: Boolean): Boolean {
        val view = Intent(Intent.ACTION_VIEW, url.toUri())
        return try {
            context.startActivity(if (chooser) Intent.createChooser(view, null) else view)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (e: RuntimeException) {
            // FileUriExposedException, SecurityException: the address cannot leave the app safely.
            AppLogger.w(TAG, "Link not opened: ${e.javaClass.simpleName}")
            false
        }
    }

    override fun writeMail(address: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply { data = "mailto:$address".toUri() }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private companion object {
        const val TAG = "ExternalLinks"
    }
}

/** Toasts with the string resource read in a coroutine and shown on the main thread. */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class)
class AndroidToaster(@param:ApplicationContext private val context: Context) : Toaster {
    override fun show(text: StringResource, duration: ToastDuration, vararg args: Any) {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val length = if (duration == ToastDuration.LONG) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            Toast.makeText(context, getString(text, *args), length).show()
        }
    }
}

/** [SensitiveClipboard] over the system clipboard, by [SensitiveClip]'s policy. */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class)
class AndroidSensitiveClipboard(@param:ApplicationContext private val context: Context) : SensitiveClipboard {
    private val manager get() = context.getSystemService(ClipboardManager::class.java)

    override val systemConfirmsCopy: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun copy(label: String, text: String): Boolean {
        val clipboard = manager ?: return false
        SensitiveClip.copy(clipboard, label, text)
        return true
    }

    override fun clear() {
        manager?.let(SensitiveClip::clear)
    }
}

/** The in-app language through [AppLocales] (AppCompat's per-app locales). */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class)
class AndroidAppLocaleController : AppLocaleController {
    override fun current(): AppLanguage = AppLocales.current()

    override fun apply(language: AppLanguage) = AppLocales.apply(language)
}

/** This build's facts, from [BuildConfig] and the launcher label. */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class)
class AndroidAppInfo(@param:ApplicationContext private val context: Context) : AppInfo {
    override val appName: String get() = context.getString(R.string.app_name)
    override val versionName: String = BuildConfig.VERSION_NAME
    override val donationsEnabled: Boolean = BuildConfig.DONATIONS_ENABLED
    override val storeRatingEnabled: Boolean = BuildConfig.STORE_RATING_ENABLED
    override val storePageLinks: List<String> = listOf(
        "market://details?id=${context.packageName}",
        "https://play.google.com/store/apps/details?id=${context.packageName}",
    )
}

/** The app's cache directory. */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class)
class AndroidCacheDirectory(@param:ApplicationContext private val context: Context) : CacheDirectory {
    override val path: Path get() = Path(context.cacheDir.path)
}
