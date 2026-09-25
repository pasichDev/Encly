package com.pasich.encly.domain.usecase.note

import com.pasich.encly.core.di.IoDispatcher
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
 * The non-trashed notes, sorted, with their previews. Parsing every note's blocks happens here,
 * on [dispatcher], once per database change: never on the main thread and never per keystroke
 * or recomposition. A read failure fails the flow; the screen turns it into an error state.
 */
class ObserveNotesUseCase @Inject constructor(
    private val repository: NotesRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /**
     * @param tagId the tag to show, or null for every note whose tag is not hidden
     */
    operator fun invoke(tagId: Long?, sortOption: NoteSortOption): Flow<List<NoteListItem>> {
        val notes = if (tagId == null) {
            repository.getAllNotesWithTag().map { all -> all.filter { it.tag == null || it.tag.isVisible } }
        } else {
            repository.getNotesByTagId(tagId)
        }
        return notes.map { list -> list.toSortedItems(sortOption) }.flowOn(dispatcher)
    }

    private fun List<NoteWithTag>.toSortedItems(sortOption: NoteSortOption): List<NoteListItem> {
        val comparator = sortOption.comparator()
        return sortedWith { a, b -> comparator.compare(a.note, b.note) }.map(NoteListItem::of)
    }
}
