package com.pasich.encly.desktop.window

import androidx.compose.runtime.mutableStateListOf
import com.pasich.encly.desktop.platform.DesktopDocumentPickers
import com.pasich.encly.desktop.platform.DesktopEnvironment
import com.pasich.encly.desktop.platform.DropRouter
import com.pasich.encly.desktop.platform.GatedFileDialogs
import com.pasich.encly.desktop.security.DesktopAppLifecycle
import com.pasich.encly.platform.NavigationRequests
import com.pasich.encly.platform.NoteEditClaims
import com.pasich.encly.platform.NoteEditRegistry
import com.pasich.encly.platform.NoteWindows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What belongs to one window and must not be shared between windows: the requests the menu sends it, the files
 * dropped on it (and the screens waiting for them), and the file dialogs those screens open. The vault, the lock,
 * the settings and the other services of the graph are the process's, one for all windows.
 */
class WindowServices(
    val navigation: NavigationRequests,
    val dropRouter: DropRouter,
    val documentPickers: DesktopDocumentPickers,
    /** "Open in new window" for the screens of this window; null in a host with one window. */
    val noteWindows: NoteWindows? = null,
    /** This window's claims on the notes it edits; null where nothing shares a vault between windows. */
    val claims: NoteEditClaims? = null,
) {
    companion object {
        /** The services of a window of [environment]. */
        fun create(
            environment: DesktopEnvironment,
            lifecycle: DesktopAppLifecycle,
            noteWindows: NoteWindows? = null,
            claims: NoteEditClaims? = null,
        ): WindowServices {
            val navigation = NavigationRequests()
            val drops = DropRouter(navigation)
            val pickers = DesktopDocumentPickers(GatedFileDialogs(environment.fileDialogs, lifecycle), drops)
            return WindowServices(navigation, drops, pickers, noteWindows, claims)
        }
    }
}

/** One open window: its [id], its [services] and, for a window opened on a note, the note's id. */
class WindowSession(val id: Int, val services: WindowServices, val openNoteId: Long? = null) {
    /** What the menu of this window does; set by the window host once its window exists. */
    @Volatile
    var commands: DesktopCommands? = null
}

/**
 * The open windows of the app and which one has the focus. Every window shows the same vault behind the same lock
 * (one `SessionLockManager`): this class only decides which windows exist.
 *
 * Closing the last window ends the app ([onLastClosed]); a window cannot be opened before the vault exists
 * ([canOpen]: not during onboarding or when the vault is damaged).
 */
class DesktopWindows(
    private val canOpen: () -> Boolean,
    private val onLastClosed: () -> Unit,
    private val create: (id: Int, windows: DesktopWindows) -> WindowServices,
) {
    private var nextId = 1
    private val open = mutableStateListOf<WindowSession>()

    /** The vault's note claims, one registry for all windows. */
    val registry = NoteEditRegistry()

    /** The windows, in the order they were opened. Observable. */
    val sessions: List<WindowSession> get() = open

    /** The window that was active last: where the menu bar's commands and the OS's requests go. */
    var focusedId: Int? = null
        private set

    val focused: WindowSession? get() = open.firstOrNull { it.id == focusedId } ?: open.lastOrNull()

    /** Opens a window, on note [noteId] if given. Null when the vault does not allow a window yet. */
    fun open(noteId: Long? = null): WindowSession? {
        // The first window is the app itself; a further one needs a vault that exists.
        if (open.isNotEmpty() && !canOpen()) return null
        val id = nextId++
        val session = WindowSession(id, create(id, this), noteId)
        open.add(session)
        focusedId = id
        return session
    }

    /** The window [id] was closed. Closing the last window ends the app. */
    fun close(id: Int) {
        val index = open.indexOfFirst { it.id == id }
        if (index < 0) return
        open.removeAt(index)
        if (focusedId == id) focusedId = open.lastOrNull()?.id
        if (open.isEmpty()) onLastClosed()
    }

    /** Closes every window at once (Quit): the app ends, once. */
    fun closeAll() {
        if (open.isEmpty()) return
        open.clear()
        focusedId = null
        onLastClosed()
    }

    /** The window [id] became the active one. */
    fun focus(id: Int) {
        if (open.any { it.id == id }) focusedId = id
    }

    /** Whether [id] is one of the open windows. */
    fun isOpen(id: Int): Boolean = open.any { it.id == id }
}

/** The AWT frames of the app's windows, to tell the focus moving between them from the app being left. */
class WindowFrames {
    private val frames = mutableSetOf<java.awt.Window>()

    fun add(frame: java.awt.Window) {
        frames += frame
    }

    fun remove(frame: java.awt.Window) {
        frames -= frame
    }

    /** Whether [frame] is one of the app's windows; a null (the focus left the app) is not. */
    operator fun contains(frame: java.awt.Window?): Boolean = frame != null && frame in frames
}

/**
 * Tells the app's one lifecycle what the windows together do. The app is in front while any window is active and
 * not every window is minimised. Focus moving from one window of the app to another is not leaving the app: the
 * first window's deactivation names the second as its opposite, and nothing is reported then, so `SessionLockManager`
 * never sees a stop (which, with auto-lock set to "immediately", would lock the vault on every click between windows).
 */
class WindowForeground(private val lifecycle: DesktopAppLifecycle) {
    private val windows = mutableSetOf<Int>()
    private val active = mutableSetOf<Int>()
    private val minimised = mutableSetOf<Int>()

    fun opened(id: Int) {
        windows += id
    }

    fun activated(id: Int) {
        active += id
        report()
    }

    /** Window [id] lost the focus; [toOwnWindow] says the focus went to another window of the app. */
    fun deactivated(id: Int, toOwnWindow: Boolean) {
        active -= id
        if (!toOwnWindow) report()
    }

    fun iconified(id: Int) {
        minimised += id
        report()
    }

    fun deiconified(id: Int) {
        minimised -= id
        report()
    }

    /**
     * Window [id] is gone. It is never reported as the app being left: while other windows remain the focus goes to
     * one of them, and with none left the app is ending, where the vault is locked only after the editors saved
     * (see `awaitPendingWork`), not by the last window disappearing under them.
     */
    fun closed(id: Int) {
        windows -= id
        active -= id
        minimised -= id
        reportMinimised()
    }

    private fun report() {
        if (active.isNotEmpty()) lifecycle.windowActivated() else lifecycle.windowDeactivated()
        reportMinimised()
    }

    private fun reportMinimised() {
        val allMinimised = windows.isNotEmpty() && minimised.containsAll(windows)
        if (allMinimised) lifecycle.windowMinimised() else lifecycle.windowRestored()
    }
}

/**
 * Waits (at most [timeoutMillis]) for the work this scope launched to finish: the last-chance saves of the editors
 * that were just closed run here, and the vault must not be closed under them.
 */
suspend fun CoroutineScope.awaitPendingWork(timeoutMillis: Long) {
    val jobs = coroutineContext[Job]?.children?.toList().orEmpty()
    withTimeoutOrNull(timeoutMillis) { jobs.joinAll() }
}
