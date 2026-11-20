package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.pasich.encly.domain.model.NotePreviews
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.StateFlow

/** Whether the note lists show a preview of the text (see [NotePreviews]). */
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class NotePreviewsViewModel(private val previews: NotePreviews) : ViewModel() {
    val show: StateFlow<Boolean> = previews.show

    fun setShow(enabled: Boolean) = previews.setShow(enabled)
}
