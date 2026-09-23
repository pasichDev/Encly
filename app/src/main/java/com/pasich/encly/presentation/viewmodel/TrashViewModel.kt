package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.core.common.asLoadState
import com.pasich.encly.core.common.valueOrNull
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.usecase.note.CleanTrashNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val updateNoteTrashStatusUseCase: UpdateNoteTrashStatusUseCase,
    private val cleanTrashNotesUseCase: CleanTrashNotesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TrashListState())
    val state: StateFlow<TrashListState> get() = _state

    init {
        loadNotes()
    }

    private fun loadNotes() {
        viewModelScope.launch {
            notesRepository.getTrashNotes().asLoadState(ListLoadErrors.NOTES).collect { load ->
                _state.update { it.copy(notesLoad = load) }
            }
        }
    }

    private fun toggleCheckItem(index: Int) {
        val currentNotes = _state.value.notesLoad.valueOrNull() ?: return
        if (index !in currentNotes.indices) return

        val updatedNotes = currentNotes.mapIndexed { i, item ->
            if (i == index) {
                item.copy(isChecked = !item.isChecked)
            } else {
                item
            }
        }

        val checkedCount = updatedNotes.count { it.isChecked }

        _state.value = _state.value.copy(
            notesLoad = LoadState.Ready(updatedNotes),
            checkedCount = checkedCount,
            canCheck = checkedCount > 0,
        )
    }

    private fun toggleAllCheck(checked: Boolean) {
        val currentNotes = _state.value.notesLoad.valueOrNull() ?: return
        val updated = currentNotes.map { item ->
            item.copy(isChecked = checked)
        }

        _state.value = _state.value.copy(
            notesLoad = LoadState.Ready(updated),
            canCheck = updated.any { it.isChecked },
            checkedCount = updated.count { it.isChecked },
        )
    }

    private fun restoreNotes() {
        val currentNotes = _state.value.notes
        currentNotes.forEach { if (it.isChecked) restoreNote(it) }
        toggleAllCheck(false)
    }

    private fun restoreNote(note: Note) {
        viewModelScope.launch {
            updateNoteTrashStatusUseCase.invoke(
                note,
                false,
            )
        }
    }

    private fun cleanAll() {
        val currentNotes = _state.value.notes
        if (currentNotes.isEmpty()) return
        viewModelScope.launch {
            cleanTrashNotesUseCase.invoke()
        }
        toggleAllCheck(false)
    }

    private fun cleanNotes() {
        val currentNotes = _state.value.notes
        if (currentNotes.isEmpty()) return
        viewModelScope.launch {
            // Get the selected notes
            val selectedNotes = currentNotes.filter { it.isChecked }
            if (selectedNotes.isNotEmpty()) {
                cleanTrashNotesUseCase.cleanSelectedNotes(selectedNotes)
            }
        }
        toggleAllCheck(false)
    }

    fun onEvent(event: TrashListEvent) {
        when (event) {
            is TrashListEvent.ToggleCheckItem -> toggleCheckItem(event.index)
            is TrashListEvent.RestoreNotes -> restoreNotes()
            is TrashListEvent.CleanAll -> cleanAll()
            is TrashListEvent.CleanNotes -> cleanNotes()
        }
    }
}

sealed class TrashListEvent {
    data class ToggleCheckItem(val index: Int) : TrashListEvent()
    class RestoreNotes : TrashListEvent()
    class CleanAll : TrashListEvent()
    class CleanNotes : TrashListEvent()
}

data class TrashListState(
    val notesLoad: LoadState<List<Note>> = LoadState.Loading,
    val canCheck: Boolean = false,
    val checkedCount: Int = 0,
) {
    val notes: List<Note> get() = notesLoad.valueOrNull().orEmpty()
}
