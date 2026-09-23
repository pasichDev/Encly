package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Tag
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The tag chip the note list is filtered by, shared by the tag row and the note list. */
@Singleton
class SelectedTagHolder @Inject constructor() {
    private val _selectedTagFlow = MutableSharedFlow<Tag>(replay = 1)
    val selectedTagFlow: SharedFlow<Tag> = _selectedTagFlow.asSharedFlow()

    fun selectTag(tag: Tag) {
        _selectedTagFlow.tryEmit(tag)
    }
}
