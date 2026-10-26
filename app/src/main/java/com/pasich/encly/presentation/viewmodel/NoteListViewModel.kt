package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.core.common.asLoadState
import com.pasich.encly.core.common.reloadWith
import com.pasich.encly.core.common.valueOrNull
import com.pasich.encly.core.security.NeverLocked
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.domain.usecase.note.ObserveNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteArchiveStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteColorUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteDescriptionUseCase
import com.pasich.encly.domain.usecase.note.UpdateNotePinStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteListViewModel
@Suppress("LongParameterList") // Hilt-injected dependencies, one use case per action on a note.
@Inject
constructor(
    private val observeNotesUseCase: ObserveNotesUseCase,
    private val updateNoteTrashStatusUseCase: UpdateNoteTrashStatusUseCase,
    private val updateNoteTagUseCase: UpdateNoteTagUseCase,
    private val updateNotePinStatusUseCase: UpdateNotePinStatusUseCase,
    private val updateNoteArchiveStatusUseCase: UpdateNoteArchiveStatusUseCase,
    private val updateNoteColorUseCase: UpdateNoteColorUseCase,
    private val selectedTagHolder: SelectedTagHolder,
    private val settingsRepository: SettingsRepository,
    private val updateNoteDescriptionUseCase: UpdateNoteDescriptionUseCase,
    lockEvents: VaultLockEvents = NeverLocked,
) : ViewModel() {

    private val _state = MutableStateFlow(NoteListState())
    val state: StateFlow<NoteListState> get() = _state

    private val scrollToTop = NotesScrollToTop()
    private var lastScrollToTopRequest = 0
    private val _scrollToTopRequest = MutableStateFlow<Int?>(null)

    /**
     * A scroll back to the top the notes list still owes ([NotesScrollToTop]), or null when there
     * is none. It lives as long as this ViewModel, so a list that comes back from the editor still
     * gets it, and a list restored after process death starts with none. The screen clears it
     * with [onScrolledToTop] once it has scrolled.
     */
    val scrollToTopRequest: StateFlow<Int?> = _scrollToTopRequest.asStateFlow()

    private var notesJob: Job? = null

    init {
        observeNotes()
        clearOnLock(lockEvents) {
            notesJob?.cancel()
            _state.value = NoteListState()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeNotes() {
        notesJob = viewModelScope.launch {
            combine(
                selectedTagHolder.selectedTagFlow.onStart { emit(Tag()) },
                settingsRepository.getSortNotes,
            ) { tag, sortOption ->
                NotesQuery(tagId = tag.id, tagName = tag.nameTag, sortOption = sortOption)
            }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    _state.update {
                        it.copy(
                            selectedTag = query.tagId,
                            selectedTagName = query.tagName,
                            noteSortOption = query.sortOption,
                        )
                    }
                    observeNotesUseCase(
                        tagId = query.tagId.takeUnless { it == ALL_NOTES_TAG_ID },
                        sortOption = query.sortOption,
                    ).asLoadState(ListLoadErrors.NOTES).map { load -> query.sortOption to load }
                }
                .collect { (sortOption, load) ->
                    // The sort order travels with the notes it produced: the list scrolls to its
                    // top once the re-sorted notes are there, not while the old order is shown.
                    val scroll = load.valueOrNull()?.let { notes ->
                        scrollToTop.onNotesLoaded(sortOption, notes.map { it.note.id })
                    } == true
                    _state.update { it.copy(notesLoad = it.notesLoad.reloadWith(load)) }
                    if (scroll) _scrollToTopRequest.value = ++lastScrollToTopRequest
                }
        }
    }

    /** The list has scrolled to its top for [request]; a newer request stays pending. */
    fun onScrolledToTop(request: Int) {
        _scrollToTopRequest.compareAndSet(request, null)
    }

    /** The note as stored now, for the actions sheet: it must not show what an older read held. */
    fun observeNote(noteId: Long) = observeNotesUseCase.observeNote(noteId)

    fun onEvent(event: NoteListEvent) {
        // Picking a tag is not a write: it applies at once, before any coroutine runs.
        if (event is NoteListEvent.SelectTag) {
            selectedTagHolder.selectTag(event.tag)
        } else {
            viewModelScope.launch { perform(event) }
        }
    }

    /** Carries out [event]; false when its write failed (a swipe then springs the card back). */
    suspend fun perform(event: NoteListEvent): Boolean = viewModelScope.async { execute(event) }.await()

    // The write runs in the ViewModel's scope: a card that leaves the screen mid-write cannot cancel it.
    private suspend fun execute(event: NoteListEvent): Boolean = when (event) {
        is NoteListEvent.SelectTag -> {
            selectedTagHolder.selectTag(event.tag)
            true
        }

        is NoteListEvent.NoteToTrash -> updateNoteTrashStatusUseCase(event.note, true).isSuccess

        is NoteListEvent.PinNote -> updateNotePinStatusUseCase(event.note, event.pinned).isSuccess

        is NoteListEvent.ArchiveNote -> updateNoteArchiveStatusUseCase(event.note, event.archived).isSuccess

        is NoteListEvent.ChangeColor -> updateNoteColorUseCase(event.note, event.color).isSuccess

        is NoteListEvent.ChangeTag -> updateNoteTagUseCase(event.note, event.idTag).isSuccess

        is NoteListEvent.ChangeDescription -> updateNoteDescriptionUseCase(event.note, event.description).isSuccess

        is NoteListEvent.ToggleNoteSort -> {
            settingsRepository.setSortNotes(event.option)
            true
        }
    }

    private data class NotesQuery(val tagId: Long, val tagName: String, val sortOption: NoteSortOption)

    private companion object {
        const val ALL_NOTES_TAG_ID = 0L
    }
}

sealed class NoteListEvent {
    data class SelectTag(val tag: Tag) : NoteListEvent()
    data class NoteToTrash(val note: Note) : NoteListEvent()
    data class PinNote(val note: Note, val pinned: Boolean) : NoteListEvent()
    data class ArchiveNote(val note: Note, val archived: Boolean) : NoteListEvent()
    data class ChangeColor(val note: Note, val color: NoteColor) : NoteListEvent()
    data class ChangeTag(val note: Note, val idTag: Long) : NoteListEvent()
    data class ChangeDescription(val note: Note, val description: String) : NoteListEvent()
    data class ToggleNoteSort(val option: NoteSortOption) : NoteListEvent()
}

data class NoteListState(
    val selectedTag: Long = 0,
    val selectedTagName: String = "",
    val noteSortOption: NoteSortOption = NoteSortOption.UPDATED_DESC,
    val notesLoad: LoadState<List<NoteListItem>> = LoadState.Loading,
) {
    val notes: List<NoteListItem> get() = notesLoad.valueOrNull().orEmpty()

    /**
     * Which empty state the list shows once it loaded with nothing: none while loading, failed
     * or with notes; "no notes tagged …" while a tag chip filters an otherwise non-empty vault.
     */
    val emptyState: NotesEmptyState?
        get() = when {
            notesLoad !is LoadState.Ready || notes.isNotEmpty() -> null
            selectedTag != 0L -> NotesEmptyState.NoneTagged(selectedTagName)
            else -> NotesEmptyState.NoNotes
        }
}

/** The empty notes list: no notes at all, or none with the selected tag. */
sealed interface NotesEmptyState {
    data object NoNotes : NotesEmptyState

    data class NoneTagged(val tagName: String) : NotesEmptyState
}
