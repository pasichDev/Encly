package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.LifecycleOwner
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SessionLockManager
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.testutil.TestSettingsRepository
import com.pasich.encly.testutil.TestTagsRepository
import com.pasich.encly.testutil.TestTasksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** The small ViewModels behind settings switches, the theme, the list layout and the drawer. */
@OptIn(ExperimentalCoroutinesApi::class)
class SmallViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = TestSettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun settingsSwitchesStartFromDefaultsThenFollowTheStore() = runTest(dispatcher) {
        settings.showTasksFlow.value = false
        settings.simpleEditFlow.value = true
        val viewModel = SettingsViewModel(settings)
        assertTrue("default before the store is read", viewModel.showTasksFlow.value)
        assertFalse(viewModel.simpleEditFlow.value)

        advanceUntilIdle()

        assertFalse(viewModel.showTasksFlow.value)
        assertTrue(viewModel.simpleEditFlow.value)
    }

    @Test
    fun settingsSwitchesAreStored() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(settings)

        viewModel.onEvent(SettingsEvent.UpdateShowTasks(false))
        viewModel.onEvent(SettingsEvent.UpdateSimpleEdit(true))
        advanceUntilIdle()

        assertFalse(settings.showTasksFlow.value)
        assertTrue(settings.simpleEditFlow.value)
        assertFalse(viewModel.showTasksFlow.value)
        assertTrue(viewModel.simpleEditFlow.value)
    }

    @Test
    fun theLanguageDialogOpensAndCloses() {
        val viewModel = SettingsViewModel(settings)

        viewModel.setLanguageDialogVisibility(true)
        assertTrue(viewModel.languageDialogVisible.value)
        viewModel.setLanguageDialogVisibility(false)
        assertFalse(viewModel.languageDialogVisible.value)
    }

    @Test
    fun theThemeStartsFromTheLastReadThemeNotTheDefaults() = runTest(dispatcher) {
        val stored = ThemeSettings(type = ThemeType.DARK, palette = ThemePalette.GRAPHITE)
        val viewModel = ThemeViewModel(TestSettingsRepository(latestThemeSettings = stored))

        assertEquals(stored, viewModel.themeSettingsFlow.value)
    }

    @Test
    fun theThemeFollowsChangesWhileObserved() = runTest(dispatcher) {
        val viewModel = ThemeViewModel(settings)
        assertEquals(ThemeSettings(), viewModel.themeSettingsFlow.value)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.themeSettingsFlow.collect {} }

        settings.setThemePalette(ThemePalette.GRAPHITE)
        advanceUntilIdle()

        assertEquals(ThemePalette.GRAPHITE, viewModel.themeSettingsFlow.value.palette)
    }

    @Test
    fun theListLayoutTogglesBetweenListAndGrid() = runTest(dispatcher) {
        val viewModel = MainListStateViewModel(settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isGridNoteList.collect {} }
        advanceUntilIdle()

        viewModel.updateGridNoteList()
        advanceUntilIdle()
        assertTrue(settings.isGridNoteList.value)
        assertTrue(viewModel.isGridNoteList.value)

        viewModel.updateGridNoteList()
        advanceUntilIdle()
        assertFalse(viewModel.isGridNoteList.value)
    }

    @Test
    fun theDrawerCountsTagsAndOpenTasks() = runTest(dispatcher) {
        val tags = TestTagsRepository(listOf(Tag(id = 1), Tag(id = 2, position = 1)))
        val tasks = TestTasksRepository(
            listOf(Task(id = 1, title = "a"), Task(id = 2, title = "b"), Task(id = 3, title = "c", isCompleted = true)),
        )
        val viewModel = StatisticViewModel(tags, tasks)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.totalTagsCreated.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.openTasksCount.collect {} }
        advanceUntilIdle()

        assertEquals(2, viewModel.totalTagsCreated.value)
        assertEquals(2, viewModel.openTasksCount.value)

        tasks.updateTaskStatus(1, isCompleted = true, completedDate = 1L)
        advanceUntilIdle()
        assertEquals(1, viewModel.openTasksCount.value)
    }

    @Test
    fun lockNowClosesAnOpenVault() {
        val security = mock(SecurityManager::class.java)
        `when`(security.isLockable()).thenReturn(true)
        `when`(security.isDatabaseUnlocked()).thenReturn(true)
        val sessionLock = SessionLockManager(security).apply { onStart(mock(LifecycleOwner::class.java)) }

        LockNowViewModel(sessionLock).lockNow()

        verify(security).lock()
        assertTrue(sessionLock.locked.value)
    }

    @Test
    fun lockNowBeforeSetupDoesNothing() {
        val security = mock(SecurityManager::class.java)
        `when`(security.isLockable()).thenReturn(false)

        LockNowViewModel(SessionLockManager(security)).lockNow()

        verify(security, never()).lock()
    }

    @Test
    fun theDamagedVaultScreenWipesEverything() {
        val security = mock(SecurityManager::class.java)

        LossRecoveryViewModel(security).wipeAllData()

        verify(security).wipeAndReset()
    }

    @Test
    fun theSelectedTagIsReplayedToLateSubscribers() = runTest(dispatcher) {
        val holder = SelectedTagHolder()
        holder.selectTag(Tag(id = 1, nameTag = "Work"))
        holder.selectTag(Tag(id = 2, nameTag = "Home"))

        assertEquals("Home", holder.selectedTagFlow.first().nameTag)
    }
}
