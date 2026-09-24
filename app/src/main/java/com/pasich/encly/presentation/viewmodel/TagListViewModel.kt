package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.core.common.asLoadState
import com.pasich.encly.core.common.valueOrNull
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.repository.TagsRepository
import com.pasich.encly.domain.usecase.tag.ReorderTagsUseCase
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
    REORDER,

    /** Another tag already has the name (compared trimmed, ignoring case). */
    NAME_TAKEN,
}

/**
 * Whether a tag other than [exceptId] is already called [name]. Names are compared trimmed and
 * ignoring case, so "Work", "work" and " Work " are one tag.
 */
internal fun List<Tag>.hasTagNamed(name: String, exceptId: Long? = null): Boolean {
    val wanted = name.trim()
    return any { it.id != exceptId && it.nameTag.trim().equals(wanted, ignoreCase = true) }
}

@HiltViewModel
class TagListViewModel @Inject constructor(
    private val tagsRepository: TagsRepository,
    private val reorderTagsUseCase: ReorderTagsUseCase,
    private val selectedTagHolder: SelectedTagHolder,
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
            selectedTagHolder.selectedTagFlow.collect { selectedTag ->
                _state.update { it.copy(selectedTagId = selectedTag.id) }
            }
        }
    }

    fun onEvent(event: TagListEvent) {
        when (event) {
            is TagListEvent.AddTag -> addTag(event)

            is TagListEvent.DeleteTag -> deleteTag(event.tag)

            is TagListEvent.UpdateTag -> updateTag(event.tag, event.onResult)

            is TagListEvent.SelectTag -> selectedTagHolder.selectTag(event.tag)

            is TagListEvent.ReorderTags -> scheduleReorder(event.tags)

            is TagListEvent.ReorderTagsLive -> reorderTags(event)

            is TagListEvent.ToggleVisibleTag -> updateTag(
                event.tag.copy(isVisible = !event.tag.isVisible),
            )
        }
    }

    private fun addTag(event: TagListEvent.AddTag) {
        viewModelScope.launch {
            if (_state.value.listTags.hasTagNamed(event.tag.nameTag)) {
                event.onResult(false)
                _operationFailures.emit(TagOperationFailure.NAME_TAKEN)
                return@launch
            }
            val newPosition = (_state.value.listTags.minOfOrNull { it.position } ?: 0) - 1
            val added = tagsRepository.addTag(event.tag.copy(position = newPosition)).isSuccess
            event.onResult(added)
            if (!added) {
                _operationFailures.emit(TagOperationFailure.CREATE)
            }
        }
    }

    private fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            if (tagsRepository.deleteTag(tag).isSuccess) {
                if (_state.value.selectedTagId == tag.id) {
                    selectedTagHolder.selectTag(Tag(id = 0, nameTag = "All"))
                }
            } else {
                _operationFailures.emit(TagOperationFailure.DELETE)
            }
        }
    }

    private fun updateTag(tag: Tag, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (_state.value.listTags.hasTagNamed(tag.nameTag, exceptId = tag.id)) {
                onResult(false)
                _operationFailures.emit(TagOperationFailure.NAME_TAKEN)
                return@launch
            }
            val updated = tagsRepository.updateTag(tag).isSuccess
            onResult(updated)
            if (!updated) {
                _operationFailures.emit(TagOperationFailure.UPDATE)
            }
        }
    }

    private fun reorderTags(event: TagListEvent.ReorderTagsLive) {
        val currentTags = _state.value.tagsLoad.valueOrNull() ?: return
        val reorderedTags = currentTags.toMutableList().apply {
            add(event.to, removeAt(event.from))
        }
        _state.update { it.copy(tagsLoad = LoadState.Ready(reorderedTags)) }
        scheduleReorder(reorderedTags)
    }

    private fun scheduleReorder(tags: List<Tag>) {
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            delay(REORDER_SAVE_DEBOUNCE_MS)
            if (reorderTagsUseCase(tags).isFailure) {
                _state.update { it.copy(tagsLoad = LoadState.Ready(persistedTags)) }
                _operationFailures.emit(TagOperationFailure.REORDER)
            }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            tagsRepository.getTags().asLoadState(ListLoadErrors.TAGS).collect { load ->
                load.valueOrNull()?.let { persistedTags = it }
                _state.update { it.copy(tagsLoad = load) }
            }
        }
    }

    private companion object {
        const val REORDER_SAVE_DEBOUNCE_MS = 300L
    }
}

sealed class TagListEvent {
    data class AddTag(val tag: Tag, val onResult: (Boolean) -> Unit) : TagListEvent()

    data class DeleteTag(val tag: Tag) : TagListEvent()
    data class UpdateTag(val tag: Tag, val onResult: (Boolean) -> Unit = {}) : TagListEvent()
    data class SelectTag(val tag: Tag) : TagListEvent()
    data class ReorderTags(val tags: List<Tag>) : TagListEvent()
    data class ReorderTagsLive(val to: Int, val from: Int) : TagListEvent()
    data class ToggleVisibleTag(val tag: Tag) : TagListEvent()
}

data class TagListState(val tagsLoad: LoadState<List<Tag>> = LoadState.Loading, val selectedTagId: Long = 0) {
    val listTags: List<Tag> get() = tagsLoad.valueOrNull().orEmpty()
}
