package com.pasich.encly.presentation.components.swipe

import androidx.annotation.StringRes
import com.pasich.encly.R
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.model.SwipeAction

/** The name of [this] action in the settings choice. */
@StringRes
fun SwipeAction.choiceLabel(): Int = when (this) {
    SwipeAction.PIN -> R.string.swipe_choice_pin
    SwipeAction.ARCHIVE -> R.string.note_archive
    SwipeAction.NEXT_COLOR -> R.string.swipe_choice_color
    SwipeAction.DELETE -> R.string.swipe_choice_delete
    SwipeAction.NONE -> R.string.swipe_choice_none
}

/** What [this] action does to [note] right now, as a label ("Unpin" for a pinned note). */
@StringRes
fun SwipeAction.noteLabel(note: Note): Int = when (this) {
    SwipeAction.PIN -> if (note.isPinned) R.string.note_unpin else R.string.note_pin
    SwipeAction.ARCHIVE -> if (note.isArchived) R.string.note_unarchive else R.string.note_archive
    else -> choiceLabel()
}
