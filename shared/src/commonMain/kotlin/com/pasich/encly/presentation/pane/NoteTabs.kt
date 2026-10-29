package com.pasich.encly.presentation.pane

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One open note of the detail pane. [key] identifies the tab for as long as it exists (a new note has no
 * id yet, and two blank notes can be open at once); [noteId] is the stored note's id, or -1 for a note not
 * saved yet; [params] are the editor route's query arguments (a copy source, a tag to start with); [title]
 * is what the tab shows; [epoch] is the vault's erase epoch when the tab was opened (see RelockReturn).
 */
data class NoteTab(
    val key: Long,
    val noteId: Long,
    val params: String = "",
    val title: String = "",
    val epoch: Long? = null,
)

/**
 * What the editor shown in the active tab lets the tabs do: save it ([save], true when it is stored or
 * nothing needed storing) and close it the way its Back does ([requestClose]: save, then leave).
 */
class ActiveEditor(val save: suspend () -> Boolean, val requestClose: () -> Unit)

/**
 * The notes open in the detail pane of the expanded layout, and which one is shown. Only the active tab has
 * an editor (and so its note in memory): leaving a tab saves it, and coming back loads it afresh, so a
 * background tab holds no plaintext beyond its title. Everything here lives in the home screen's ViewModel
 * and goes with it when the vault locks.
 */
@Stable
class NoteTabs {
    private val openTabs = mutableStateListOf<NoteTab>()
    private var nextKey = 1L
    private var editor: ActiveEditor? = null
    private var switching = false

    /** The open tabs, in the order they were opened. */
    val tabs: List<NoteTab> get() = openTabs

    /** The key of the tab shown, null when no note is open. */
    var activeKey: Long? by mutableStateOf(null)
        private set

    /** The tab shown. */
    val active: NoteTab? get() = openTabs.firstOrNull { it.key == activeKey }

    /** Registered by the editor of the active tab while it is composed. */
    fun attach(editor: ActiveEditor) {
        this.editor = editor
    }

    /** The [editor] left the composition; a newer one that already took its place stays. */
    fun detach(editor: ActiveEditor) {
        if (this.editor === editor) this.editor = null
    }

    /**
     * Shows note [noteId] (-1: a new note, with [params] such as a tag to start with): the tab it already
     * has, or a new one. The tab shown until now is saved first; false when that failed, in which case
     * nothing changed.
     */
    suspend fun open(noteId: Long, params: String = "", epoch: Long? = null): Boolean {
        val existing = if (noteId > 0L) openTabs.firstOrNull { it.noteId == noteId } else null
        if (existing != null) return activate(existing.key)
        return switchWith {
            val tab = NoteTab(key = nextKey++, noteId = noteId, params = params, epoch = epoch)
            openTabs.add(tab)
            activeKey = tab.key
        }
    }

    /** Shows the tab [key], saving the one shown; false when that failed or there is no such tab. */
    suspend fun activate(key: Long): Boolean = when {
        key == activeKey -> true
        openTabs.none { it.key == key } -> false
        else -> switchWith { activeKey = key }
    }

    /**
     * Closes the tab [key]. The shown tab is closed the way the editor's Back closes it (it saves, then
     * calls [remove]); a background tab was saved when it was left, so it just goes.
     */
    fun close(key: Long) {
        val shown = editor
        if (key == activeKey && shown != null) shown.requestClose() else remove(key)
    }

    /** Drops the tab [key] without saving (its editor did, or it never had one); the neighbour is shown. */
    fun remove(key: Long) {
        val index = openTabs.indexOfFirst { it.key == key }
        if (index < 0) return
        openTabs.removeAt(index)
        if (key == activeKey) activeKey = openTabs.getOrNull(index - 1)?.key ?: openTabs.firstOrNull()?.key
    }

    /** Replaces the tab [key] by a tab of note [noteId] (a copy made from it), in the same place and shown. */
    fun replace(key: Long, noteId: Long) {
        val index = openTabs.indexOfFirst { it.key == key }
        if (index < 0) return
        val tab = NoteTab(key = nextKey++, noteId = noteId, epoch = openTabs[index].epoch)
        openTabs[index] = tab
        if (key == activeKey) activeKey = tab.key
    }

    /** The editor of the tab [key] says which stored note it holds and what to call it. */
    fun report(key: Long, noteId: Long, title: String) {
        val index = openTabs.indexOfFirst { it.key == key }
        if (index < 0) return
        val tab = openTabs[index]
        // An editor that has not loaded its stored note yet shows a blank new one: that is not what the tab holds.
        if (noteId <= 0L && tab.noteId > 0L) return
        if (tab.noteId != noteId || tab.title != title) openTabs[index] = tab.copy(noteId = noteId, title = title)
    }

    /** Closes every tab without saving: the vault locked and the editors dropped their content. */
    fun clear() {
        openTabs.clear()
        activeKey = null
        editor = null
    }

    // Saves the tab shown and runs [change]; a note that was never stored (a blank new one) leaves no tab.
    private suspend fun switchWith(change: () -> Unit): Boolean {
        if (switching) return false
        switching = true
        try {
            val leaving = active
            val saved = leaving == null || editor?.save?.invoke() != false
            if (saved) {
                // The editor reported the stored id while saving; a note that is still unsaved was blank.
                openTabs.firstOrNull { it.key == leaving?.key && it.noteId <= 0L }?.let(openTabs::remove)
                change()
            }
            return saved
        } finally {
            switching = false
        }
    }
}
