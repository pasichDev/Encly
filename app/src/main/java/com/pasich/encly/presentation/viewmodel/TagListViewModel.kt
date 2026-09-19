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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TagOperationFailure {
    CREATE,
    UPDATE,
    DELETE,
    REORDER
}

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

    private val _operationFailures = MutableSharedFlow<TagOperationFailure>(extraBufferCapacity = 1)
    val operationFailures: SharedFlow<TagOperationFailure> = _operationFailures.asSharedFlow()

    private var persistedTags: List<Tag> = emptyList()
    private var reorderJob: Job? = null

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
                viewModelScope.launch {
                    val newPosition = (_state.value.listTags.minOfOrNull { it.position } ?: 0) - 1
                    val added = addTagUseCase(event.tag.copy(position = newPosition)) > 0L
                    event.onResult(added)
                    if (!added) {
                        _operationFailures.emit(TagOperationFailure.CREATE)
                    }
                }
            }

            is TagListEvent.DeleteTag -> {
                viewModelScope.launch {
                    if (deleteTagUseCase(event.tag)) {
                        if (_state.value.selectedTagId == event.tag.id) {
                            selectTagUseCase(Tag(id = 0, nameTag = "All"))
                        }
                    } else {
                        _operationFailures.emit(TagOperationFailure.DELETE)
                    }
                }
            }

            is TagListEvent.UpdateTag -> {
                viewModelScope.launch {
                    if (!updateTagUseCase(event.tag)) {
                        _operationFailures.emit(TagOperationFailure.UPDATE)
                    }
                }
            }

            is TagListEvent.SelectTag -> {
                selectTagUseCase(event.tag)
            }

            is TagListEvent.ReorderTags -> {
                scheduleReorder(event.tags)
            }

            is TagListEvent.ReorderTagsLive -> {
                val reorderedTags = _state.value.listTags.toMutableList().apply {
                    add(event.to, removeAt(event.from))
                }
                _state.update { it.copy(listTags = reorderedTags) }
                scheduleReorder(reorderedTags)
            }

            is TagListEvent.ToggleVisibleTag -> {
                viewModelScope.launch {
                    if (!updateTagUseCase(event.tag.copy(isVisible = !event.tag.isVisible))) {
                        _operationFailures.emit(TagOperationFailure.UPDATE)
                    }
                }
            }
        }
    }

    private fun scheduleReorder(tags: List<Tag>) {
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            delay(REORDER_SAVE_DEBOUNCE_MS)
            if (!reorderTagsUseCase(tags)) {
                _state.update { it.copy(listTags = persistedTags) }
                _operationFailures.emit(TagOperationFailure.REORDER)
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
                        val tags = uiState.data.orEmpty()
                        persistedTags = tags
                        _state.update {
                            it.copy(
                                listTags = tags,
                                baseState = it.baseState.copy(isLoading = false)
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
        }
    }

    private companion object {
        const val REORDER_SAVE_DEBOUNCE_MS = 300L
    }
}

sealed class TagListEvent {
    data class AddTag(
        val tag: Tag,
        val onResult: (Boolean) -> Unit
    ) : TagListEvent()

    data class DeleteTag(val tag: Tag) : TagListEvent()
    data class UpdateTag(val tag: Tag) : TagListEvent()
    data class SelectTag(val tag: Tag) : TagListEvent()
    data class ReorderTags(val tags: List<Tag>) : TagListEvent()
    data class ReorderTagsLive(val to: Int, val from: Int) : TagListEvent()
    data class ToggleVisibleTag(val tag: Tag) : TagListEvent()
}

data class TagListState(
    val listTags: List<Tag> = emptyList(),
    val selectedTagId: Long = 0,
    val baseState: BaseState = BaseState()
)
