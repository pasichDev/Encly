package com.pasich.encly.domain.repository

import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeSettingsFlow: Flow<ThemeSettings>
    val isGridNoteList: Flow<Boolean>
    val getSortNotes: Flow<NoteSortOption>
    val showTasksFlow: Flow<Boolean>
    val simpleEditFlow: Flow<Boolean>
    val fontSizeFlow: Flow<Int>
    val fontStyleFlow: Flow<FontStyleType>

    /** The theme last read from the store, or null before the first read. */
    val latestThemeSettings: ThemeSettings?

    /**
     * Brings the appearance of an install set up by an older version forward (once), then reads
     * the theme, so startup can hold the splash until the first frame can be drawn right.
     */
    suspend fun loadThemeSettings(existingInstall: Boolean): ThemeSettings

    suspend fun setDynamicTheme(value: Boolean)
    suspend fun setThemeType(value: ThemeType)
    suspend fun setThemePalette(value: ThemePalette)
    suspend fun setGridNoteList(value: Boolean)
    suspend fun setSortNotes(option: NoteSortOption)
    suspend fun setShowTasks(value: Boolean)
    suspend fun setSimpleEdit(value: Boolean)
    suspend fun setFontSize(value: Int)
    suspend fun setFontStyle(value: FontStyleType)
}
