package com.pasich.encly.domain.repository

import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.FontStyleType
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

    suspend fun setDynamicTheme(value: Boolean)
    suspend fun setThemeType(value: ThemeType)
    suspend fun setGridNoteList(value: Boolean)
    suspend fun setSortNotes(option: NoteSortOption)
    suspend fun setShowTasks(value: Boolean)
    suspend fun setSimpleEdit(value: Boolean)
    suspend fun setFontSize(value: Int)
    suspend fun setFontStyle(value: FontStyleType)
}
