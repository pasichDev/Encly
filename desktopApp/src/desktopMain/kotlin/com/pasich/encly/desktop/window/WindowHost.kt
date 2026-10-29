package com.pasich.encly.desktop.window

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuBarScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.about
import com.pasich.encly.core.resources.backup_title
import com.pasich.encly.core.resources.import_title
import com.pasich.encly.core.resources.licenses_title
import com.pasich.encly.core.resources.lock_now
import com.pasich.encly.core.resources.main_drawer_faq
import com.pasich.encly.core.resources.main_drawer_settings
import com.pasich.encly.core.resources.menu_exit
import com.pasich.encly.core.resources.menu_file
import com.pasich.encly.core.resources.menu_help
import com.pasich.encly.core.resources.menu_minimize
import com.pasich.encly.core.resources.menu_view
import com.pasich.encly.core.resources.menu_window
import com.pasich.encly.core.resources.note_add
import com.pasich.encly.core.resources.settings_language
import com.pasich.encly.core.resources.theme
import com.pasich.encly.core.resources.theme_type_dark
import com.pasich.encly.core.resources.theme_type_light
import com.pasich.encly.core.resources.theme_type_system
import com.pasich.encly.desktop.DesktopApp
import com.pasich.encly.desktop.di.DesktopAppGraph
import com.pasich.encly.desktop.platform.DesktopAppWindow
import com.pasich.encly.desktop.security.IdleLock
import com.pasich.encly.desktop.storage.DesktopOs
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.presentation.designsystem.AppCommand
import com.pasich.encly.presentation.designsystem.EnclyShortcuts
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import java.awt.AWTEvent
import java.awt.Desktop
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.event.AWTEventListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.nio.file.Path

// The window, the menu bar, drag and drop and the OS hooks. It needs a real AWT window, so it cannot run on a
// headless machine and is excluded from the coverage gate (see build.gradle.kts); the logic it uses
// (DesktopCommands, DesktopShortcuts, WindowStateStore, DropRouter, IdleLock, DesktopAppLifecycle) is tested.

private const val FIRST_FRAME_MILLIS = 1_000L
private const val SAVE_DEBOUNCE_MILLIS = 500L

/** Encly's one window. [launchFiles] are the backup or notes files the OS started the app with. */
@OptIn(FlowPreview::class)
@Suppress("LongMethod", "ModifierMissing") // The one window of the app: it has no parent to size it.
@Composable
fun ApplicationScope.EnclyWindow(graph: DesktopAppGraph, launchFiles: List<Path>, onExit: () -> Unit) {
    val environment = graph.environment
    val os = environment.os
    val store = remember { WindowStateStore(graph.flags) }
    val saved = remember { store.load(screenBounds()) }
    val state = rememberWindowState(
        size = DpSize(
            (saved?.width ?: WindowStateStore.DEFAULT_WIDTH).dp,
            (saved?.height ?: WindowStateStore.DEFAULT_HEIGHT).dp,
        ),
        position = if (saved?.x != null && saved.y != null) {
            WindowPosition(saved.x.dp, saved.y.dp)
        } else {
            WindowPosition.PlatformDefault
        },
        placement = if (saved?.maximised == true) WindowPlacement.Maximized else WindowPlacement.Floating,
    )
    val commands = remember {
        DefaultDesktopCommands(
            navigation = graph.navigationRequests,
            lockSession = graph.sessionLockManager::lockNow,
            locales = graph.locales,
            saveThemeType = graph.settingsRepository::setThemeType,
            scope = graph.applicationScope,
            onMinimise = { state.isMinimized = true },
            onQuit = onExit,
        )
    }
    val icon = remember { loadWindowIcon() }
    // The one key map of the window: the menu's accelerators and the keyboard host read the same.
    val shortcuts = remember(os) { DesktopShortcuts.map(os) }

    Window(
        onCloseRequest = onExit,
        state = state,
        title = "Encly",
        icon = icon,
    ) {
        val composeWindow = window
        SideEffect { composeWindow.minimumSize = Dimension(WindowStateStore.MIN_WIDTH, WindowStateStore.MIN_HEIGHT) }

        DisposableEffect(composeWindow) {
            environment.dialogParent.frame = composeWindow
            val lifecycle = graph.lifecycle
            val listener = object : WindowAdapter() {
                override fun windowOpened(e: WindowEvent) {
                    environment.captureExclusion.exclude(composeWindow)
                }

                override fun windowActivated(e: WindowEvent) = lifecycle.windowActivated()

                override fun windowDeactivated(e: WindowEvent) = lifecycle.windowDeactivated()

                override fun windowIconified(e: WindowEvent) = lifecycle.windowMinimised()

                override fun windowDeiconified(e: WindowEvent) = lifecycle.windowRestored()
            }
            composeWindow.addWindowListener(listener)
            // The window is already open when the effect runs the first time.
            environment.captureExclusion.exclude(composeWindow)
            onDispose {
                composeWindow.removeWindowListener(listener)
                environment.dialogParent.frame = null
            }
        }

        DisposableEffect(Unit) {
            val idle = IdleLock(environment.timer, graph.autoLockPolicy, lockNow = commands::lockNow)
            val listener = AWTEventListener { idle.touch() }
            Toolkit.getDefaultToolkit().addAWTEventListener(listener, INPUT_EVENTS)
            idle.start()
            onDispose {
                idle.stop()
                Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
            }
        }

        LaunchedEffect(state) {
            snapshotFlow { Triple(state.size, state.position, state.placement) }
                .debounce(SAVE_DEBOUNCE_MILLIS)
                .collect {
                    val maximised = state.placement == WindowPlacement.Maximized
                    val bounds = composeWindow.bounds.let { WindowBounds(it.x, it.y, it.width, it.height) }
                    store.save(bounds, maximised)
                }
        }

        LaunchedEffect(Unit) {
            installOsHandlers(commands) { files -> graph.dropRouter.drop(files) }
            if (launchFiles.isNotEmpty()) {
                // Let the UI start and, with a vault, the lock screen come up; then hand the files over.
                delay(FIRST_FRAME_MILLIS)
                graph.sessionLockManager.locked.first { !it }
                graph.dropRouter.drop(launchFiles)
            }
        }

        EnclyMenuBar(os, shortcuts, commands)

        val appWindow = remember(composeWindow) {
            DesktopAppWindow(environment.captureExclusion, windowProvider = { composeWindow })
        }
        Box(Modifier.fillMaxSize().dropFiles(graph.dropRouter::drop)) {
            DesktopApp(graph, appWindow, shortcuts = shortcuts)
        }
    }
}

private const val INPUT_EVENTS: Long = AWTEvent.KEY_EVENT_MASK or
    AWTEvent.MOUSE_EVENT_MASK or
    AWTEvent.MOUSE_MOTION_EVENT_MASK or
    AWTEvent.MOUSE_WHEEL_EVENT_MASK

/** Files dropped on the window go to [onDrop], which answers whether it took any. */
private fun Modifier.dropFiles(onDrop: (List<Path>) -> Boolean): Modifier = dragAndDropTarget(
    shouldStartDragAndDrop = { event -> event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) },
    target = object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            val transferable = event.awtTransferable
            if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
            val files = (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                .orEmpty()
                .filterIsInstance<File>()
                .map(File::toPath)
            return onDrop(files)
        }
    },
)

/** The OS's own hooks: the About and Preferences items of the macOS app menu, and files opened with the app. */
private fun installOsHandlers(commands: DesktopCommands, onFiles: (List<Path>) -> Boolean) {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    runCatching { if (desktop.isSupported(Desktop.Action.APP_ABOUT)) desktop.setAboutHandler { commands.about() } }
    runCatching {
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) desktop.setPreferencesHandler { commands.settings() }
    }
    runCatching {
        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler { event -> onFiles(event.files.map(File::toPath)) }
        }
    }
}

private fun loadWindowIcon(): Painter? {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("encly-icon.png") ?: return null
    return stream.use { BitmapPainter(loadImageBitmap(it)) }
}

private fun screenBounds(): List<WindowBounds> = runCatching {
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map {
        it.defaultConfiguration.bounds.let { b -> WindowBounds(b.x, b.y, b.width, b.height) }
    }
}.getOrDefault(emptyList())

/**
 * The menu bar: the native one on macOS, the window's on Windows and Linux. No Edit menu: the text fields handle
 * cut, copy, paste and undo from the keyboard themselves, and a menu item with the same shortcut would take the
 * key from them on macOS.
 */
@Composable
private fun FrameWindowScope.EnclyMenuBar(os: DesktopOs, shortcuts: EnclyShortcuts, commands: DesktopCommands) {
    MenuBar {
        FileMenu(os, shortcuts, commands)
        ViewMenu(commands)
        Menu(stringResource(Res.string.menu_window)) {
            Item(
                stringResource(Res.string.menu_minimize),
                shortcut = DesktopShortcuts.minimise(os).toKeyShortcut(),
                onClick = commands::minimise,
            )
        }
        Menu(stringResource(Res.string.menu_help)) {
            Item(stringResource(Res.string.about), onClick = commands::about)
            Item(stringResource(Res.string.licenses_title), onClick = commands::licences)
            Item(stringResource(Res.string.main_drawer_faq), onClick = commands::faq)
        }
    }
}

@Composable
private fun MenuBarScope.FileMenu(os: DesktopOs, shortcuts: EnclyShortcuts, commands: DesktopCommands) {
    Menu(stringResource(Res.string.menu_file)) {
        Item(
            stringResource(Res.string.note_add),
            shortcut = DesktopShortcuts.of(shortcuts, AppCommand.NewNote).toKeyShortcut(),
            onClick = commands::newNote,
        )
        Item(
            stringResource(Res.string.import_title),
            shortcut = DesktopShortcuts.importNotes(os).toKeyShortcut(),
            onClick = commands::importNotes,
        )
        Item(
            stringResource(Res.string.backup_title),
            shortcut = DesktopShortcuts.backup(os).toKeyShortcut(),
            onClick = commands::backup,
        )
        Separator()
        Item(
            stringResource(Res.string.lock_now),
            shortcut = DesktopShortcuts.of(shortcuts, AppCommand.LockApp).toKeyShortcut(),
            onClick = commands::lockNow,
        )
        Item(
            stringResource(Res.string.main_drawer_settings),
            shortcut = DesktopShortcuts.settings(os).toKeyShortcut(),
            onClick = commands::settings,
        )
        // macOS has Quit in the application menu (Cmd+Q); the other systems have it here.
        if (os != DesktopOs.MACOS) {
            Separator()
            Item(
                stringResource(Res.string.menu_exit),
                shortcut = DesktopShortcuts.quit(os).toKeyShortcut(),
                onClick = commands::quit,
            )
        }
    }
}

@Composable
private fun MenuBarScope.ViewMenu(commands: DesktopCommands) {
    Menu(stringResource(Res.string.menu_view)) {
        Menu(stringResource(Res.string.theme)) {
            Item(stringResource(Res.string.theme_type_system)) { commands.setTheme(ThemeType.SYSTEM) }
            Item(stringResource(Res.string.theme_type_light)) { commands.setTheme(ThemeType.LIGHT) }
            Item(stringResource(Res.string.theme_type_dark)) { commands.setTheme(ThemeType.DARK) }
        }
        Menu(stringResource(Res.string.settings_language)) {
            AppLanguage.entries.forEach { language ->
                Item(stringResource(language.nativeName)) { commands.setLanguage(language) }
            }
        }
    }
}
