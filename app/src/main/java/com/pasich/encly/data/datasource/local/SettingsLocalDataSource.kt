package com.pasich.encly.data.datasource.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.pasich.encly.domain.enums.NoteSortOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

const val IS_DYNAMIC_THEME = "is_dynamic_theme"
const val THEME_TYPE = "theme_type"
const val IS_GRID = "is_grid_note_list"
const val SCREEN_PROTECTION = "is_screen_protection"
const val SORT_NOTES = "sort"
const val SHOW_TASKS = "show_tasks"
const val SIMPLE_EDIT = "simple_edit"
const val FONT_SIZE = "font_size"
const val FONT_STYLE = "font_style"

enum class ThemeType { SYSTEM, LIGHT, DARK }

enum class FontStyleType {
    MODERN_SIMPLE, // Poppins + Roboto
    COZY_EDITOR,   // Playfair + Source Sans 3
    TECH_MINIMAL   // IBM Plex Sans + Inter
}

class SettingsLocalDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private companion object {
        val isScreenProtection = booleanPreferencesKey(SCREEN_PROTECTION)
        val isDynamicTheme = booleanPreferencesKey(IS_DYNAMIC_THEME)
        val themeType = intPreferencesKey(THEME_TYPE)
        val isGridNoteList = booleanPreferencesKey(IS_GRID)
        val sortNotes = intPreferencesKey(SORT_NOTES)
        val showTasks = booleanPreferencesKey(SHOW_TASKS)
        val simpleEdit = booleanPreferencesKey(SIMPLE_EDIT)
        val fontSize = intPreferencesKey(FONT_SIZE)
        val fontStyle = intPreferencesKey(FONT_STYLE)
    }

    val isScreenProtectionFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[isScreenProtection] == true
    }

    val isGridNoteListFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[isGridNoteList] == true
    }

    val isDynamicThemeFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[isDynamicTheme] == true
    }

    val themeTypeFlow: Flow<ThemeType> = dataStore.data.map { preferences ->
        ThemeType.entries[preferences[themeType] ?: 0]
    }

    val combinedThemeSettingsFlow: Flow<Triple<Boolean, ThemeType, Boolean>> = combine(
        isDynamicThemeFlow, themeTypeFlow, isScreenProtectionFlow
    ) { isDynamic, themeType, isScreenProtection ->
        Triple(isDynamic, themeType, isScreenProtection)
    }

    val getSortNotes: Flow<NoteSortOption> = dataStore.data.map { preferences ->
        val index = preferences[sortNotes] ?: NoteSortOption.UPDATED_DESC.index
        NoteSortOption.fromIndex(index)
    }

    val showTasksFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[showTasks] ?: true // за замовчуванням показувати завдання
    }

    val simpleEditFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[simpleEdit] ?: false // за замовчуванням динамічне редагування
    }

    val fontSizeFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[fontSize] ?: 16 // за замовчуванням 16sp
    }

    val fontStyleFlow: Flow<FontStyleType> = dataStore.data.map { preferences ->
        val index = preferences[fontStyle] ?: 0
        FontStyleType.entries[index]
    }

    fun setSortNotes(option: NoteSortOption, scope: CoroutineScope) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[sortNotes] = option.index
            }
        }
    }

    fun setGridNoteList(value: Boolean, scope: CoroutineScope) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[isGridNoteList] = value
            }
        }
    }

    fun setDynamicTheme(value: Boolean, scope: CoroutineScope) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[isDynamicTheme] = value
            }
        }
    }

    fun setThemeType(value: ThemeType, scope: CoroutineScope) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[themeType] = value.ordinal
            }
        }
    }

    fun setScreenProtection(value: Boolean, scope: CoroutineScope) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[isScreenProtection] = value
            }
        }
    }

    fun setShowTasks(value: Boolean, scope: CoroutineScope) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[showTasks] = value
            }
        }
    }

    fun setSimpleEdit(value: Boolean, scope: CoroutineScope) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[simpleEdit] = value
            }
        }
    }

    fun setFontSize(value: Int, scope: CoroutineScope) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[fontSize] = value.coerceIn(10, 32) // обмежуємо діапазон 10-32
            }
        }
    }

    fun setFontStyle(value: FontStyleType, scope: CoroutineScope) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[fontStyle] = value.ordinal
            }
        }
    }

}
