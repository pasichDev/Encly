package com.pasich.encly.data.repository

import com.pasich.encly.data.datasource.local.FontStyleType
import com.pasich.encly.data.datasource.local.ThemeType
import com.pasich.encly.domain.enums.NoteSortOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val isDynamicThemeFlow: Flow<Boolean>
    val themeTypeFlow: Flow<ThemeType>
    val combinedThemeSettingsFlow:  Flow<Triple<Boolean, ThemeType, Boolean>>
    val isGridNoteList: Flow<Boolean>
    val getSortNotes: Flow<NoteSortOption>
    val showTasksFlow: Flow<Boolean>
    val simpleEditFlow: Flow<Boolean>
    val fontSizeFlow: Flow<Int>
    val fontStyleFlow: Flow<FontStyleType>

     fun setDynamicTheme(value: Boolean, scope: CoroutineScope)
     fun setScreenProtection(value: Boolean, scope: CoroutineScope)
     fun setThemeType(value: ThemeType, scope: CoroutineScope)
     fun setGridNoteList(value: Boolean, scope: CoroutineScope)
     fun setSortNotes(option: NoteSortOption, scope: CoroutineScope)
     fun setShowTasks(value: Boolean, scope: CoroutineScope)
     fun setSimpleEdit(value: Boolean, scope: CoroutineScope)
     fun setFontSize(value: Int, scope: CoroutineScope)
     fun setFontStyle(value: FontStyleType, scope: CoroutineScope)
}
