package com.pasich.encly.presentation.components.tiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.presentation.designsystem.EnclyNoteCard
import com.pasich.encly.presentation.designsystem.NoteCardMeta
import com.pasich.encly.utils.NotesTextFormatter
import com.pasich.encly.utils.formatCardDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * A note in a list: the design-system note card with its tag and date. Long-press opens the
 * note's actions. [dimmed] is the trash look; a checked note ([Note.isChecked]) is shown selected.
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
) {
    val note: Note = item?.note ?: itemNote ?: Note()
    val preview = rememberNotePreview(item, note) ?: stringResource(R.string.note_preview_unreadable)
    val hapticFeedback = LocalHapticFeedback.current

    EnclyNoteCard(
        title = note.title,
        excerpt = preview,
        meta = NoteCardMeta(
            tag = item?.tag?.nameTag,
            date = note.date.takeIf { it > 0L }?.let { formatCardDate(Date(it)) },
        ),
        excerptLines = excerptLines,
        highlight = highlight,
        selected = note.isChecked,
        dimmed = dimmed,
        onClick = { onItemClick(note) },
        onLongClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onItemLongClick(note)
        },
        modifier = modifier,
    )
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
