package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.core.common.asLoadState
import com.pasich.encly.core.common.reloadWith
import com.pasich.encly.core.common.valueOrNull
import com.pasich.encly.data.model.Note
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.NoteListItem
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.domain.usecase.note.ObserveNotesUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteDescriptionUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTagUseCase
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
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
    private val observeNotesUseCase: ObserveNotesUseCase,
    private val updateNoteTrashStatusUseCase: UpdateNoteTrashStatusUseCase,
    private val updateNoteTagUseCase: UpdateNoteTagUseCase,
    private val selectedTagHolder: SelectedTagHolder,
    private val settingsRepository: SettingsRepository,
    private val updateNoteDescriptionUseCase: UpdateNoteDescriptionUseCase,
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
                selectedTagHolder.selectedTagFlow.onStart { emit(Tag()) },
                settingsRepository.getSortNotes,
            ) { tag, sortOption ->
                NotesQuery(tagId = tag.id, sortOption = sortOption)
            }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    _state.update {
                        it.copy(
                            selectedTag = query.tagId,
                            noteSortOption = query.sortOption,
                        )
                    }
                    observeNotesUseCase(
                        tagId = query.tagId.takeUnless { it == ALL_NOTES_TAG_ID },
                        sortOption = query.sortOption,
                    ).asLoadState(ListLoadErrors.NOTES)
                }
                .collect { load ->
                    _state.update { it.copy(notesLoad = it.notesLoad.reloadWith(load)) }
                }
        }
    }

    fun onEvent(event: NoteListEvent) {
        when (event) {
            is NoteListEvent.SelectTag -> {
                selectedTagHolder.selectTag(event.tag)
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
                    settingsRepository.setSortNotes(event.option)
                }
            }

            is NoteListEvent.ChangeDescription -> {
                viewModelScope.launch {
                    updateNoteDescriptionUseCase.invoke(event.note, event.description)
                }
            }
        }
    }

    private data class NotesQuery(val tagId: Long, val sortOption: NoteSortOption)

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
    val notesLoad: LoadState<List<NoteListItem>> = LoadState.Loading,
) {
    val notes: List<NoteListItem> get() = notesLoad.valueOrNull().orEmpty()
}
