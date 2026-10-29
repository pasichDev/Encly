package com.pasich.encly.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.swipe_delete_message
import com.pasich.encly.core.resources.swipe_delete_title
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.presentation.components.swipe.SwipeContext
import com.pasich.encly.presentation.components.tiles.NoteContextActions
import com.pasich.encly.presentation.components.tiles.NoteItem
import com.pasich.encly.presentation.dialogs.ConfirmDialog
import com.pasich.encly.presentation.dialogs.NoteAction
import com.pasich.encly.presentation.viewmodel.NoteListEvent
import com.pasich.encly.ui.theme.LocalPointerFirst
import org.jetbrains.compose.resources.stringResource

/** The cards to show and what a tap or long-press on one does. */
internal class NotesContent(
    val entries: List<NoteEntry>,
    val highlight: String,
    val onItemClick: (Note) -> Unit,
    val onItemLongClick: (NoteListItem) -> Unit,
    val swipe: SwipeContext,
    val pointer: NotesPointer,
)

/** What a mouse adds to a card: the right-click menu, and the tabs' marks and shortcuts. Null: touch only. */
internal class NotesPointer(val tabs: NotesTabs?, val actionsFor: ((Note) -> NoteContextActions)?)

/** The card of [entry] in the list or the grid; the same call for both. */
@Composable
internal fun NotesCard(entry: NoteRow, content: NotesContent, excerptLines: Int, modifier: Modifier = Modifier) {
    val tabs = content.pointer.tabs
    NoteItem(
        item = entry.item,
        highlight = content.highlight,
        excerptLines = excerptLines,
        modifier = modifier,
        onItemClick = content.onItemClick,
        onItemLongClick = { content.onItemLongClick(entry.item) },
        contextActions = content.pointer.actionsFor?.invoke(entry.item.note),
        current = tabs?.activeNoteId?.invoke() == entry.item.note.id,
        onCommandClick = tabs?.onOpenInNewTab,
    )
}

/** What a right click on a card does beyond the tabs: open, duplicate, and the list's own events. */
internal class NoteMenuHandlers(
    val onOpen: (Note) -> Unit,
    val onDuplicate: (Note) -> Unit,
    val onEvent: (NoteListEvent) -> Unit,
)

/**
 * The mouse side of the list: the right-click menu on a pointer-first device (a finger keeps the long-press
 * sheet), whose "Move to trash" asks first like the swipe does, and the [tabs] if the host has any.
 */
@Composable
internal fun rememberNotesPointer(tabs: NotesTabs?, handlers: NoteMenuHandlers): NotesPointer {
    var trashing by remember { mutableStateOf<Note?>(null) }
    ConfirmDialog(
        isVisible = trashing != null,
        titleText = stringResource(Res.string.swipe_delete_title),
        messageText = stringResource(Res.string.swipe_delete_message),
        onConfirm = {
            trashing?.let { handlers.onEvent(NoteListEvent.NoteToTrash(it)) }
            trashing = null
        },
        onDismiss = { trashing = null },
        destructive = true,
    )
    if (!LocalPointerFirst.current) return NotesPointer(tabs, actionsFor = null)
    return NotesPointer(tabs) { note ->
        NoteContextActions(
            onAction = { action ->
                when (action) {
                    NoteAction.Edit -> handlers.onOpen(note)
                    NoteAction.Duplicate -> handlers.onDuplicate(note)
                    NoteAction.Delete -> trashing = note
                    else -> action.toEvent(note)?.let(handlers.onEvent)
                }
            },
            onOpenInNewTab = tabs?.let { { it.onOpenInNewTab(note) } },
        )
    }
}

/** The list event a sheet [NoteAction] on [note] stands for; null for the ones that open another screen. */
internal fun NoteAction.toEvent(note: Note): NoteListEvent? = when (this) {
    NoteAction.Edit, NoteAction.Duplicate -> null
    NoteAction.Delete -> NoteListEvent.NoteToTrash(note)
    is NoteAction.Pin -> NoteListEvent.PinNote(note, pinned)
    is NoteAction.Archive -> NoteListEvent.ArchiveNote(note, archived)
    is NoteAction.ChangeColor -> NoteListEvent.ChangeColor(note, color)
    is NoteAction.ChangeTag -> NoteListEvent.ChangeTag(note = note, idTag = tagId)
    is NoteAction.ChangeDescription -> NoteListEvent.ChangeDescription(note = note, description = description)
}
