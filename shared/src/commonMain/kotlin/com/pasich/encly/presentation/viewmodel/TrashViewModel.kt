package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.core.common.asLoadState
import com.pasich.encly.core.common.valueOrNull
import com.pasich.encly.core.security.NeverLocked
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTags
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.usecase.note.CleanTrashNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A trash operation that did not go through; the screen tells the user. */
enum class TrashOperationFailure {
    RESTORE,
    DELETE,
}

@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class TrashViewModel(
    private val notesRepository: NotesRepository,
    private val updateNoteTrashStatusUseCase: UpdateNoteTrashStatusUseCase,
    private val cleanTrashNotesUseCase: CleanTrashNotesUseCase,
    lockEvents: VaultLockEvents = NeverLocked,
) : ViewModel() {

    private val _state = MutableStateFlow(TrashListState())
    val state: StateFlow<TrashListState> get() = _state

    private val _operationFailures = MutableSharedFlow<TrashOperationFailure>(extraBufferCapacity = 1)
    val operationFailures: SharedFlow<TrashOperationFailure> = _operationFailures.asSharedFlow()

    private var notesJob: Job? = null

    init {
        loadNotes()
        clearOnLock(lockEvents) {
            notesJob?.cancel()
            _state.value = TrashListState()
        }
    }

    private fun loadNotes() {
        notesJob = viewModelScope.launch {
            notesRepository.getTrashNotesWithTag().asLoadState(ListLoadErrors.NOTES).collect { load ->
                // The selection is a set of note ids: a re-emission of the table keeps what is still there selected,
                // and drops what is gone, so the counter and the rows never disagree.
                _state.update { current -> current.withNotes(load) }
            }
        }
    }

    private fun toggleCheckItem(noteId: Long) {
        _state.update { current ->
            val notes = current.notesLoad.valueOrNull() ?: return@update current
            if (notes.none { it.id == noteId }) return@update current
            val ids = current.selectedIds
            current.withSelection(if (noteId in ids) ids - noteId else ids + noteId)
        }
    }

    private fun clearSelection() {
        _state.update { current ->
            if (current.notesLoad.valueOrNull() == null) current else current.withSelection(emptySet())
        }
    }

    /** Puts the notes that were restored or deleted out of the selection; the ones that failed stay selected. */
    private fun deselect(done: Set<Long>) {
        _state.update { current -> current.withSelection(current.selectedIds - done) }
    }

    private fun restoreNotes() {
        val selected = _state.value.notes.filter { it.isChecked }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val restored = mutableSetOf<Long>()
            for (note in selected) {
                if (updateNoteTrashStatusUseCase(note, false).isSuccess) restored += note.id
            }
            deselect(restored)
            if (restored.size < selected.size) _operationFailures.emit(TrashOperationFailure.RESTORE)
        }
    }

    private fun cleanAll() {
        if (_state.value.notes.isEmpty()) return
        viewModelScope.launch {
            if (cleanTrashNotesUseCase().isSuccess) {
                clearSelection()
            } else {
                _operationFailures.emit(TrashOperationFailure.DELETE)
            }
        }
    }

    private fun cleanNotes() {
        val selected = _state.value.notes.filter { it.isChecked }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            if (cleanTrashNotesUseCase.cleanSelectedNotes(selected).isSuccess) {
                deselect(selected.mapTo(mutableSetOf()) { it.id })
            } else {
                _operationFailures.emit(TrashOperationFailure.DELETE)
            }
        }
    }

    fun onEvent(event: TrashListEvent) {
        when (event) {
            is TrashListEvent.ToggleCheckItem -> toggleCheckItem(event.noteId)
            is TrashListEvent.RestoreNotes -> restoreNotes()
            is TrashListEvent.CleanAll -> cleanAll()
            is TrashListEvent.CleanNotes -> cleanNotes()
            is TrashListEvent.ClearSelection -> clearSelection()
        }
    }
}

sealed class TrashListEvent {
    /** Selects or unselects the note with this id. */
    data class ToggleCheckItem(val noteId: Long) : TrashListEvent()
    class RestoreNotes : TrashListEvent()
    class CleanAll : TrashListEvent()
    class CleanNotes : TrashListEvent()

    /** Leaves selection mode (back, or the close button) without touching the notes. */
    data object ClearSelection : TrashListEvent()
}

/**
 * The trash: [notesLoad] holds the notes with [Note.isChecked] set from [selectedIds]. [hiddenIds] are the notes that
 * carry a hidden tag: the screen shows them as a placeholder, as the lists do not show them at all.
 */
data class TrashListState(
    val notesLoad: LoadState<List<Note>> = LoadState.Loading,
    val selectedIds: Set<Long> = emptySet(),
    val hiddenIds: Set<Long> = emptySet(),
) {
    val notes: List<Note> get() = notesLoad.valueOrNull().orEmpty()
    val checkedCount: Int get() = selectedIds.size
    val canCheck: Boolean get() = selectedIds.isNotEmpty()

    /** The state after [load] arrived: a selected note that is gone from it is dropped from the selection. */
    internal fun withNotes(load: LoadState<List<NoteWithTags>>): TrashListState {
        val items = load.valueOrNull() ?: return copy(
            notesLoad = when (load) {
                is LoadState.Failed -> load
                else -> LoadState.Loading
            },
            selectedIds = emptySet(),
            hiddenIds = emptySet(),
        )
        val notes = items.map { it.note }
        val present = notes.mapTo(HashSet()) { it.id }
        val kept = selectedIds.filterTo(LinkedHashSet()) { it in present }
        // The same rule as the lists: a note with any hidden tag is not shown.
        val hidden = items.filter { item -> item.tags.any { !it.isVisible } }.mapTo(HashSet()) { it.note.id }
        return copy(notesLoad = LoadState.Ready(notes.markChecked(kept)), selectedIds = kept, hiddenIds = hidden)
    }

    internal fun withSelection(selected: Set<Long>): TrashListState {
        val notes = notesLoad.valueOrNull() ?: return this
        return copy(notesLoad = LoadState.Ready(notes.markChecked(selected)), selectedIds = selected)
    }

    private fun List<Note>.markChecked(selected: Set<Long>): List<Note> =
        map { if ((it.id in selected) == it.isChecked) it else it.copy(isChecked = it.id in selected) }
}
