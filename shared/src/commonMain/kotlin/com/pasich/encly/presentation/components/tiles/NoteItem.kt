package com.pasich.encly.presentation.components.tiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.duplicate
import com.pasich.encly.core.resources.note_archive
import com.pasich.encly.core.resources.note_color_title
import com.pasich.encly.core.resources.note_export_pdf
import com.pasich.encly.core.resources.note_open
import com.pasich.encly.core.resources.note_open_in_new_tab
import com.pasich.encly.core.resources.note_open_in_new_window
import com.pasich.encly.core.resources.note_pin
import com.pasich.encly.core.resources.note_preview_unreadable
import com.pasich.encly.core.resources.note_print
import com.pasich.encly.core.resources.note_unarchive
import com.pasich.encly.core.resources.note_unpin
import com.pasich.encly.core.resources.swipe_choice_delete
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.presentation.designsystem.EnclyContextMenu
import com.pasich.encly.presentation.designsystem.EnclyDesktopIcons
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyNoteCard
import com.pasich.encly.presentation.designsystem.MenuEntry
import com.pasich.encly.presentation.designsystem.NoteCardMeta
import com.pasich.encly.presentation.dialogs.NoteAction
import com.pasich.encly.presentation.effects.ContainerEnd
import com.pasich.encly.presentation.effects.rememberNoteContainerModifier
import com.pasich.encly.utils.NotesTextFormatter
import com.pasich.encly.utils.formatCardDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * A note in a list: the design-system note card with its tag and date. Long-press opens the
 * note's actions. [dimmed] is the trash look; a checked note ([Note.isChecked]) is shown selected.
 * With [contextActions] a right click (or the Menu key) on the card opens a menu of the same actions;
 * a finger never opens it, and the trash ([dimmed]) has none. [current] marks the note open beside the list,
 * and [onCommandClick] is a click with the command key held (open in a new tab).
 */
@Composable
fun NoteItem(
    onItemClick: (Note) -> Unit,
    onItemLongClick: (Note) -> Unit,
    modifier: Modifier = Modifier,
    item: NoteListItem? = null,
    itemNote: Note? = null,
    excerptLines: Int = 2,
    highlight: String = "",
    dimmed: Boolean = false,
    contextActions: NoteContextActions? = null,
    current: Boolean = false,
    onCommandClick: ((Note) -> Unit)? = null,
) {
    val note: Note = item?.note ?: itemNote ?: Note()
    val preview = rememberNotePreview(item, note) ?: stringResource(Res.string.note_preview_unreadable)
    val hapticFeedback = LocalHapticFeedback.current

    val menu = noteMenuEntries(note, contextActions)
    EnclyContextMenu(entries = { menu }, enabled = contextActions != null && !dimmed) {
        EnclyNoteCard(
            title = note.title,
            excerpt = preview,
            meta = NoteCardMeta(
                tag = item?.tag?.nameTag,
                date = note.date.takeIf { it > 0L }?.let { formatCardDate(Instant.fromEpochMilliseconds(it)) },
            ),
            excerptLines = excerptLines,
            highlight = highlight,
            selected = note.isChecked,
            dimmed = dimmed,
            pinned = note.isPinned && !dimmed,
            noteColor = if (dimmed) NoteColor.NONE else NoteColor.fromKey(note.color),
            current = current,
            onCommandClick = onCommandClick?.let { { it(note) } },
            onClick = { onItemClick(note) },
            onLongClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onItemLongClick(note)
            },
            // The trash opens its notes read-only, with the plain screen motion: no card to grow.
            modifier = modifier.then(rememberNoteContainerModifier(if (dimmed) 0L else note.id, ContainerEnd.CARD)),
        )
    }
}

/** Open, open in a new tab or window, pin, archive, the next colour, duplicate, PDF, print and move to trash. */
@Composable
private fun noteMenuEntries(note: Note, actions: NoteContextActions?): List<MenuEntry> {
    if (actions == null) return emptyList()
    val nextColor = NoteColor.fromKey(note.color).next()
    val open = stringResource(Res.string.note_open)
    val newTab = stringResource(Res.string.note_open_in_new_tab)
    val newWindow = stringResource(Res.string.note_open_in_new_window)
    val pdf = stringResource(Res.string.note_export_pdf)
    val print = stringResource(Res.string.note_print)
    val pin = stringResource(if (note.isPinned) Res.string.note_unpin else Res.string.note_pin)
    val archive = stringResource(if (note.isArchived) Res.string.note_unarchive else Res.string.note_archive)
    val color = "${stringResource(Res.string.note_color_title)}: ${stringResource(nextColor.labelRes)}"
    val duplicate = stringResource(Res.string.duplicate)
    val trash = stringResource(Res.string.swipe_choice_delete)
    return buildList {
        add(MenuEntry(open, { actions.onAction(NoteAction.Edit) }, EnclyIcons.Edit))
        actions.onOpenInNewTab?.let { add(MenuEntry(newTab, it, EnclyIcons.Plus)) }
        actions.onOpenInNewWindow?.let { add(MenuEntry(newWindow, it, EnclyDesktopIcons.Window)) }
        if (!note.isArchived) {
            add(
                MenuEntry(
                    pin,
                    { actions.onAction(NoteAction.Pin(!note.isPinned)) },
                    if (note.isPinned) EnclyIcons.PinOff else EnclyIcons.Pin,
                ),
            )
        }
        add(
            MenuEntry(
                archive,
                { actions.onAction(NoteAction.Archive(!note.isArchived)) },
                if (note.isArchived) EnclyIcons.Unarchive else EnclyIcons.Archive,
            ),
        )
        add(MenuEntry(color, { actions.onAction(NoteAction.ChangeColor(nextColor)) }, EnclyIcons.Palette))
        add(MenuEntry(duplicate, { actions.onAction(NoteAction.Duplicate) }, EnclyIcons.Duplicate))
        actions.onExportPdf?.let { add(MenuEntry(pdf, it, EnclyIcons.Paper)) }
        actions.onPrint?.let { add(MenuEntry(print, it, EnclyDesktopIcons.Print)) }
        add(MenuEntry(trash, { actions.onAction(NoteAction.Delete) }, EnclyIcons.Trash, destructive = true))
    }
}

/**
 * The note list passes a preview built off the main thread; a bare note (the trash) is parsed
 * here, also off the main thread. Null means unreadable content.
 */
@Composable
private fun rememberNotePreview(item: NoteListItem?, note: Note): String? {
    if (item != null) return item.preview
    val parsed by produceState<String?>(initialValue = note.description, note.description, note.value) {
        value = withContext(Dispatchers.Default) {
            note.description.ifEmpty { NotesTextFormatter.jsonToPlainText(note.value) }
        }
    }
    return parsed
}
