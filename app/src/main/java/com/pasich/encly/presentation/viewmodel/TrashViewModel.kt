package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.common.BaseState
import com.pasich.encly.core.common.UiState
import com.pasich.encly.data.model.Note
import com.pasich.encly.domain.usecase.note.DeleteNoteByIdUseCase
import com.pasich.encly.domain.usecase.note.GetTrashNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class TrashViewModel @Inject constructor(
    private val getTrashNotesUseCase: GetTrashNotesUseCase,
    private val updateNoteTrashStatusUseCase: UpdateNoteTrashStatusUseCase,
    private val deleteNoteByIdUseCase: DeleteNoteByIdUseCase,
    private val cleanTrashNotesUseCase: com.pasich.encly.domain.usecase.note.CleanTrashNotesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(TrashListState())
    val state: StateFlow<TrashListState> get() = _state

    init {
        loadNotes()
    }

    private fun handleUiState(uiState: UiState<List<Note>>) {

        when (uiState) {
            is UiState.Loading -> {
                _state.update {
                    it.copy(
                        baseState = it.baseState.copy(isLoading = true),
                    )
                }
            }

            is UiState.Success -> {
                uiState.data?.let { data ->
                    _state.update {
                        it.copy(
                            notes = data,
                            baseState = it.baseState.copy(isLoading = false),
                        )
                    }
                }
            }

            is UiState.Error -> {
                _state.update {
                    it.copy(
                        baseState = it.baseState.copy(
                            isLoading = false, error = uiState.message.toString()
                        )
                    )
                }
            }
        }
    }


    private fun loadNotes() {
        viewModelScope.launch {
            getTrashNotesUseCase().collect { uiState ->
                handleUiState(uiState)

            }
        }
    }


    private fun toggleCheckItem(index: Int) {
        val currentNotes = _state.value.notes
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
            notes = updatedNotes, checkedCount = checkedCount, canCheck = checkedCount > 0
        )
    }


    private fun toggleAllCheck(checked: Boolean) {
        val updated = _state.value.notes.map { item ->
            item.copy(isChecked = checked)
        }

        _state.value = _state.value.copy(
            notes = updated,
            canCheck = updated.any { it.isChecked },
            checkedCount = updated.count { it.isChecked })
    }


    private fun restoreNotes() {
        val currentNotes = _state.value.notes
        currentNotes.forEach { if (it.isChecked) restoreNote(it) }
        toggleAllCheck(false)
    }


    private fun restoreNote(note: Note) {
        viewModelScope.launch {
            updateNoteTrashStatusUseCase.invoke(
                note, false
            )
        }
    }

    private fun cleanAll() {
        val currentNotes = _state.value.notes
        if (currentNotes.isEmpty()) return
        viewModelScope.launch {
            // Використовуємо новий UseCase для масового видалення з очищенням фотографій
            cleanTrashNotesUseCase.invoke()
        }
        toggleAllCheck(false)
    }

    private fun cleanNotes() {
        val currentNotes = _state.value.notes
        if (currentNotes.isEmpty()) return
        viewModelScope.launch {
            // Отримуємо вибрані нотатки
            val selectedNotes = currentNotes.filter { it.isChecked }
            if (selectedNotes.isNotEmpty()) {
                // Використовуємо новий UseCase для видалення вибраних нотаток з очищенням фотографій
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
    class RestoreNotes() : TrashListEvent()
    class CleanAll() : TrashListEvent()
    class CleanNotes() : TrashListEvent()
}


data class TrashListState(
    val notes: List<Note> = emptyList<Note>(),
    val baseState: BaseState = BaseState(),
    val canCheck: Boolean = false,
    val checkedCount: Int = 0
)

