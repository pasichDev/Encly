package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.di.IoDispatcher
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.domain.repository.NotesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The notes of the main list (not trashed, not archived) or of the archive, sorted with the
 * pinned ones first, with their previews. Parsing every note's blocks happens here,
 * on [dispatcher], once per database change: never on the main thread and never per keystroke
 * or recomposition. A read failure fails the flow; the screen turns it into an error state.
 */
class ObserveNotesUseCase @Inject constructor(
    private val repository: NotesRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /**
     * @param tagId the tag to show, or null for every note whose tag is not hidden
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
            else -> repository.getNotesByTagId(tagId)
        }
        return notes.map { list -> list.toSortedItems(sortOption) }.flowOn(dispatcher)
    }

    /** A note whose tag is hidden stays out of the lists that show every note. */
    private fun NoteWithTag.isShown(): Boolean = tag == null || tag.isVisible

    private fun List<NoteWithTag>.toSortedItems(sortOption: NoteSortOption): List<NoteListItem> {
        // Pinned first whatever the sort; the sort orders within each group.
        val comparator = compareByDescending<Note> { it.isPinned }.then(sortOption.comparator())
        return sortedWith { a, b -> comparator.compare(a.note, b.note) }.map(NoteListItem::of)
    }
}
