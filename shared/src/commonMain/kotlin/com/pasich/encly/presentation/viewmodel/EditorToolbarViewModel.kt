package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.pasich.encly.domain.model.EditorToolbar
import com.pasich.encly.domain.model.ToolbarLayout
import com.pasich.encly.domain.model.ToolbarTool
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.StateFlow

/** Which tools the editor toolbar offers and in what order (see [EditorToolbar]); every change is saved at once. */
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class EditorToolbarViewModel(private val toolbar: EditorToolbar) : ViewModel() {
    val layout: StateFlow<ToolbarLayout> = toolbar.layout

    /** Moves [tool] [steps] places down the list of all tools (up when negative); see [ToolbarLayout.moved]. */
    fun move(tool: ToolbarTool, steps: Int) = toolbar.set(toolbar.layout.value.moved(tool, steps))

    /** Hides [tool], or shows it again. */
    fun toggle(tool: ToolbarTool) = toolbar.set(toolbar.layout.value.toggled(tool))

    /** Back to the toolbar as it ships. */
    fun reset() = toolbar.reset()
}
