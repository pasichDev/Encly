package com.pasich.encly.desktop.platform

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
import java.awt.Desktop
import java.awt.Window
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import java.util.Properties

/** [LinkOpener] on `java.awt.Desktop`: the default browser or mail application. Looked up on first use. */
object AwtLinkOpener : LinkOpener {
    @Suppress("ReturnCount") // Guards: no desktop, no action.
    override fun open(uri: URI): Boolean {
        val desktop = runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null }.getOrNull()
            ?: return false
        val action = if (uri.scheme.equals("mailto", ignoreCase = true)) Desktop.Action.MAIL else Desktop.Action.BROWSE
        if (!desktop.isSupported(action)) return false
        // Opening can block on some desktops (Linux): never on the UI thread.
        Thread({
            runCatching {
                if (action == Desktop.Action.MAIL) desktop.mail(uri) else desktop.browse(uri)
            }
        }, "encly-open-link").apply { isDaemon = true }.start()
        return true
    }
}

/**
 * [ExternalLinks] that hands an address to the OS. Only `https`, `http` and `mailto` addresses leave the app:
 * a `file:` or custom-scheme address is refused, so a link in a note can never start a local program. There is
 * no chooser on the desktop: the default application opens it.
 */
class DesktopExternalLinks(private val opener: LinkOpener) : ExternalLinks {
    override fun openUrl(url: String, chooser: Boolean): Boolean {
        val uri = parse(url) ?: return false
        return uri.scheme.lowercase() in WEB_SCHEMES && opener.open(uri)
    }

    override fun writeMail(address: String): Boolean {
        val uri = parse("mailto:$address") ?: return false
        return opener.open(uri)
    }

    private fun parse(text: String): URI? = try {
        URI(text.trim()).takeIf { it.scheme != null }
    } catch (_: URISyntaxException) {
        null
    }

    private companion object {
        val WEB_SCHEMES = setOf("https", "http", "mailto")
    }
}

/** What the window offers the UI: the capture exclusion and the background behind the content. */
class DesktopAppWindow(
    private val capture: CaptureExclusion,
    private val windowProvider: () -> Window?,
    private val onBackground: (Color) -> Unit = {},
) : AppWindow {
    override val isChangingConfigurations: Boolean = false

    /** Excludes the window from screenshots and recordings, wherever the OS allows (see [CaptureExclusion]). */
    override fun enforceSecure() {
        windowProvider()?.let(capture::exclude)
    }

    override fun setLightStatusBar(light: Boolean) = Unit

    override fun setBackgroundColor(argb: Int) = onBackground(Color(argb))

    override fun hideKeyboard() = Unit

    override fun showKeyboard() = Unit
}

/**
 * Messages as snackbars inside the window ([ToastHost]); there is no system toast on the desktop. The
 * string resource is read in a coroutine on [dispatcher] (the UI thread), in the in-app language.
 */
class DesktopToaster(dispatcher: CoroutineDispatcher = Dispatchers.Main) : Toaster {
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

/** Where [DesktopToaster]'s messages appear: put it at the bottom of the window, above the content. */
@Composable
fun ToastHost(toaster: DesktopToaster, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = toaster.hostState, modifier = modifier)
}

/**
 * The in-app language. It is kept in [flags] and applied as the process's default locale, which the string
 * resources read. There is no Activity to recreate: [revision] changes on every [apply], and the window host
 * recomposes the app under a new key, so every screen reads its strings again.
 */
class DesktopAppLocaleController(
    private val flags: KeyValueStore,
    private val systemLocale: Locale = Locale.getDefault(),
) : AppLocaleController {
    private val _revision = MutableStateFlow(0)

    /** Increases whenever the language was applied. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    override fun current(): AppLanguage = AppLanguage.fromTag(flags.getString(LANGUAGE_KEY, null))

    override fun apply(language: AppLanguage) {
        flags.edit {
            if (language.tag == null) remove(LANGUAGE_KEY) else putString(LANGUAGE_KEY, language.tag!!)
        }
        useAsDefaultLocale(language)
        _revision.value += 1
    }

    /** Puts the stored language into the process before anything is composed. */
    fun syncDefaultLocale() = useAsDefaultLocale(current())

    private fun useAsDefaultLocale(language: AppLanguage) {
        Locale.setDefault(language.tag?.let(Locale::forLanguageTag) ?: systemLocale)
    }

    private companion object {
        const val LANGUAGE_KEY = "app_language"
    }
}

/** This build's facts. The version is written from `version.properties` at build time. */
class DesktopAppInfo(override val versionName: String) : AppInfo {
    override val appName: String = "Encly"

    // Not a store build: the donation links are allowed.
    override val donationsEnabled: Boolean = true
    override val storeRatingEnabled: Boolean = false
    override val storePageLinks: List<String> = emptyList()

    companion object {
        private const val RESOURCE = "/encly-build.properties"

        /** The info of the running build; the version is "0.0.0" if the build resource is missing. */
        fun fromBuild(): DesktopAppInfo {
            val properties = Properties()
            DesktopAppInfo::class.java.getResourceAsStream(RESOURCE)?.use(properties::load)
            return DesktopAppInfo(properties.getProperty("version")?.takeIf { it.isNotBlank() } ?: "0.0.0")
        }
    }
}

/** The app's cache directory: a folder of the data directory, so it is private to the user as well. */
class DesktopCacheDirectory(override val path: Path) : CacheDirectory

/** A desktop has no wallpaper-derived colours Encly can read. */
object DesktopDeviceCapabilities : DeviceCapabilities {
    override fun supportsDynamicColors(): Boolean = false
}
