package com.pasich.encly.desktop.window

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import com.pasich.encly.core.locale.AppLanguage
import com.pasich.encly.desktop.storage.DesktopOs
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.platform.AppLocaleController
import com.pasich.encly.platform.NavigationRequests
import com.pasich.encly.presentation.navigation.NavRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the menu bar (and later the keyboard shortcuts and the tray) can ask the app to do. The window host
 * wires menu items to this and nothing else, so the screens are not edited for them: a command either calls
 * the session ([lockNow]), a setting, or asks the UI to open a screen through [NavigationRequests].
 *
 * Commands that open a screen are ignored while the vault is locked (see `HostNavigationRequests`): no menu
 * item skips the lock screen.
 */
interface DesktopCommands {
    fun newNote()

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
class DefaultDesktopCommands(
    private val navigation: NavigationRequests,
    private val lockSession: () -> Unit,
    private val locales: AppLocaleController,
    private val saveThemeType: suspend (ThemeType) -> Unit,
    private val scope: CoroutineScope,
    private val onMinimise: () -> Unit,
    private val onQuit: () -> Unit,
) : DesktopCommands {
    override fun newNote() = open("${NavRoutes.EditNoteRoute.name}/-1")

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

/** The keyboard shortcuts of the menu: the primary modifier is Cmd on macOS and Ctrl everywhere else. */
object DesktopShortcuts {
    fun primary(os: DesktopOs, key: Key, shift: Boolean = false): KeyShortcut =
        KeyShortcut(key = key, ctrl = os != DesktopOs.MACOS, meta = os == DesktopOs.MACOS, shift = shift)

    fun newNote(os: DesktopOs) = primary(os, Key.N)

    fun importNotes(os: DesktopOs) = primary(os, Key.I)

    fun backup(os: DesktopOs) = primary(os, Key.B)

    fun lock(os: DesktopOs) = primary(os, Key.L)

    fun settings(os: DesktopOs) = primary(os, Key.Comma)

    fun minimise(os: DesktopOs) = primary(os, Key.M)

    fun quit(os: DesktopOs) = primary(os, Key.Q)
}
