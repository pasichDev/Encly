package com.pasich.encly.domain.repository

import com.pasich.encly.data.model.Tag
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

class TagSelectionRepository @Inject constructor() {
    private val _selectedTagFlow = MutableSharedFlow<Tag>(replay = 1)
    val selectedTagFlow: SharedFlow<Tag> = _selectedTagFlow.asSharedFlow()

    fun selectTag(tag: Tag) {
        _selectedTagFlow.tryEmit(tag)
    }

    fun clearSelection() {
        _selectedTagFlow.tryEmit(Tag(id = 0, nameTag = "All"))
    }
}