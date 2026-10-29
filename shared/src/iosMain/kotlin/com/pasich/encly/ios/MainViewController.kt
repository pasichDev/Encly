package com.pasich.encly.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.pasich.encly.core.keystore.BiometricKeychain
import com.pasich.encly.core.keystore.IosPinFactor
import com.pasich.encly.core.security.PinHardwareFactor
import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.ios.di.DataFiles
import com.pasich.encly.ios.di.IosAppGraph
import com.pasich.encly.ios.di.IosEnvironment
import com.pasich.encly.ios.platform.IosAppWindow
import com.pasich.encly.ios.storage.IosDataDirectory
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.UIKit.UIViewController

/**
 * The one entry point of the iOS app: the root view controller that the Xcode project's SwiftUI shell shows.
 * It hosts [IosApp] and creates the process-wide graph on first use (on the main thread).
 */
// Named as the Swift call site reads it, `MainViewControllerKt.MainViewController()`: the Compose template convention.
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
public fun MainViewController(): UIViewController {
    val graph = IosHost.graph
    var host: HostViewController? = null
    val window = IosAppWindow(
        enforceShield = graph.shield::enforce,
        view = { host?.view },
        statusBar = { darkContent -> host?.setDarkStatusBarContent(darkContent) },
    )
    val content = ComposeUIViewController { IosApp(graph, window) }
    return HostViewController(content).also { host = it }
}

/** The long-lived parts of the process: the data directory, the graph and the lock sources. */
internal object IosHost {
    val graph: IosAppGraph by lazy { create() }

    private fun create(): IosAppGraph {
        val dataDirectory = IosDataDirectory.prepare()
        val pinFactor = IosPinFactor.create()
        dropOrphanedKeychainItems(dataDirectory, pinFactor)
        val environment = IosEnvironment(dataDirectory, pinFactor)
        val graph = createGraphFactory<IosAppGraph.Factory>().create(environment)
        start(graph)
        return graph
    }

    /**
     * Keychain items outlive an uninstall (the app's data does not): with no vault file there is no vault they
     * could belong to, so the PIN device secrets and the biometric key of an earlier install are removed. Android
     * loses its Keystore keys with the app; this gives a reinstall the same clean start.
     */
    private fun dropOrphanedKeychainItems(dataDirectory: String, pinFactor: PinHardwareFactor) {
        if (SystemFileSystem.exists(Path("$dataDirectory/${DataFiles.VAULT_STATE}"))) return
        PinKeySlot.entries.forEach(pinFactor::delete)
        BiometricKeychain().delete()
    }

    private fun start(graph: IosAppGraph) {
        graph.locales.syncAppleLanguages()
        // The cover goes up before anything else can be drawn or snapshotted.
        graph.shield.install()
        // SessionLockManager follows the application: it is told when the app is left and when it is back.
        graph.lifecycle.lifecycle.addObserver(graph.sessionLockManager)
        // iOS suspends the app soon after it is stopped: a short background task keeps the auto-lock timer and the
        // device-lock signal working, and closes the vault when iOS takes the task back.
        graph.lifecycle.lifecycle.addObserver(graph.backgroundLockGuard)
        // The device locking locks the vault, whether Encly is in front or not.
        graph.deviceLockWatcher.onLocked { graph.sessionLockManager.lockNow() }
        // Pick grants and imported copies do not outlive the unlocked session they were made in.
        graph.pickedCopies.purge()
        graph.applicationScope.launch {
            graph.sessionLockManager.locked.collect { locked ->
                if (locked) {
                    graph.scopes.releaseAll()
                    graph.pickedCopies.purge()
                    withContext(Dispatchers.Main) { graph.backgroundLockGuard.end() }
                }
            }
        }
        // A My Notes hand-off (plaintext) that a killed process left in the cache; off the main thread.
        graph.applicationScope.launch { graph.handoffStagingSweeper.sweep() }
    }
}
