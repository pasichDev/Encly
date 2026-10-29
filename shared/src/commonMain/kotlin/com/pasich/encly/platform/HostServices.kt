package com.pasich.encly.platform

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.LifecycleOwner
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.security.BiometricHost
import kotlinx.io.files.Path
import org.jetbrains.compose.resources.StringResource

// Small interfaces for what the UI and the ViewModels need from the platform they run on. The host app
// implements them (the Android ones are in `:app`) and gives the composables theirs through the `Local...`
// values below, the ViewModels theirs through the dependency graph.

/** How long a [Toaster] message stays. */
enum class ToastDuration { SHORT, LONG }

/** Brief messages the system shows over any screen (a toast on Android). */
interface Toaster {
    /**
     * Shows the string resource [text] (filled with [args]) in the in-app language. It may be called
     * from any thread and from a plain click handler; the resource is read and the message shown for it.
     */
    fun show(text: StringResource, duration: ToastDuration, vararg args: Any)
}

/** Hands a link or an e-mail to another app. Nothing is fetched by Encly itself. */
interface ExternalLinks {
    /**
     * Opens [url] in an app the user picks when [chooser] is set, else in the default one. False when no
     * app can open it or the system refused; the caller says so.
     */
    fun openUrl(url: String, chooser: Boolean = false): Boolean

    /** Starts a new e-mail to [address]. False when there is no mail app. */
    fun writeMail(address: String): Boolean
}

/**
 * Encly's clipboard policy for what is copied out of the app: every clip is marked sensitive, so the
 * system does not preview its content or suggest it, and it is cleared after a minute unless something
 * else was copied since.
 */
interface SensitiveClipboard {
    /** True when the system already confirms a copy itself, so the app need not say it. */
    val systemConfirmsCopy: Boolean

    /** Copies [text] under [label] as a sensitive clip that clears itself. False when nothing was copied. */
    fun copy(label: String, text: String): Boolean

    /** Empties the clipboard now (e.g. after the recovery phrase was pasted). */
    fun clear()
}

/** What the app window offers the UI. */
interface AppWindow {
    /** True while the hosting Activity is being recreated (a rotation): not the user leaving the screen. */
    val isChangingConfigurations: Boolean

    /** Re-asserts that the window cannot be captured (screenshots, recents, casting). */
    fun enforceSecure()

    /** Dark icons on the status bar when [light] is true (a light page), light ones otherwise. */
    fun setLightStatusBar(light: Boolean)

    /** Paints the window behind the content, which shows during window transitions and the keyboard. */
    fun setBackgroundColor(argb: Int)

    fun hideKeyboard()

    fun showKeyboard()

    /** A host with no window to manage (previews, tests). */
    object None : AppWindow {
        override val isChangingConfigurations: Boolean = false

        override fun enforceSecure() = Unit

        override fun setLightStatusBar(light: Boolean) = Unit

        override fun setBackgroundColor(argb: Int) = Unit

        override fun hideKeyboard() = Unit

        override fun showKeyboard() = Unit
    }
}

/**
 * What a biometric prompt is shown on ([biometric], an Activity on Android) and the lifetime it belongs to
 * ([owner], the same Activity). The security core only passes [biometric] on; the ViewModels use [owner] to
 * release a prompt whose screen died. Two of the same Activity are equal.
 */
data class ScreenBiometricHost(val biometric: BiometricHost, val owner: LifecycleOwner)

/** The in-app language: the one the user picked in Settings, which the platform applies to the whole app. */
interface AppLocaleController {
    /** The language in force; [AppLanguage.SYSTEM] when the device's own is followed. */
    fun current(): AppLanguage

    /** Applies [language] to the app; the running screen is recreated to read its strings again. */
    fun apply(language: AppLanguage)
}

/** Facts about this build of the app that the UI shows or branches on. */
interface AppInfo {
    val appName: String
    val versionName: String

    /** The store build has no donation links (store policy); the other one has. */
    val donationsEnabled: Boolean

    /** Only the store build links to its own store page to rate the app. */
    val storeRatingEnabled: Boolean

    /** This app's page in the device's store, the store app's own link first and a web page after it. */
    val storePageLinks: List<String>

    /** A host that says nothing about itself (previews, tests). */
    object Default : AppInfo {
        override val appName: String = "Encly"
        override val versionName: String = "0.0.0"
        override val donationsEnabled: Boolean = true
        override val storeRatingEnabled: Boolean = false
        override val storePageLinks: List<String> = emptyList()
    }
}

/** A private, app-owned directory for temporary files (the app's cache on Android). */
interface CacheDirectory {
    val path: Path
}

/** The host's window, provided once by its Activity. */
val LocalAppWindow = staticCompositionLocalOf<AppWindow> { AppWindow.None }

/** The host's external links, provided once by its Activity. */
val LocalExternalLinks = staticCompositionLocalOf<ExternalLinks> { NoExternalLinks }

/** The host's toasts. */
val LocalToaster = staticCompositionLocalOf<Toaster> { NoToasts }

/** The host's clipboard policy. */
val LocalSensitiveClipboard = staticCompositionLocalOf<SensitiveClipboard> { NoClipboard }

/** The biometric host of the screen, or null where there is none (no prompt can be shown). */
val LocalBiometricHost = staticCompositionLocalOf<ScreenBiometricHost?> { null }

/** The in-app language controller. */
val LocalAppLocales = staticCompositionLocalOf<AppLocaleController> { FixedLocale }

/** Facts about the build. */
val LocalAppInfo = staticCompositionLocalOf<AppInfo> { AppInfo.Default }

private object NoToasts : Toaster {
    override fun show(text: StringResource, duration: ToastDuration, vararg args: Any) = Unit
}

private object NoExternalLinks : ExternalLinks {
    override fun openUrl(url: String, chooser: Boolean): Boolean = false

    override fun writeMail(address: String): Boolean = false
}

/** A clipboard policy that does nothing, for a host with no clipboard. */
private object NoClipboard : SensitiveClipboard {
    override val systemConfirmsCopy: Boolean = false

    override fun copy(label: String, text: String): Boolean = false

    override fun clear() = Unit
}

private object FixedLocale : AppLocaleController {
    override fun current(): AppLanguage = AppLanguage.SYSTEM

    override fun apply(language: AppLanguage) = Unit
}
