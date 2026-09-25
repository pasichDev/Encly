package com.pasich.encly.domain.model

import com.pasich.encly.core.serialization.BlockConverter
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.utils.NotesTextFormatter

/**
 * A note as the note list and search show it. [preview] and [searchText] are derived from the
 * stored blocks once, when the note is loaded, never during composition.
 *
 * @property preview the note's description, or else the plain text of its blocks; null when
 *   the blocks cannot be read (damaged, or written by a newer version)
 * @property searchText what search matches besides the title: the description and the text the
 *   user typed, without list markers or separator lines
 */
data class NoteListItem(val noteWithTag: NoteWithTag, val preview: String?, val searchText: String) {
    val note: Note get() = noteWithTag.note
    val tag: Tag? get() = noteWithTag.tag

    /** Case-insensitive match of [query] against the title, description and content. */
    fun matches(query: String): Boolean =
        note.title.contains(query, ignoreCase = true) || searchText.contains(query, ignoreCase = true)

    companion object {
        /** Parses [noteWithTag]'s blocks once; an unreadable note becomes an item, not a crash. */
        fun of(noteWithTag: NoteWithTag): NoteListItem {
            val note = noteWithTag.note
            val blocks = BlockConverter.jsonToBlocksOrNull(note.value)
            val preview = note.description.ifEmpty { blocks?.let(NotesTextFormatter::blocksToPlainText) }
            val content = blocks?.let(NotesTextFormatter::blocksToSearchText).orEmpty()
            val searchText = listOf(note.description, content).filter { it.isNotEmpty() }.joinToString("\n")
            return NoteListItem(noteWithTag, preview, searchText)
        }
    }
}
