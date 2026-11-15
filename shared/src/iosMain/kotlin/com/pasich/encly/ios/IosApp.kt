package com.pasich.encly.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.pasich.encly.core.di.platformIoDispatcher
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.ios.di.IosAppGraph
import com.pasich.encly.ios.platform.SensitiveClipboardBoundary
import com.pasich.encly.ios.platform.ToastHost
import com.pasich.encly.ios.security.IOS_MAX_AUTO_LOCK_MILLIS
import com.pasich.encly.ios.security.IosBiometricHost
import com.pasich.encly.ios.security.biometryDisplayName
import com.pasich.encly.platform.AppWindow
import com.pasich.encly.platform.BiometricWording
import com.pasich.encly.platform.HostPlatform
import com.pasich.encly.platform.LocalAppInfo
import com.pasich.encly.platform.LocalAppLocales
import com.pasich.encly.platform.LocalAppWindow
import com.pasich.encly.platform.LocalBiometricHost
import com.pasich.encly.platform.LocalBiometricWording
import com.pasich.encly.platform.LocalDocumentPickers
import com.pasich.encly.platform.LocalExternalLinks
import com.pasich.encly.platform.LocalHostPlatform
import com.pasich.encly.platform.LocalKeyboardPrivacyOffered
import com.pasich.encly.platform.LocalMaxAutoLockMillis
import com.pasich.encly.platform.LocalSensitiveClipboard
import com.pasich.encly.platform.LocalToaster
import com.pasich.encly.platform.LocalVaultTextKeyboardType
import com.pasich.encly.platform.ScreenBiometricHost
import com.pasich.encly.presentation.navigation.EnclyApp
import com.pasich.encly.ui.theme.AppTheme
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

/**
 * The whole app inside the Compose view controller: what `MainActivity` does on Android and `DesktopApp` on the
 * desktop. It resolves the vault's start state off the main thread, gives the shared UI its platform services, and
 * draws [EnclyApp] and the in-window messages.
 */
@Composable
internal fun IosApp(graph: IosAppGraph, appWindow: AppWindow, modifier: Modifier = Modifier) {
    var startStatus by remember(graph) { mutableStateOf<InitialStatus?>(null) }
    LaunchedEffect(graph) {
        val status = withContext(platformIoDispatcher) {
            // Never throws: unreadable vault state routes to the damaged-vault screen.
            val resolved = graph.securityManager.resolveInitialStatus()
            try {
                graph.settingsRepository.loadThemeSettings(existingInstall = !graph.securityManager.isOnboardingShow())
            } catch (_: IOException) {
                // An unreadable settings file: the theme starts from its defaults.
            }
            resolved
        }
        // A committed vault that is not open starts locked, before the first frame.
        if (status == InitialStatus.AUTH) graph.sessionLockManager.requireUnlock()
        // The system biometric prompt is built outside any coroutine: give it finished texts first.
        graph.biometricTexts.refresh()
        startStatus = status
    }

    IosHostServices(graph, appWindow) {
        // iOS cannot switch the language of a running app: a chosen language applies at the next start, so the
        // screens are not rebuilt (that would only reset the navigation to the launch route).
        Box(modifier.fillMaxSize()) {
            EnclyApp(
                startStatus = startStatus,
                securityManager = graph.securityManager,
                sessionLockManager = graph.sessionLockManager,
            )
            AppTheme {
                ToastHost(
                    graph.toaster,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(EnclyTheme.spacing.m),
                )
            }
        }
    }
}

/** Hands the shared UI what it needs from iOS, once, around its whole content. */
@Composable
internal fun IosHostServices(graph: IosAppGraph, appWindow: AppWindow, content: @Composable () -> Unit) {
    val viewModelStoreOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(viewModelStoreOwner) { onDispose { viewModelStoreOwner.viewModelStore.clear() } }
    val biometricHost = remember(graph) { ScreenBiometricHost(IosBiometricHost, graph.lifecycle) }
    val biometricName = remember(graph) { biometryDisplayName() }
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides viewModelStoreOwner,
        LocalLifecycleOwner provides graph.lifecycle,
        LocalMetroViewModelFactory provides graph.metroViewModelFactory,
        LocalAppWindow provides appWindow,
        LocalExternalLinks provides graph.externalLinks,
        LocalToaster provides graph.toaster,
        LocalSensitiveClipboard provides graph.clipboard,
        LocalBiometricHost provides biometricHost,
        LocalAppLocales provides graph.locales,
        LocalAppInfo provides graph.appInfo,
        LocalKeyboardPrivacyOffered provides false,
        LocalVaultTextKeyboardType provides KeyboardType.Text,
        LocalMaxAutoLockMillis provides IOS_MAX_AUTO_LOCK_MILLIS,
        LocalHostPlatform provides HostPlatform.IOS,
        LocalBiometricWording provides BiometricWording.Named(biometricName),
        LocalDocumentPickers provides graph.pickers,
    ) {
        SensitiveClipboardBoundary(graph.clipboardPolicy, content)
    }
}
