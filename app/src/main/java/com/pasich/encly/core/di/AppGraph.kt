package com.pasich.encly.core.di

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import com.pasich.encly.ImportFromMyNotesActivity
import com.pasich.encly.MainActivity
import com.pasich.encly.MyApplication
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlin.reflect.KClass

/** Qualifier for the application [Context]: the only Context the graph provides. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationContext

/**
 * The one dependency graph of the process. Every `@SingleIn(AppScope::class)` binding lives as
 * long as it; the bindings themselves are contributed to [AppScope] by their own declarations
 * (the binding containers named *Module, `@ContributesIntoMap` on the ViewModels).
 *
 * Android creates the Application, the Activities and the ViewModels, so none of them can take
 * constructor arguments: the Application and the Activities are injected as members through
 * the `inject` functions below, the ViewModels through [ViewModelGraph.metroViewModelFactory].
 */
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {

    fun inject(application: MyApplication)

    fun inject(activity: MainActivity)

    fun inject(activity: ImportFromMyNotesActivity)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides @ApplicationContext context: Context): AppGraph
    }
}

/** Creates the app's ViewModels from the graph's multibindings (see `MetroViewModelFactory`). */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AppViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders:
    Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
) : MetroViewModelFactory()

/** Implemented by the Application: the only way to reach the graph from the UI layer. */
interface AppGraphOwner {
    val appGraph: AppGraph
}

/** The app graph, for Activities that inject themselves in `onCreate`. */
internal val Activity.appGraph: AppGraph
    get() = (application as AppGraphOwner).appGraph
