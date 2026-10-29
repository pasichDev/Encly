package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.data.model.Tag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The tag chip the note list is filtered by, shared by the tag row and the note list. */
@SingleIn(AppScope::class)
@Inject
class SelectedTagHolder {
    private val _selectedTagFlow = MutableSharedFlow<Tag>(replay = 1)
    val selectedTagFlow: SharedFlow<Tag> = _selectedTagFlow.asSharedFlow()

    fun selectTag(tag: Tag) {
        _selectedTagFlow.tryEmit(tag)
    }
}
