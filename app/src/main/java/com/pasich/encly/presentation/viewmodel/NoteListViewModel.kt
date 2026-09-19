package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.common.BaseState
import com.pasich.encly.core.common.UiState
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.NoteWithTag
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.repository.TagSelectionRepository
import com.pasich.encly.domain.usecase.note.GetAllNotesUseCase
import com.pasich.encly.domain.usecase.note.GetNotesByTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteDescriptionUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.domain.usecase.settings.GetNoteSortOptionUseCase
import com.pasich.encly.domain.usecase.settings.ToggleNoteSortTagUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val getAllNotesUseCase: GetAllNotesUseCase,
    private val getNotesByTagUseCase: GetNotesByTagUseCase,
    private val updateNoteTrashStatusUseCase: UpdateNoteTrashStatusUseCase,
    private val updateNoteTagUseCase: UpdateNoteTagUseCase,
    private val tagSelectionRepository: TagSelectionRepository,
    private val toggleNoteSortTagUseCase: ToggleNoteSortTagUseCase,
    private val getNoteSortOptionUseCase: GetNoteSortOptionUseCase,
    private val updateNoteDescriptionUseCase: UpdateNoteDescriptionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(NoteListState())
    val state: StateFlow<NoteListState> get() = _state

    init {
        observeNotes()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeNotes() {
        viewModelScope.launch {
            combine(
                tagSelectionRepository.selectedTagFlow.onStart { emit(Tag()) },
                getNoteSortOptionUseCase()
            ) { tag, sortOption ->
                NotesQuery(tagId = tag.id, sortOption = sortOption)
            }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    _state.update {
                        it.copy(
                            selectedTag = query.tagId,
                            noteSortOption = query.sortOption
                        )
                    }
                    if (query.tagId == ALL_NOTES_TAG_ID) {
                        getAllNotesUseCase(query.sortOption)
                    } else {
                        getNotesByTagUseCase(query.tagId, query.sortOption)
                    }
                }
                .collect(::handleUiState)
        }
    }

    private fun handleUiState(uiState: UiState<List<NoteWithTag>>) {
        when (uiState) {
            is UiState.Loading -> {
                _state.update {
                    it.copy(baseState = it.baseState.copy(isLoading = true, error = null))
                }
            }

            is UiState.Success -> {
                _state.update {
                    it.copy(
                        notes = uiState.data.orEmpty(),
                        baseState = it.baseState.copy(isLoading = false, error = null)
                    )
                }
            }

            is UiState.Error -> {
                _state.update {
                    it.copy(
                        baseState = it.baseState.copy(
                            isLoading = false,
                            error = uiState.message.toString()
                        )
                    )
                }
            }
        }
    }

    fun onEvent(event: NoteListEvent) {
        when (event) {
            is NoteListEvent.SelectTag -> {
                tagSelectionRepository.selectTag(event.tag)
            }

            is NoteListEvent.NoteToTrash -> {
                viewModelScope.launch {
                    updateNoteTrashStatusUseCase.invoke(event.note, true)
                }
            }

            is NoteListEvent.ChangeTag -> {
                viewModelScope.launch {
                    updateNoteTagUseCase.invoke(event.note, event.idTag)
                }
            }

            is NoteListEvent.ToggleNoteSort -> {
                viewModelScope.launch {
                    toggleNoteSortTagUseCase.invoke(event.option, viewModelScope)
                }
            }

            is NoteListEvent.ChangeDescription -> {
                viewModelScope.launch {
                    updateNoteDescriptionUseCase.invoke(event.note, event.description)
                }
            }
        }
    }

    private data class NotesQuery(
        val tagId: Long,
        val sortOption: NoteSortOption
    )

    private companion object {
        const val ALL_NOTES_TAG_ID = 0L
    }
}

sealed class NoteListEvent {
    data class SelectTag(val tag: Tag) : NoteListEvent()
    data class NoteToTrash(val note: Note) : NoteListEvent()
    data class ChangeTag(val note: Note, val idTag: Long) : NoteListEvent()
    data class ChangeDescription(val note: Note, val description: String) : NoteListEvent()
    data class ToggleNoteSort(val option: NoteSortOption) : NoteListEvent()
}

data class NoteListState(
    val selectedTag: Long = 0,
    val noteSortOption: NoteSortOption = NoteSortOption.UPDATED_DESC,
    val notes: List<NoteWithTag> = emptyList(),
    val baseState: BaseState = BaseState()
)
