package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.di.IoDispatcher
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.domain.repository.NotesRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The notes of the main list (not trashed, not archived) or of the archive, sorted with the
 * pinned ones first, with their previews. Parsing every note's blocks happens here,
 * on [dispatcher], once per database change: never on the main thread and never per keystroke
 * or recomposition. A read failure fails the flow; the screen turns it into an error state.
 */
@Inject
class ObserveNotesUseCase(
    private val repository: NotesRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /**
     * @param tagId the tag to show, or null for every note without a hidden tag
     * @param archived the archive instead of the main list ([tagId] does not apply to it)
     */
    operator fun invoke(
        tagId: Long?,
        sortOption: NoteSortOption,
        archived: Boolean = false,
    ): Flow<List<NoteListItem>> {
        val notes = when {
            archived -> repository.getArchivedNotesWithTag().map { all -> all.filter { it.isShown() } }

            tagId == null -> repository.getAllNotesWithTag().map { all -> all.filter { it.isShown() } }

            // A hidden tag's archived notes are in no other list (the archive leaves hidden tags out
            // too), so its own view is the way to unarchive them. A visible tag's are in the archive.
            // One read for both: two flows combined could hold a note that is being archived twice.
            // A note that also has a hidden tag stays out of a visible tag's view: a hidden tag keeps its
            // notes out of every list but the hidden tags' own.
            else -> repository.getNotesOfTagWithArchived(tagId).map { all ->
                all.filter { item ->
                    val viewHidden = item.tags.any { it.id == tagId && !it.isVisible }
                    if (viewHidden) true else item.isShown() && !item.note.isArchived
                }
            }
        }
        return notes.map { list -> list.toSortedItems(sortOption) }.flowOn(dispatcher)
    }

    /** The note as stored now, following every change to it; null once it is gone. */
    fun observeNote(noteId: Long): Flow<NoteWithTags?> = repository.observeNote(noteId)

    /** A note with any hidden tag stays out of the lists that show every note. */
    private fun NoteWithTags.isShown(): Boolean = tags.all { it.isVisible }

    private fun List<NoteWithTags>.toSortedItems(sortOption: NoteSortOption): List<NoteListItem> {
        // Pinned first whatever the sort, archived notes (a hidden tag's view) last; the sort orders within.
        val comparator = compareBy<Note> { it.isArchived }.thenByDescending { it.isPinned }
            .then(sortOption.comparator())
        return sortedWith { a, b -> comparator.compare(a.note, b.note) }.map(NoteListItem::of)
    }
}
