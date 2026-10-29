package com.pasich.encly.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.pasich.encly.core.security.InitialStatus
import com.pasich.encly.desktop.di.DesktopAppGraph
import com.pasich.encly.desktop.platform.SensitiveClipboardBoundary
import com.pasich.encly.desktop.platform.ToastHost
import com.pasich.encly.desktop.window.DesktopShortcuts
import com.pasich.encly.desktop.window.WindowServices
import com.pasich.encly.platform.AppWindow
import com.pasich.encly.platform.LocalAppInfo
import com.pasich.encly.platform.LocalAppLocales
import com.pasich.encly.platform.LocalAppWindow
import com.pasich.encly.platform.LocalDocumentPickers
import com.pasich.encly.platform.LocalExternalLinks
import com.pasich.encly.platform.LocalNavigationRequests
import com.pasich.encly.platform.LocalNoteDocumentRenderer
import com.pasich.encly.platform.LocalNoteEditClaims
import com.pasich.encly.platform.LocalNoteWindows
import com.pasich.encly.platform.LocalSensitiveClipboard
import com.pasich.encly.platform.LocalSessionSharedAcrossWindows
import com.pasich.encly.platform.LocalToaster
import com.pasich.encly.presentation.designsystem.EnclyShortcuts
import com.pasich.encly.presentation.navigation.EnclyApp
import com.pasich.encly.ui.theme.AppTheme
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The whole app inside a window: what `MainActivity` does on Android. It resolves the vault's start state off
 * the UI thread, gives the shared UI its platform services, and draws [EnclyApp] and the in-window messages.
 * It needs no real window ([appWindow] says what the window can do), so tests compose it headless. [shortcuts] is
 * the window's one key map, the same the menu bar's accelerators are built from. [services] are the window's own
 * (its menu requests, dropped files and file dialogs); the vault, the lock and the rest are the [graph]'s, shared by
 * every window of the process.
 */
@Composable
fun DesktopApp(
    graph: DesktopAppGraph,
    appWindow: AppWindow,
    modifier: Modifier = Modifier,
    shortcuts: EnclyShortcuts = remember(graph) { DesktopShortcuts.map(graph.environment.os) },
    services: WindowServices = remember(graph) { WindowServices.create(graph.environment, graph.lifecycle) },
) {
    var startStatus by remember(graph) { mutableStateOf<InitialStatus?>(null) }
    LaunchedEffect(graph) {
        val status = withContext(Dispatchers.IO) {
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
        startStatus = status
    }

    DesktopHostServices(graph, appWindow, services) {
        // There is no Activity to recreate when the language changes: a new key makes every screen read its
        // strings again.
        val revision by graph.locales.revision.collectAsState()
        Box(modifier.fillMaxSize()) {
            key(revision) {
                EnclyApp(
                    startStatus = startStatus,
                    securityManager = graph.securityManager,
                    sessionLockManager = graph.sessionLockManager,
                    shortcuts = shortcuts,
                    // The menu bar registers these accelerators itself, so the keyboard host leaves them to it.
                    declinedCommands = DesktopShortcuts.menuOwned,
                )
            }
            AppTheme {
                ToastHost(graph.toaster, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}

/** Hands the shared UI what it needs from the desktop, once, around its whole content. */
@Composable
fun DesktopHostServices(
    graph: DesktopAppGraph,
    appWindow: AppWindow,
    services: WindowServices,
    content: @Composable () -> Unit,
) {
    val viewModelStoreOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(viewModelStoreOwner) { onDispose { viewModelStoreOwner.viewModelStore.clear() } }
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides viewModelStoreOwner,
        LocalLifecycleOwner provides graph.lifecycle,
        LocalMetroViewModelFactory provides graph.metroViewModelFactory,
        LocalAppWindow provides appWindow,
        LocalExternalLinks provides graph.externalLinks,
        LocalToaster provides graph.toaster,
        LocalSensitiveClipboard provides graph.clipboard,
        LocalAppLocales provides graph.locales,
        LocalAppInfo provides graph.appInfo,
        LocalDocumentPickers provides services.documentPickers,
        LocalNavigationRequests provides services.navigation,
        LocalNoteDocumentRenderer provides graph.noteDocuments,
        LocalNoteWindows provides services.noteWindows,
        LocalNoteEditClaims provides services.claims,
        // Every window shows the same vault behind the same lock: a lock screen follows an unlock made elsewhere.
        LocalSessionSharedAcrossWindows provides true,
    ) {
        SensitiveClipboardBoundary(graph.clipboardWiper, content)
    }
}
