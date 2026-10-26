package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.core.common.asLoadState
import com.pasich.encly.core.common.reloadWith
import com.pasich.encly.core.common.valueOrNull
import com.pasich.encly.core.security.NeverLocked
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.domain.usecase.note.ObserveNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteArchiveStatusUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteColorUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteDescriptionUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The archive: archived notes that are not in the trash, newest edit first. It acts on a note
 * with the same [NoteListEvent]s as the main list, minus the ones that do not apply here (a
 * tag chip, the sort order, pinning: an archived note is never pinned).
 */
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val observeNotesUseCase: ObserveNotesUseCase,
    private val updateNoteTrashStatusUseCase: UpdateNoteTrashStatusUseCase,
    private val updateNoteArchiveStatusUseCase: UpdateNoteArchiveStatusUseCase,
    private val updateNoteTagUseCase: UpdateNoteTagUseCase,
    private val updateNoteDescriptionUseCase: UpdateNoteDescriptionUseCase,
    private val updateNoteColorUseCase: UpdateNoteColorUseCase,
    lockEvents: VaultLockEvents = NeverLocked,
) : ViewModel() {

    private val _state = MutableStateFlow(ArchiveState())
    val state: StateFlow<ArchiveState> get() = _state

    private val notesJob: Job = viewModelScope.launch {
        observeNotesUseCase(tagId = null, sortOption = NoteSortOption.UPDATED_DESC, archived = true)
            .asLoadState(ListLoadErrors.NOTES)
            .collect { load -> _state.update { it.copy(notesLoad = it.notesLoad.reloadWith(load)) } }
    }

    init {
        clearOnLock(lockEvents) {
            notesJob.cancel()
            _state.value = ArchiveState()
        }
    }

    /** The note as stored now, for the actions sheet: it must not show what an older read held. */
    fun observeNote(noteId: Long) = observeNotesUseCase.observeNote(noteId)

    fun onEvent(event: NoteListEvent) {
        viewModelScope.launch {
            when (event) {
                is NoteListEvent.ArchiveNote -> updateNoteArchiveStatusUseCase(event.note, event.archived)
                is NoteListEvent.NoteToTrash -> updateNoteTrashStatusUseCase(event.note, true)
                is NoteListEvent.ChangeTag -> updateNoteTagUseCase(event.note, event.idTag)
                is NoteListEvent.ChangeDescription -> updateNoteDescriptionUseCase(event.note, event.description)
                is NoteListEvent.ChangeColor -> updateNoteColorUseCase(event.note, event.color)
                is NoteListEvent.SelectTag, is NoteListEvent.ToggleNoteSort, is NoteListEvent.PinNote -> Unit
            }
        }
    }
}

data class ArchiveState(val notesLoad: LoadState<List<NoteListItem>> = LoadState.Loading) {
    val notes: List<NoteListItem> get() = notesLoad.valueOrNull().orEmpty()
}
