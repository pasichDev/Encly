package com.pasich.encly.data.datasource.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

const val IS_DYNAMIC_THEME = "is_dynamic_theme"
const val THEME_TYPE = "theme_type"
const val IS_GRID = "is_grid_note_list"
const val SORT_NOTES = "sort"
const val SHOW_TASKS = "show_tasks"
const val SIMPLE_EDIT = "simple_edit"
const val FONT_SIZE = "font_size"
const val FONT_STYLE = "font_style"

@Singleton
class SettingsLocalDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private companion object {
        val isDynamicTheme = booleanPreferencesKey(IS_DYNAMIC_THEME)
        val themeType = intPreferencesKey(THEME_TYPE)
        val isGridNoteList = booleanPreferencesKey(IS_GRID)
        val sortNotes = intPreferencesKey(SORT_NOTES)
        val showTasks = booleanPreferencesKey(SHOW_TASKS)
        val simpleEdit = booleanPreferencesKey(SIMPLE_EDIT)
        val fontSize = intPreferencesKey(FONT_SIZE)
        val fontStyle = intPreferencesKey(FONT_STYLE)
    }

    val isGridNoteListFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[isGridNoteList] == true
    }

    val themeSettingsFlow: Flow<ThemeSettings> = dataStore.data.map { preferences ->
        ThemeSettings(
            dynamic = preferences[isDynamicTheme] == true,
            type = ThemeType.entries[preferences[themeType] ?: 0],
        )
    }.distinctUntilChanged()

    val getSortNotes: Flow<NoteSortOption> = dataStore.data.map { preferences ->
        val index = preferences[sortNotes] ?: NoteSortOption.UPDATED_DESC.index
        NoteSortOption.fromIndex(index)
    }

    val showTasksFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[showTasks] ?: true // show tasks by default
    }

    val simpleEditFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[simpleEdit] ?: false // dynamic editing by default
    }

    val fontSizeFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[fontSize] ?: 16 // 16sp by default
    }

    val fontStyleFlow: Flow<FontStyleType> = dataStore.data.map { preferences ->
        val index = preferences[fontStyle] ?: 0
        FontStyleType.entries[index]
    }

    suspend fun setSortNotes(option: NoteSortOption) {
        dataStore.edit { preferences ->
            preferences[sortNotes] = option.index
        }
    }

    suspend fun setGridNoteList(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[isGridNoteList] = value
        }
    }

    suspend fun setDynamicTheme(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[isDynamicTheme] = value
        }
    }

    suspend fun setThemeType(value: ThemeType) {
        dataStore.edit { preferences ->
            preferences[themeType] = value.ordinal
        }
    }

    suspend fun setShowTasks(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[showTasks] = value
        }
    }

    suspend fun setSimpleEdit(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[simpleEdit] = value
        }
    }

    suspend fun setFontSize(value: Int) {
        dataStore.edit { preferences ->
            preferences[fontSize] = value.coerceIn(10, 32) // clamp to the 10-32 range
        }
    }

    suspend fun setFontStyle(value: FontStyleType) {
        dataStore.edit { preferences ->
            preferences[fontStyle] = value.ordinal
        }
    }
}
