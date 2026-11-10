package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.domain.enums.NoteSortOption

/**
 * Decides when the notes list must jump back to its top. A lazy list keeps its position by the
 * key of the first visible note, so after a new sort order or a new note it would stay on a
 * note further down, with the newest notes hidden above the screen.
 */
internal class NotesScrollToTop {
    private var lastSort: NoteSortOption? = null
    private var lastIds: Set<Long>? = null

    /**
     * Records the notes now shown ([ids], sorted by [sort]); true when the list has to scroll to
     * its top: the sort order changed, or a note appeared that was not shown before. The first
     * load keeps the position the list restores.
     */
    fun onNotesLoaded(sort: NoteSortOption, ids: List<Long>): Boolean {
        val previousIds = lastIds
        val sortChanged = lastSort != null && lastSort != sort
        lastSort = sort
        lastIds = ids.toSet()
        if (previousIds == null) return false
        return sortChanged || ids.any { it !in previousIds }
    }
}
