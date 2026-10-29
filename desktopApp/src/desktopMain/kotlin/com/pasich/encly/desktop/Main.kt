package com.pasich.encly.desktop

import androidx.compose.ui.window.application
import com.pasich.encly.core.keystore.DesktopPinFactor
import com.pasich.encly.core.keystore.SecretStoreUnavailableException
import com.pasich.encly.core.security.PinHardwareFactor
import com.pasich.encly.desktop.di.DesktopAppGraph
import com.pasich.encly.desktop.platform.DesktopEnvironment
import com.pasich.encly.desktop.storage.AppDirectories
import com.pasich.encly.desktop.storage.DesktopOs
import com.pasich.encly.desktop.window.EnclyWindows
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.launch
import java.awt.GraphicsEnvironment
import java.awt.Taskbar
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JOptionPane
import javax.swing.UIManager
import kotlin.system.exitProcess

private const val APP_NAME = "Encly"

/**
 * Starts Encly on the desktop: the private data directory, the OS secret store, the dependency graph and the
 * window. See `desktopApp/README.md` for what is stored where and what the app promises.
 */
fun main(args: Array<String>) {
    System.setProperty("apple.awt.application.name", APP_NAME)
    val os = DesktopOs.current()
    useNativeLookAndFeel(os)

    val dataDirectory = try {
        AppDirectories.createPrivate(AppDirectories.systemDataDirectory())
    } catch (e: IOException) {
        fail("Encly cannot create its data folder: ${e.message}")
    }
    // Held until the process ends: the OS releases the lock with it.
    val instance = SingleInstanceLock.tryAcquire(dataDirectory)
        ?: fail("Encly is already running.", exitCode = 0)

    val pinFactor = createPinFactor(dataDirectory)
    val environment = DesktopEnvironment.system(os, dataDirectory, pinFactor)
    val graph = createGraphFactory<DesktopAppGraph.Factory>().create(environment)
    startServices(graph)
    setDockIcon()

    val launchFiles = LaunchFiles.fromArguments(args)
    application(exitProcessOnExit = true) {
        EnclyWindows(graph, launchFiles) {
            shutdown(graph)
            instance.close()
            exitApplication()
        }
    }
}

/** The PIN's device-bound key lives in the OS secret store; without one there is no PIN, so the app cannot run. */
private fun createPinFactor(dataDirectory: Path): PinHardwareFactor = try {
    DesktopPinFactor.create(dataDirectory)
} catch (e: SecretStoreUnavailableException) {
    fail("Encly needs the operating system's secret store, which is not available: ${e.message}")
}

/** The long-lived parts that run for the whole process: the lock sources and the first sweep. */
private fun startServices(graph: DesktopAppGraph) {
    graph.locales.syncDefaultLocale()
    // SessionLockManager follows the window: it is told when the app is left and when it is back.
    graph.lifecycle.lifecycle.addObserver(graph.sessionLockManager)
    // The OS locking the screen locks the vault, whether Encly is in front or not.
    graph.deviceLockWatcher.onLocked { graph.sessionLockManager.lockNow() }
    graph.deviceLockWatcher.start()
    // A My Notes hand-off (plaintext) that a killed process left in the cache; off the UI thread.
    graph.applicationScope.launch { graph.handoffStagingSweeper.sweep() }
}

/**
 * Closes the vault and empties the clipboard of what Encly put there before the process ends. The windows are
 * closed and their editors have saved by now (see `EnclyWindows`).
 */
private fun shutdown(graph: DesktopAppGraph) {
    graph.sessionLockManager.lockNow()
    // First-run setup has no lockable vault yet but may hold the database open: it is closed all the same.
    if (graph.securityManager.isDatabaseUnlocked()) graph.securityManager.lock()
    graph.clipboardWiper.wipeIfStillHeld()
    graph.deviceLockWatcher.close()
    graph.lifecycle.destroy()
}

/** The window menu bar and file chooser of Windows look like Windows; elsewhere the defaults are kept. */
private fun useNativeLookAndFeel(os: DesktopOs) {
    if (os != DesktopOs.WINDOWS) return
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
}

/** The Dock icon when running unpackaged (a packaged macOS app gets it from its bundle). */
private fun setDockIcon() {
    if (GraphicsEnvironment.isHeadless() || !Taskbar.isTaskbarSupported()) return
    runCatching {
        val taskbar = Taskbar.getTaskbar()
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            Thread.currentThread().contextClassLoader.getResourceAsStream("encly-icon.png")?.use {
                taskbar.iconImage = ImageIO.read(it)
            }
        }
    }
}

private fun fail(message: String, exitCode: Int = 1): Nothing {
    System.err.println(message)
    if (!GraphicsEnvironment.isHeadless()) {
        runCatching { JOptionPane.showMessageDialog(null, message, APP_NAME, JOptionPane.ERROR_MESSAGE) }
    }
    exitProcess(exitCode)
}
