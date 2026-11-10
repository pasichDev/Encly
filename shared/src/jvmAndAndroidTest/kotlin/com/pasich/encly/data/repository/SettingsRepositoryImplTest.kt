package com.pasich.encly.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pasich.encly.data.datasource.local.SettingsLocalDataSource
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Every setting the repository writes reads back through the flow the app observes. */
class SettingsRepositoryImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository by lazy {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            folder.newFile("settings.preferences_pb").also { it.delete() }
        }
        SettingsRepositoryImpl(SettingsLocalDataSource(dataStore))
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun listSettingsRoundTrip() = runTest {
        assertFalse(repository.isGridNoteList.first())
        assertEquals(NoteSortOption.UPDATED_DESC, repository.getSortNotes.first())

        repository.setGridNoteList(true)
        repository.setSortNotes(NoteSortOption.CREATED_ASC)

        assertTrue(repository.isGridNoteList.first())
        assertEquals(NoteSortOption.CREATED_ASC, repository.getSortNotes.first())
    }

    @Test
    fun editorAndTaskSettingsRoundTrip() = runTest {
        repository.setShowTasks(false)
        repository.setSimpleEdit(true)
        repository.setFontSize(FONT_SIZE)
        repository.setFontStyle(FontStyleType.MODERN_SIMPLE)

        assertFalse(repository.showTasksFlow.first())
        assertTrue(repository.simpleEditFlow.first())
        assertEquals(FONT_SIZE, repository.fontSizeFlow.first())
        assertEquals(FontStyleType.MODERN_SIMPLE, repository.fontStyleFlow.first())
    }

    @Test
    fun theThemeIsLoadedAndRememberedForTheFirstFrame() = runTest {
        repository.setThemePalette(ThemePalette.GRAPHITE)
        repository.setThemeType(ThemeType.LIGHT)
        repository.setDynamicTheme(false)

        val loaded = repository.loadThemeSettings(existingInstall = false)

        assertEquals(ThemePalette.GRAPHITE, loaded.palette)
        assertEquals(ThemeType.LIGHT, loaded.type)
        assertEquals(loaded, repository.themeSettingsFlow.first())
        assertEquals(loaded, repository.latestThemeSettings)
    }

    private companion object {
        const val FONT_SIZE = 20
    }
}
