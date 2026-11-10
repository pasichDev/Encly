@file:OptIn(ExperimentalForeignApi::class)

package com.pasich.encly.ios.platform

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.platform.KeyValueStore
import com.pasich.encly.platform.AppInfo
import com.pasich.encly.platform.AppLocaleController
import com.pasich.encly.platform.AppWindow
import com.pasich.encly.platform.CacheDirectory
import com.pasich.encly.platform.ExternalLinks
import com.pasich.encly.platform.ToastDuration
import com.pasich.encly.platform.Toaster
import com.pasich.encly.utils.DeviceCapabilities
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.endEditing

/**
 * [ExternalLinks] that hands an address to iOS. Only `https`, `http` and `mailto` addresses leave the app: a `file:`
 * or custom-scheme address is refused, so a link in a note can never start another app or read a local file. iOS has
 * no chooser: the default app opens it. `openURL` answers later, so a web address counts as opened once it was
 * handed over; for a mail address the Mail app (or any `mailto` handler) is asked for first (the scheme is in
 * `LSApplicationQueriesSchemes`).
 */
internal class IosExternalLinks : ExternalLinks {
    @Suppress("ReturnCount") // Guards: no address, a scheme that may not leave the app.
    override fun openUrl(url: String, chooser: Boolean): Boolean {
        val target = parse(url) ?: return false
        if (target.scheme?.lowercase() !in WEB_SCHEMES) return false
        return open(target)
    }

    override fun writeMail(address: String): Boolean {
        val target = parse("mailto:$address") ?: return false
        return open(target)
    }

    private fun open(target: NSURL): Boolean {
        val application = UIApplication.sharedApplication
        if (target.scheme.equals("mailto", ignoreCase = true) && !application.canOpenURL(target)) return false
        application.openURL(target, options = emptyMap<Any?, Any?>(), completionHandler = null)
        return true
    }

    private fun parse(text: String): NSURL? = NSURL.URLWithString(text.trim())?.takeIf { it.scheme != null }

    private companion object {
        val WEB_SCHEMES = setOf("https", "http", "mailto")
    }
}

/**
 * What the window offers the UI. [view] is the root view of the host, painted behind the content, and [statusBar]
 * sets the style of the status bar (dark icons on a light page). The cover that keeps the content out of the app
 * switcher and recordings is [com.pasich.encly.ios.security.PrivacyShield]; [enforceSecure] re-asserts it.
 */
internal class IosAppWindow(
    private val enforceShield: () -> Unit,
    private val view: () -> UIView?,
    private val statusBar: (darkContent: Boolean) -> Unit,
) : AppWindow {
    override val isChangingConfigurations: Boolean = false

    override fun enforceSecure() = enforceShield()

    override fun setLightStatusBar(light: Boolean) = statusBar(light)

    override fun setBackgroundColor(argb: Int) {
        view()?.backgroundColor = Color(argb).toUiColor()
    }

    override fun hideKeyboard() {
        view()?.endEditing(true)
    }

    // A focused text field raises the keyboard by itself on iOS.
    override fun showKeyboard() = Unit
}

internal fun Color.toUiColor(): UIColor =
    UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())

/**
 * Messages as snackbars inside the window ([ToastHost]); iOS has no system toast. The string resource is read in
 * a coroutine on [dispatcher] (the main thread), in the in-app language.
 */
internal class IosToaster(dispatcher: CoroutineDispatcher = Dispatchers.Main) : Toaster {
    val hostState = SnackbarHostState()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    override fun show(text: StringResource, duration: ToastDuration, vararg args: Any) {
        scope.launch {
            val message = getString(text, *args)
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(
                message = message,
                duration = if (duration == ToastDuration.LONG) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }
}

/** Where [IosToaster]'s messages appear: put it at the bottom of the window, above the content. */
@Composable
internal fun ToastHost(toaster: IosToaster, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = toaster.hostState, modifier = modifier)
}

/**
 * The in-app language. It is kept in [flags] and also written as the app's `AppleLanguages`, which is what the
 * system and the string resources read when the app starts. iOS cannot switch a running app's language, so a
 * change takes effect the next time the app is started (the system's own Settings → Encly → Language does the
 * same). [revision] changes on every [apply] for the rest of the host.
 */
internal class IosAppLocaleController(private val flags: KeyValueStore) : AppLocaleController {
    private val _revision = MutableStateFlow(0)

    /** Increases whenever the language was applied. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    override fun current(): AppLanguage = AppLanguage.fromTag(flags.getString(LANGUAGE_KEY, null))

    override fun apply(language: AppLanguage) {
        flags.edit {
            if (language.tag == null) remove(LANGUAGE_KEY) else putString(LANGUAGE_KEY, language.tag!!)
        }
        writeAppleLanguages(language)
        _revision.value += 1
    }

    /** Puts the stored language where the system reads it (idempotent); done before anything is composed. */
    fun syncAppleLanguages() = writeAppleLanguages(current())

    private fun writeAppleLanguages(language: AppLanguage) {
        val defaults = NSUserDefaults.standardUserDefaults
        val tag = language.tag
        if (tag ==
            null
        ) {
            defaults.removeObjectForKey(APPLE_LANGUAGES)
        } else {
            defaults.setObject(listOf(tag), APPLE_LANGUAGES)
        }
    }

    private companion object {
        const val LANGUAGE_KEY = "app_language"
        const val APPLE_LANGUAGES = "AppleLanguages"
    }
}

/** This build's facts, from the app bundle (`CFBundleShortVersionString` is written from `version.properties`). */
internal class IosAppInfo : AppInfo {
    override val appName: String = "Encly"
    override val versionName: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0.0.0"

    // Every iOS build is distributed through the App Store or TestFlight: no donation links (store policy), as in
    // the Play build. There is no store page to rate yet.
    override val donationsEnabled: Boolean = false
    override val storeRatingEnabled: Boolean = false
    override val storePageLinks: List<String> = emptyList()
}

/** The app's cache directory: a folder of the data directory, so it is as private and as protected as the rest. */
internal class IosCacheDirectory(override val path: Path) : CacheDirectory

/** iOS has no wallpaper-derived colours Encly can read. */
internal object IosDeviceCapabilities : DeviceCapabilities {
    override fun supportsDynamicColors(): Boolean = false
}
