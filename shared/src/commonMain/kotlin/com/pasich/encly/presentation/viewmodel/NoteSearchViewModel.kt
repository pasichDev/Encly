package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.common.asLoadState
import com.pasich.encly.core.common.valueOrNull
import com.pasich.encly.core.di.IoDispatcher
import com.pasich.encly.core.security.NeverLocked
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.domain.usecase.note.ObserveNotesUseCase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach

/**
 * Searches every note that is not in the trash and whose tag is not hidden, whatever tag chip
 * the note list shows, and returns every match. The notes' text is parsed once per database
 * change (in [ObserveNotesUseCase]); a keystroke only filters, after a short pause.
 */
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class NoteSearchViewModel(
    observeNotesUseCase: ObserveNotesUseCase,
    @IoDispatcher dispatcher: CoroutineDispatcher,
    lockEvents: VaultLockEvents = NeverLocked,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val notes = observeNotesUseCase(tagId = null, sortOption = NoteSortOption.UPDATED_DESC)
        // A read failure is shown by the note list behind the search; here it only means
        // no results, never a crash.
        .asLoadState(ListLoadErrors.NOTES)
        .mapNotNull { it.valueOrNull() }

    private val _results = MutableStateFlow(SearchResults())

    /** Kept in a state of its own, not shared from the flow, so a lock can empty it at any time. */
    val results: StateFlow<SearchResults> = _results.asStateFlow()

    private var searchJob: Job? = null

    init {
        search(dispatcher)
        clearOnLock(lockEvents) {
            searchJob?.cancel()
            _query.value = ""
            _results.value = SearchResults()
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun search(dispatcher: CoroutineDispatcher) {
        // With nothing typed the notes are not even read: they are collected only while a query is shown.
        searchJob = _query
            .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
            .map { it.trim() }
            .distinctUntilChanged()
            .flatMapLatest { needle ->
                if (needle.isEmpty()) {
                    flowOf(SearchResults())
                } else {
                    notes.map { all -> SearchResults(query = needle, notes = all.filter { it.matches(needle) }) }
                }
            }
            .flowOn(dispatcher)
            .onEach { _results.value = it }
            .launchIn(viewModelScope)
    }

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
    }
}
