package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.common.BaseState
import com.pasich.encly.core.common.UiState
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.repository.TagSelectionRepository
import com.pasich.encly.domain.usecase.tag.AddTagUseCase
import com.pasich.encly.domain.usecase.tag.DeleteTagUseCase
import com.pasich.encly.domain.usecase.tag.GetTagsUseCase
import com.pasich.encly.domain.usecase.tag.ReorderTagsUseCase
import com.pasich.encly.domain.usecase.tag.SelectTagUseCase
import com.pasich.encly.domain.usecase.tag.UpdateTagUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TagListViewModel @Inject constructor(
    private val getTagsUseCase: GetTagsUseCase,
    private val addTagUseCase: AddTagUseCase,
    private val deleteTagUseCase: DeleteTagUseCase,
    private val updateTagUseCase: UpdateTagUseCase,
    private val reorderTagsUseCase: ReorderTagsUseCase,
    private val selectTagUseCase: SelectTagUseCase,
    private val tagSelectionRepository: TagSelectionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TagListState())
    val state: StateFlow<TagListState> = _state.asStateFlow()

    init {
        loadTags()
        viewModelScope.launch {
            tagSelectionRepository.selectedTagFlow.collect { selectedTag ->
                _state.update { it.copy(selectedTagId = selectedTag.id) }
            }
        }
    }

    fun onEvent(event: TagListEvent) {
        when (event) {
            is TagListEvent.AddTag -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val newPosition = (_state.value.listTags.minOfOrNull { it.position } ?: 0) - 1
                    addTagUseCase.invoke(event.tag.copy(position = newPosition))
                }
            }

            is TagListEvent.DeleteTag -> {
                viewModelScope.launch(Dispatchers.IO) {
                    if (_state.value.selectedTagId == event.tag.id) {
                        selectTagUseCase(Tag(id = 0, nameTag = "All"))
                    }
                    deleteTagUseCase.invoke(event.tag)
                }
            }

            is TagListEvent.UpdateTag -> {
                viewModelScope.launch(Dispatchers.IO) {
                    updateTagUseCase.invoke(event.tag)
                }
            }

            is TagListEvent.SelectTag -> {
                selectTagUseCase(event.tag)
            }

            is TagListEvent.ReorderTags -> {
                viewModelScope.launch(Dispatchers.IO) {
                    reorderTagsUseCase.invoke(event.tags)
                }
            }

            is TagListEvent.ReorderTagsLive -> {
                _state.value =
                    _state.value.copy(listTags = _state.value.listTags.toMutableList().apply {
                        add(event.to, removeAt(event.from))
                    })
            }

            is TagListEvent.ToggleVisibleTag -> {
                viewModelScope.launch(Dispatchers.IO) {
                    updateTagUseCase.invoke(event.tag.copy(isVisible = !event.tag.isVisible))
                }
            }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            getTagsUseCase().collect { uiState ->
                when (uiState) {
                    is UiState.Loading -> {
                        _state.update { it.copy(baseState = it.baseState.copy(isLoading = true)) }
                    }

                    is UiState.Success -> {
                        _state.update {
                            it.copy(
                                listTags = uiState.data ?: emptyList(),
                                baseState = it.baseState.copy(isLoading = false)
                            )
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
        }
    }
}


sealed class TagListEvent {
    data class AddTag(val tag: Tag) : TagListEvent()
    data class DeleteTag(val tag: Tag) : TagListEvent()
    data class UpdateTag(val tag: Tag) : TagListEvent()
    data class SelectTag(val tag: Tag) : TagListEvent()
    data class ReorderTags(val tags: List<Tag>) : TagListEvent()
    data class ReorderTagsLive(val to: Int, val from: Int) : TagListEvent()
    data class ToggleVisibleTag(val tag: Tag) : TagListEvent()
}

data class TagListState(
    val listTags: List<Tag> = emptyList<Tag>(),
    val selectedTagId: Long = 0,
    val baseState: BaseState = BaseState()
)

