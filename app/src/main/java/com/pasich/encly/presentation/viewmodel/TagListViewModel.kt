package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.common.LoadState
import com.pasich.encly.core.common.asLoadState
import com.pasich.encly.core.common.valueOrNull
import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.repository.TagsRepository
import com.pasich.encly.domain.usecase.tag.ReorderTagsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

/** Longest tag name the editor accepts. */
const val TAG_NAME_MAX_LENGTH = 20

/**
 * A tag name as it is stored, the same for a new tag and a rename: trimmed, inner runs of
 * whitespace collapsed to one space, at most [TAG_NAME_MAX_LENGTH] characters.
 */
fun normalizeTagName(raw: String): String =
    raw.trim().split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ").take(TAG_NAME_MAX_LENGTH).trim()

private val WHITESPACE = Regex("\\s+")

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
    notesRepository: NotesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TagListState())
    val state: StateFlow<TagListState> = _state.asStateFlow()

    private val _operationFailures = MutableSharedFlow<TagOperationFailure>(extraBufferCapacity = 1)
    val operationFailures: SharedFlow<TagOperationFailure> = _operationFailures.asSharedFlow()

    /** How many notes (not in the trash) carry each tag, by tag id. Read only by the tags screen. */
    val noteCounts: StateFlow<Map<Long, Int>> = notesRepository.getAllNotesWithTag()
        .map { notes -> notes.mapNotNull { it.note.tagId }.groupingBy { it }.eachCount() }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

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

            is TagListEvent.ReorderTags -> scheduleReorder(event.tags, debounce = false)

            is TagListEvent.ReorderTagsLive -> reorderTags(event)

            is TagListEvent.ToggleVisibleTag -> updateTag(
                event.tag.copy(isVisible = !event.tag.isVisible),
            )
        }
    }

    private fun addTag(event: TagListEvent.AddTag) {
        val name = normalizeTagName(event.tag.nameTag)
        if (name.isEmpty()) {
            event.onResult(false)
            return
        }
        viewModelScope.launch {
            if (_state.value.listTags.hasTagNamed(name)) {
                event.onResult(false)
                _operationFailures.emit(TagOperationFailure.NAME_TAKEN)
                return@launch
            }
            val newPosition = (_state.value.listTags.minOfOrNull { it.position } ?: 0) - 1
            val added = tagsRepository.addTag(event.tag.copy(nameTag = name, position = newPosition)).isSuccess
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

    private fun updateTag(raw: Tag, onResult: (Boolean) -> Unit = {}) {
        val tag = raw.copy(nameTag = normalizeTagName(raw.nameTag))
        if (tag.nameTag.isEmpty()) {
            onResult(false)
            return
        }
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
        if (event.from !in currentTags.indices || event.to !in currentTags.indices) return
        val reorderedTags = currentTags.toMutableList().apply {
            add(event.to, removeAt(event.from))
        }
        _state.update { it.copy(tagsLoad = LoadState.Ready(reorderedTags)) }
        scheduleReorder(reorderedTags)
    }

    /** Saves [tags] in their order: after a pause while dragging, at once when the drag ends. */
    private fun scheduleReorder(tags: List<Tag>, debounce: Boolean = true) {
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            if (debounce) delay(REORDER_SAVE_DEBOUNCE_MS)
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
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

sealed class TagListEvent {
    data class AddTag(val tag: Tag, val onResult: (Boolean) -> Unit) : TagListEvent()

    data class DeleteTag(val tag: Tag) : TagListEvent()
    data class UpdateTag(val tag: Tag, val onResult: (Boolean) -> Unit = {}) : TagListEvent()
    data class SelectTag(val tag: Tag) : TagListEvent()
    data class ReorderTags(val tags: List<Tag>) : TagListEvent()

    /** A drag moved the tag at [from] to [to]; the list follows at once, the save is debounced. */
    data class ReorderTagsLive(val from: Int, val to: Int) : TagListEvent()
    data class ToggleVisibleTag(val tag: Tag) : TagListEvent()
}

data class TagListState(val tagsLoad: LoadState<List<Tag>> = LoadState.Loading, val selectedTagId: Long = 0) {
    val listTags: List<Tag> get() = tagsLoad.valueOrNull().orEmpty()
}
