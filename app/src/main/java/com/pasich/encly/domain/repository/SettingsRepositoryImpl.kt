package com.pasich.encly.domain.repository

import com.pasich.encly.data.datasource.local.FontStyleType
import com.pasich.encly.data.datasource.local.SettingsLocalDataSource
import com.pasich.encly.data.datasource.local.ThemeType
import com.pasich.encly.data.repository.SettingsRepository
import com.pasich.encly.domain.enums.NoteSortOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val settingsLocalDataSource: SettingsLocalDataSource
) : SettingsRepository {

    override val isDynamicThemeFlow: Flow<Boolean> = settingsLocalDataSource.isDynamicThemeFlow
    override val themeTypeFlow: Flow<ThemeType> = settingsLocalDataSource.themeTypeFlow
    override val combinedThemeSettingsFlow: Flow<Triple<Boolean, ThemeType, Boolean>> =
        settingsLocalDataSource.combinedThemeSettingsFlow

    override val isGridNoteList: Flow<Boolean> = settingsLocalDataSource.isGridNoteListFlow
    override val getSortNotes: Flow<NoteSortOption> = settingsLocalDataSource.getSortNotes
    override val showTasksFlow: Flow<Boolean> = settingsLocalDataSource.showTasksFlow
    override val simpleEditFlow: Flow<Boolean> = settingsLocalDataSource.simpleEditFlow
    override val fontSizeFlow: Flow<Int> = settingsLocalDataSource.fontSizeFlow
    override val fontStyleFlow: Flow<FontStyleType> = settingsLocalDataSource.fontStyleFlow

    override fun setDynamicTheme(value: Boolean, scope: CoroutineScope) =
        settingsLocalDataSource.setDynamicTheme(value, scope)

    override fun setScreenProtection(
        value: Boolean,
        scope: CoroutineScope
    ) = settingsLocalDataSource.setScreenProtection(value, scope)

    override fun setThemeType(value: ThemeType, scope: CoroutineScope) =
        settingsLocalDataSource.setThemeType(value, scope)

    override fun setGridNoteList(value: Boolean, scope: CoroutineScope) =
        settingsLocalDataSource.setGridNoteList(value, scope)

    override fun setSortNotes(option: NoteSortOption, scope: CoroutineScope) =
        settingsLocalDataSource.setSortNotes(option, scope)

    override fun setShowTasks(value: Boolean, scope: CoroutineScope) =
        settingsLocalDataSource.setShowTasks(value, scope)

    override fun setSimpleEdit(value: Boolean, scope: CoroutineScope) =
        settingsLocalDataSource.setSimpleEdit(value, scope)

    override fun setFontSize(value: Int, scope: CoroutineScope) =
        settingsLocalDataSource.setFontSize(value, scope)

    override fun setFontStyle(value: FontStyleType, scope: CoroutineScope) =
        settingsLocalDataSource.setFontStyle(value, scope)


}
