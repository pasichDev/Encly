package com.pasich.encly.testutil

import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** Settings in memory: every setter writes the flow the app observes. */
internal class TestSettingsRepository(override var latestThemeSettings: ThemeSettings? = null) : SettingsRepository {
    override val themeSettingsFlow = MutableStateFlow(latestThemeSettings ?: ThemeSettings())
    override val isGridNoteList = MutableStateFlow(false)
    override val getSortNotes = MutableStateFlow(NoteSortOption.UPDATED_DESC)
    override val showTasksFlow = MutableStateFlow(true)
    override val simpleEditFlow = MutableStateFlow(false)
    override val fontSizeFlow = MutableStateFlow(DEFAULT_FONT_SIZE)
    override val fontStyleFlow = MutableStateFlow(FontStyleType.DEFAULT)

    override suspend fun loadThemeSettings(existingInstall: Boolean): ThemeSettings = themeSettingsFlow.value

    override suspend fun setDynamicTheme(value: Boolean) {
        themeSettingsFlow.value = themeSettingsFlow.value.copy(dynamic = value)
    }

    override suspend fun setThemeType(value: ThemeType) {
        themeSettingsFlow.value = themeSettingsFlow.value.copy(type = value)
    }

    override suspend fun setThemePalette(value: ThemePalette) {
        themeSettingsFlow.value = themeSettingsFlow.value.copy(palette = value)
    }

    override suspend fun setGridNoteList(value: Boolean) {
        isGridNoteList.value = value
    }

    override suspend fun setSortNotes(option: NoteSortOption) {
        getSortNotes.value = option
    }

    override suspend fun setShowTasks(value: Boolean) {
        showTasksFlow.value = value
    }

    override suspend fun setSimpleEdit(value: Boolean) {
        simpleEditFlow.value = value
    }

    override suspend fun setFontSize(value: Int) {
        fontSizeFlow.value = value
    }

    override suspend fun setFontStyle(value: FontStyleType) {
        fontStyleFlow.value = value
        themeSettingsFlow.value = themeSettingsFlow.value.copy(fontStyle = value)
    }

    private companion object {
        const val DEFAULT_FONT_SIZE = 16
    }
}
