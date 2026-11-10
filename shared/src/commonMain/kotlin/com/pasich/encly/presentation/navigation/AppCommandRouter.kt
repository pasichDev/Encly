package com.pasich.encly.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.pasich.encly.presentation.designsystem.AppCommand
import com.pasich.encly.presentation.pane.NoteTabs
import com.pasich.encly.presentation.pane.cycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the editor on screen does for a command. Each returns whether it did something, so a key that
 * changed nothing is left to whoever else wants it.
 */
class EditorCommands(
    val find: () -> Boolean,
    val moveToTrash: () -> Boolean,
    val dismiss: () -> Boolean,
    val print: () -> Boolean = { false },
    val exportPdf: () -> Boolean = { false },
)

/**
 * Runs the app's [AppCommand]s, whether a keyboard shortcut or a menu item asked: the screens that own the
 * behaviour register it while they are on screen ([attachNewNote], [attachTabs], [attachEditor]) and the router
 * calls the one that is there. So a command works the way the screen's own button does (a new note from the
 * home screen is the FAB's, opened as a tab where there are tabs), and does nothing where no screen has it.
 * The callers decide whether commands are allowed at all (never while the vault is locked).
 */
@Stable
class AppCommandRouter(
    private val scope: CoroutineScope,
    private val lockApp: () -> Unit,
    private val newNoteFallback: () -> Boolean,
) {
    private var newNote: (() -> Unit)? = null
    private var tabs: NoteTabs? = null
    private var editor: EditorCommands? by mutableStateOf(null)

    /** Whether an editor is on screen (a note to print or save as PDF); observable by the host's menu. */
    val hasEditor: Boolean get() = editor != null

    /** The home screen's way of starting a note (the FAB's); a newer one replaces it. */
    fun attachNewNote(action: () -> Unit) {
        newNote = action
    }

    /** The home screen left; a newer one that already took its place stays. */
    fun detachNewNote(action: () -> Unit) {
        if (newNote === action) newNote = null
    }

    /** The tabs of the expanded layout's detail pane. */
    fun attachTabs(tabs: NoteTabs) {
        this.tabs = tabs
    }

    /** The pane left; newer tabs that already took its place stay. */
    fun detachTabs(tabs: NoteTabs) {
        if (this.tabs === tabs) this.tabs = null
    }

    /** The editor that is on screen (in the pane: the one of the shown tab). */
    fun attachEditor(editor: EditorCommands) {
        this.editor = editor
    }

    /** The editor left; a newer one that already took its place stays. */
    fun detachEditor(editor: EditorCommands) {
        if (this.editor === editor) this.editor = null
    }

    /** Runs [command]; true when something was done, so the key that asked for it is used up. */
    fun run(command: AppCommand): Boolean = when (command) {
        AppCommand.NewNote -> startNote()
        AppCommand.FindInNote -> editor?.find?.invoke() == true
        AppCommand.LockApp -> true.also { lockApp() }
        AppCommand.CloseTab -> closeActiveTab()
        AppCommand.NextTab -> switchTab(forward = true)
        AppCommand.PreviousTab -> switchTab(forward = false)
        AppCommand.DeleteNote -> editor?.moveToTrash?.invoke() == true
        AppCommand.Dismiss -> editor?.dismiss?.invoke() == true
        AppCommand.PrintNote -> editor?.print?.invoke() == true
        AppCommand.ExportPdf -> editor?.exportPdf?.invoke() == true
    }

    private fun startNote(): Boolean {
        val start = newNote ?: return newNoteFallback()
        start()
        return true
    }

    private fun closeActiveTab(): Boolean {
        val open = tabs
        val key = open?.activeKey
        if (open == null || key == null) return false
        open.close(key)
        return true
    }

    private fun switchTab(forward: Boolean): Boolean {
        val open = tabs?.takeIf { it.tabs.size > 1 } ?: return false
        scope.launch { open.cycle(forward) }
        return true
    }
}

/** The running app's command router, or null where no host provides one (a screen drawn on its own). */
val LocalAppCommands = staticCompositionLocalOf<AppCommandRouter?> { null }

/** Registers [action] as the home screen's way of starting a note while this is on screen. */
@Composable
internal fun NewNoteCommand(action: () -> Unit) {
    val router = LocalAppCommands.current ?: return
    val current by rememberUpdatedState(action)
    DisposableEffect(router) {
        val registered = { current() }
        router.attachNewNote(registered)
        onDispose { router.detachNewNote(registered) }
    }
}

/** Registers [tabs] as the tabs the tab commands act on while this is on screen. */
@Composable
internal fun TabCommands(tabs: NoteTabs) {
    val router = LocalAppCommands.current ?: return
    DisposableEffect(router, tabs) {
        router.attachTabs(tabs)
        onDispose { router.detachTabs(tabs) }
    }
}

/** Registers [editor] as the editor the note commands act on while this is on screen. */
@Composable
internal fun EditorCommandsBinding(editor: EditorCommands) {
    val router = LocalAppCommands.current ?: return
    DisposableEffect(router, editor) {
        router.attachEditor(editor)
        onDispose { router.detachEditor(editor) }
    }
}
