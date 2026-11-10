package com.pasich.encly.desktop.window

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.desktop.storage.DesktopOs
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.platform.AppLocaleController
import com.pasich.encly.platform.NavigationRequests
import com.pasich.encly.presentation.designsystem.AppCommand
import com.pasich.encly.presentation.designsystem.EnclyShortcuts
import com.pasich.encly.presentation.navigation.NavRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the menu bar (and later the keyboard shortcuts and the tray) can ask the app to do. The window host
 * wires menu items to this and nothing else, so the screens are not edited for them: a command either calls
 * the session ([lockNow]), a setting, or asks the UI to open a screen through [NavigationRequests].
 *
 * Commands that open a screen are ignored while the vault is locked (see `HostNavigationRequests`): no menu
 * item skips the lock screen. A new note is asked for as the [AppCommand] it is, so it opens the way the
 * keyboard shortcut and the button open it (as a tab, in a wide window). Every window has its own commands over
 * its own [NavigationRequests], so a menu item acts on the window it belongs to (the focused one).
 */
@Suppress("TooManyFunctions") // One function per item of the menu bar.
interface DesktopCommands {
    fun newNote()

    /** Another window on the same vault. */
    fun newWindow()

    /** Print the note open in this window (nothing if none is open). */
    fun printNote()

    /** Save the note open in this window as a PDF (nothing if none is open). */
    fun exportPdf()

    fun importNotes()

    /** Backup, restore and the Markdown export all live on the backup screen. */
    fun backup()

    fun settings()

    fun about()

    fun licences()

    fun faq()

    fun lockNow()

    fun setLanguage(language: AppLanguage)

    fun setTheme(type: ThemeType)

    fun minimise()

    fun quit()
}

/** [DesktopCommands] over the graph's services. */
@Suppress("TooManyFunctions") // One function per item of the menu bar.
class DefaultDesktopCommands(
    private val navigation: NavigationRequests,
    private val lockSession: () -> Unit,
    private val locales: AppLocaleController,
    private val saveThemeType: suspend (ThemeType) -> Unit,
    private val scope: CoroutineScope,
    private val onMinimise: () -> Unit,
    private val onQuit: () -> Unit,
    private val openWindow: () -> Unit = {},
) : DesktopCommands {
    override fun newNote() {
        navigation.request(AppCommand.NewNote)
    }

    override fun newWindow() = openWindow()

    override fun printNote() {
        navigation.request(AppCommand.PrintNote)
    }

    override fun exportPdf() {
        navigation.request(AppCommand.ExportPdf)
    }

    override fun importNotes() = open(NavRoutes.ImportNotesRoute.name)

    override fun backup() = open(NavRoutes.BackupRoute.name)

    override fun settings() = open(NavRoutes.SettingsRoute.name)

    override fun about() = open(NavRoutes.AboutRoute.name)

    override fun licences() = open(NavRoutes.LicensesRoute.name)

    override fun faq() = open(NavRoutes.FaqRoute.name)

    override fun lockNow() = lockSession()

    override fun setLanguage(language: AppLanguage) = locales.apply(language)

    override fun setTheme(type: ThemeType) {
        scope.launch { saveThemeType(type) }
    }

    override fun minimise() = onMinimise()

    override fun quit() = onQuit()

    private fun open(route: String) {
        navigation.request(route)
    }
}

/** A key chord of the menu bar. Compose's own `KeyShortcut` hides its fields, so the map is kept in this. */
data class Accelerator(
    val key: Key,
    val ctrl: Boolean = false,
    val meta: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
) {
    /** The shortcut a menu item takes. */
    fun toKeyShortcut(): KeyShortcut = KeyShortcut(key = key, ctrl = ctrl, meta = meta, shift = shift, alt = alt)
}

/**
 * The accelerators the menu bar registers, and who owns which chord. The key map of the window is
 * [EnclyShortcuts] (one instance, [map], shared by the menu and the keyboard host). A command that map knows is
 * either the menu's ([menuOwned]: the menu registers its chord, built from the map's own key, and the host
 * declines it) or the host's (the menu registers nothing for it), never both: on a Mac the menu bar is native and
 * takes its chords before any key reaches the window, so a chord both sides took would run twice or not at all.
 * The chords below that are not in the map (new window, print, PDF, import, backup, settings, minimise, quit) are the
 * menu's alone: none of them is a chord the map answers, so the host never runs one.
 */
object DesktopShortcuts {
    /** The commands whose accelerators the menu registers; the keyboard host declines them. */
    val menuOwned: Set<AppCommand> = setOf(AppCommand.NewNote, AppCommand.LockApp)

    /** The key map of a window on [os]: Cmd is the command key on macOS, Ctrl everywhere else. */
    fun map(os: DesktopOs): EnclyShortcuts = EnclyShortcuts(commandKeyIsMeta = os == DesktopOs.MACOS)

    fun primary(os: DesktopOs, key: Key, shift: Boolean = false): Accelerator =
        Accelerator(key = key, ctrl = os != DesktopOs.MACOS, meta = os == DesktopOs.MACOS, shift = shift)

    /** The accelerator of [command] from the window's key map; the command must be one of its letter chords. */
    fun of(shortcuts: EnclyShortcuts, command: AppCommand): Accelerator {
        val key = requireNotNull(shortcuts.keyOf(command)) { "$command has no letter chord" }
        return Accelerator(key = key, ctrl = !shortcuts.usesMetaKey, meta = shortcuts.usesMetaKey)
    }

    fun newWindow(os: DesktopOs) = primary(os, Key.N, shift = true)

    fun printNote(os: DesktopOs) = primary(os, Key.P)

    fun exportPdf(os: DesktopOs) = primary(os, Key.P, shift = true)

    fun importNotes(os: DesktopOs) = primary(os, Key.I)

    fun backup(os: DesktopOs) = primary(os, Key.B)

    fun settings(os: DesktopOs) = primary(os, Key.Comma)

    fun minimise(os: DesktopOs) = primary(os, Key.M)

    fun quit(os: DesktopOs) = primary(os, Key.Q)

    /** The chords only the menu has, which the key map does not know. */
    fun menuOnly(os: DesktopOs): List<Accelerator> = listOf(
        newWindow(os),
        printNote(os),
        exportPdf(os),
        importNotes(os),
        backup(os),
        settings(os),
        minimise(os),
        quit(os),
    )
}
