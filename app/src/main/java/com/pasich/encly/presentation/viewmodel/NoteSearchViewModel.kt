package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.common.asLoadState
import com.pasich.encly.core.common.valueOrNull
import com.pasich.encly.core.di.IoDispatcher
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.domain.usecase.note.ObserveNotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Searches every note that is not in the trash and whose tag is not hidden, whatever tag chip
 * the note list shows, and returns every match. The notes' text is parsed once per database
 * change (in [ObserveNotesUseCase]); a keystroke only filters, after a short pause.
 */
@HiltViewModel
class NoteSearchViewModel @Inject constructor(
    observeNotesUseCase: ObserveNotesUseCase,
    @IoDispatcher dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val notes = observeNotesUseCase(tagId = null, sortOption = NoteSortOption.UPDATED_DESC)
        // A read failure is shown by the note list behind the search; here it only means
        // no results, never a crash.
        .asLoadState(ListLoadErrors.NOTES)
        .mapNotNull { it.valueOrNull() }

    @OptIn(FlowPreview::class)
    val results: StateFlow<SearchResults> =
        combine(_query.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }, notes) { query, all ->
            val needle = query.trim()
            SearchResults(
                query = needle,
                notes = if (needle.isEmpty()) emptyList() else all.filter { it.matches(needle) },
            )
        }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchResults())

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun clear() {
        _query.value = ""
    }

    /** The notes matching [query]; [query] is the trimmed text they were searched for. */
    data class SearchResults(val query: String = "", val notes: List<NoteListItem> = emptyList())

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 200L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
