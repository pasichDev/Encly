package com.pasich.encly.core.di

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.ImportFromMyNotesActivity
import com.pasich.encly.MainActivity
import com.pasich.encly.MyApplication
import com.pasich.encly.core.security.AuthenticationManager
import com.pasich.encly.core.security.AutoLock
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SeedPhraseManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.core.security.VaultLockEvents
import com.pasich.encly.core.security.VaultStore
import com.pasich.encly.data.backup.BackupManager
import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.database.SecureDatabaseManager
import com.pasich.encly.domain.repository.NotesRepository
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.domain.usecase.note.UpdateNoteTrashStatusUseCase
import com.pasich.encly.presentation.editor.persistence.NoteCopyTitle
import com.pasich.encly.presentation.viewmodel.AppearanceViewModel
import com.pasich.encly.presentation.viewmodel.ArchiveViewModel
import com.pasich.encly.presentation.viewmodel.AuthSetupViewModel
import com.pasich.encly.presentation.viewmodel.BackupViewModel
import com.pasich.encly.presentation.viewmodel.EditNoteViewModel
import com.pasich.encly.presentation.viewmodel.ImportFromMyNotesViewModel
import com.pasich.encly.presentation.viewmodel.ImportNotesViewModel
import com.pasich.encly.presentation.viewmodel.LockNowViewModel
import com.pasich.encly.presentation.viewmodel.LockViewModel
import com.pasich.encly.presentation.viewmodel.LossRecoveryViewModel
import com.pasich.encly.presentation.viewmodel.MainListStateViewModel
import com.pasich.encly.presentation.viewmodel.MarkdownExportViewModel
import com.pasich.encly.presentation.viewmodel.NoteListViewModel
import com.pasich.encly.presentation.viewmodel.NoteSearchViewModel
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import com.pasich.encly.presentation.viewmodel.SettingsViewModel
import com.pasich.encly.presentation.viewmodel.StatisticViewModel
import com.pasich.encly.presentation.viewmodel.TagListViewModel
import com.pasich.encly.presentation.viewmodel.TasksViewModel
import com.pasich.encly.presentation.viewmodel.ThemeViewModel
import com.pasich.encly.presentation.viewmodel.TrashViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

/**
 * Everything contributed to [AppScope] (the binding containers and the ViewModels), exposed for
 * the tests. It resolves the same bindings as [AppGraph]; that graph itself is checked below
 * through the application that owns it.
 */
@DependencyGraph(AppScope::class)
interface ContributionsGraph : ViewModelGraph {
    val securityManager: SecurityManager
    val secureDatabaseManager: SecureDatabaseManager
    val databaseProvider: DatabaseProvider
    val sessionLockManager: SessionLockManager
    val vaultLockEvents: VaultLockEvents
    val vaultStore: VaultStore
    val authenticationManager: AuthenticationManager
    val seedPhraseManager: SeedPhraseManager
    val biometricManager: BiometricManager
    val autoLock: AutoLock
    val backupManager: BackupManager
    val notesRepository: NotesRepository
    val settingsRepository: SettingsRepository
    val appFlags: SharedPreferences

    @ApplicationScope
    val applicationScope: CoroutineScope

    @IoDispatcher
    val ioDispatcher: CoroutineDispatcher

    val noteCopyTitle: NoteCopyTitle
    val trashStatusUseCase: UpdateNoteTrashStatusUseCase

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides @ApplicationContext context: Context): ContributionsGraph
    }
}

/** The dependency graph: it builds, its singletons are single, and every ViewModel can be made. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppGraphTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val graph by lazy { createGraphFactory<ContributionsGraph.Factory>().create(context) }
    private val stores = mutableListOf<ViewModelStore>()

    @After
    fun clearViewModels() {
        stores.forEach { it.clear() }
    }

    @Test
    fun theSecuritySingletonsAreOneInstanceEach() {
        assertSame(graph.securityManager, graph.securityManager)
        assertSame(graph.secureDatabaseManager, graph.secureDatabaseManager)
        assertSame(graph.sessionLockManager, graph.sessionLockManager)
        assertSame(graph.vaultStore, graph.vaultStore)
        assertSame(graph.authenticationManager, graph.authenticationManager)
        assertSame(graph.seedPhraseManager, graph.seedPhraseManager)
        assertSame(graph.biometricManager, graph.biometricManager)
        assertSame(graph.autoLock, graph.autoLock)
        assertSame(graph.backupManager, graph.backupManager)
        assertSame(graph.appFlags, graph.appFlags)
        assertSame(graph.applicationScope, graph.applicationScope)
    }

    @Test
    fun aBoundInterfaceIsTheSingletonBehindIt() {
        assertSame(graph.secureDatabaseManager, graph.databaseProvider)
        // The editor and the lock timers listen to this one: it must be the real lock manager.
        assertSame(graph.sessionLockManager, graph.vaultLockEvents)
    }

    @Test
    fun unscopedBindingsAreMadeAnewEachTime() {
        assertNotSame(graph.trashStatusUseCase, graph.trashStatusUseCase)
        assertNotSame(graph.noteCopyTitle, graph.noteCopyTitle)
    }

    @Test
    fun theViewModelsAreTheTwentyTwoOfTheApp() {
        assertEquals(PLAIN_VIEW_MODELS, graph.viewModelProviders.keys)
        assertEquals(ASSISTED_VIEW_MODELS, graph.assistedFactoryProviders.keys)
        assertEquals(22, PLAIN_VIEW_MODELS.size + ASSISTED_VIEW_MODELS.size)
        assertTrue(graph.manualAssistedFactoryProviders.isEmpty())
    }

    @Test
    fun everyViewModelCanBeCreatedThroughTheFactory() {
        val extras = MutableCreationExtras(activityExtras())
        (PLAIN_VIEW_MODELS + ASSISTED_VIEW_MODELS).forEach { type ->
            val store = ViewModelStore().also(stores::add)
            val created = ViewModelProvider.create(store, graph.metroViewModelFactory, extras)[type]
            assertNotNull(type.simpleName, created)
            assertTrue(type.simpleName, type.isInstance(created))
        }
    }

    @Test
    fun aViewModelIsNewForEveryStore() {
        val first = ViewModelStore().also(stores::add)
        val second = ViewModelStore().also(stores::add)
        val extras = activityExtras()

        val a = ViewModelProvider.create(first, graph.metroViewModelFactory, extras)[SettingsViewModel::class]
        val b = ViewModelProvider.create(second, graph.metroViewModelFactory, extras)[SettingsViewModel::class]

        assertNotSame(a, b)
    }

    @Test
    fun anAssistedViewModelGetsTheNavigationArgumentsAsItsSavedState() {
        val args = Bundle().apply { putBoolean("isReadTrashOnly", true) }
        val extras = MutableCreationExtras(activityExtras()).apply { set(DEFAULT_ARGS_KEY, args) }
        val store = ViewModelStore().also(stores::add)

        val editor = ViewModelProvider.create(store, graph.metroViewModelFactory, extras)[EditNoteViewModel::class]

        assertTrue(editor.isReadTrashOnly)
        assertTrue(editor.isNewNote)
    }

    @Test
    fun theApplicationGraphGivesTheActivitiesAndTheApplicationTheSameSingletons() {
        val appGraph = ApplicationProvider.getApplicationContext<MyApplication>().appGraph
        val main = MainActivity().also { appGraph.inject(it) }
        val otherMain = MainActivity().also { appGraph.inject(it) }
        val handoff = ImportFromMyNotesActivity().also { appGraph.inject(it) }
        val application = MyApplication().also { appGraph.inject(it) }

        assertSame(main.securityManager, otherMain.securityManager)
        assertSame(main.securityManager, handoff.securityManager)
        assertSame(main.sessionLockManager, otherMain.sessionLockManager)
        assertSame(main.sessionLockManager, application.sessionLockManager)
        assertSame(main.settingsRepository, handoff.settingsRepository)
        assertSame(main.keyboardPrivacy, handoff.keyboardPrivacy)
        assertSame(main.viewModelFactory, handoff.viewModelFactory)
    }

    @Test
    fun theApplicationGraphIsTheOneTheApplicationOwns() {
        val application = ApplicationProvider.getApplicationContext<MyApplication>()

        assertSame(application.appGraph, application.appGraph)
    }

    /** What a real Activity hands a ViewModel factory: the saved-state and store owners. */
    private fun activityExtras() = Robolectric.buildActivity(ComponentActivity::class.java).create().get()
        .defaultViewModelCreationExtras

    private companion object {
        val PLAIN_VIEW_MODELS: Set<KClass<out ViewModel>> = setOf(
            AppearanceViewModel::class,
            ArchiveViewModel::class,
            AuthSetupViewModel::class,
            BackupViewModel::class,
            ImportNotesViewModel::class,
            LockNowViewModel::class,
            LockViewModel::class,
            LossRecoveryViewModel::class,
            MainListStateViewModel::class,
            MarkdownExportViewModel::class,
            NoteListViewModel::class,
            NoteSearchViewModel::class,
            OnboardingViewModel::class,
            SecuritySettingsViewModel::class,
            SettingsViewModel::class,
            StatisticViewModel::class,
            TagListViewModel::class,
            ThemeViewModel::class,
            TrashViewModel::class,
        )

        /** The ViewModels that take a SavedStateHandle. */
        val ASSISTED_VIEW_MODELS: Set<KClass<out ViewModel>> = setOf(
            EditNoteViewModel::class,
            ImportFromMyNotesViewModel::class,
            TasksViewModel::class,
        )
    }
}
