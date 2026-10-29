package com.pasich.encly.presentation.components.swipe

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.note_archive
import com.pasich.encly.core.resources.note_pin
import com.pasich.encly.core.resources.note_unarchive
import com.pasich.encly.core.resources.note_unpin
import com.pasich.encly.core.resources.swipe_choice_color
import com.pasich.encly.core.resources.swipe_choice_delete
import com.pasich.encly.core.resources.swipe_choice_none
import com.pasich.encly.core.resources.swipe_choice_pin
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.model.SwipeAction
import org.jetbrains.compose.resources.StringResource

/** The name of [this] action in the settings choice. */
fun SwipeAction.choiceLabel(): StringResource = when (this) {
    SwipeAction.PIN -> Res.string.swipe_choice_pin
    SwipeAction.ARCHIVE -> Res.string.note_archive
    SwipeAction.NEXT_COLOR -> Res.string.swipe_choice_color
    SwipeAction.DELETE -> Res.string.swipe_choice_delete
    SwipeAction.NONE -> Res.string.swipe_choice_none
}

/** What [this] action does to [note] right now, as a label ("Unpin" for a pinned note). */
fun SwipeAction.noteLabel(note: Note): StringResource = when (this) {
    SwipeAction.PIN -> if (note.isPinned) Res.string.note_unpin else Res.string.note_pin
    SwipeAction.ARCHIVE -> if (note.isArchived) Res.string.note_unarchive else Res.string.note_archive
    else -> choiceLabel()
}
