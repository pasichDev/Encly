package com.pasich.encly.core.di

import androidx.lifecycle.ViewModel
import com.pasich.encly.presentation.viewmodel.SelectedTagHolder
import com.pasich.encly.presentation.viewmodel.WindowSelections
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

/**
 * Creates the app's ViewModels from the graph's multibindings (see `MetroViewModelFactory`). The same factory
 * serves the Android and the desktop graph, which both pick it up from `AppScope`.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AppViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders:
    Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
) : MetroViewModelFactory() {
    /**
     * The same ViewModels for one window of a host with several: every ViewModel it creates gets [selectedTag], the
     * tag filter of that window, instead of the one the single-window hosts share.
     */
    fun forWindow(selectedTag: SelectedTagHolder): MetroViewModelFactory = object : MetroViewModelFactory() {
        override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel> =
            this@AppViewModelFactory.viewModelProviders.mapValues { (_, provider) ->
                { WindowSelections.creatingFor(selectedTag) { provider() } }
            }
        override val assistedFactoryProviders = this@AppViewModelFactory.assistedFactoryProviders
        override val manualAssistedFactoryProviders = this@AppViewModelFactory.manualAssistedFactoryProviders
    }
}
