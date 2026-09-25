package com.pasich.encly.data.datasource.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

const val IS_DYNAMIC_THEME = "is_dynamic_theme"
const val THEME_TYPE = "theme_type"
const val THEME_PALETTE = "theme_palette"
const val IS_GRID = "is_grid_note_list"
const val SORT_NOTES = "sort"
const val SHOW_TASKS = "show_tasks"
const val SIMPLE_EDIT = "simple_edit"
const val FONT_SIZE = "font_size"
const val FONT_STYLE = "font_style"
const val APPEARANCE_MIGRATED = "appearance_migrated_v1"

@Singleton
class SettingsLocalDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private companion object {
        val isDynamicTheme = booleanPreferencesKey(IS_DYNAMIC_THEME)
        val themeType = intPreferencesKey(THEME_TYPE)
        val themePalette = intPreferencesKey(THEME_PALETTE)
        val isGridNoteList = booleanPreferencesKey(IS_GRID)
        val sortNotes = intPreferencesKey(SORT_NOTES)
        val showTasks = booleanPreferencesKey(SHOW_TASKS)
        val simpleEdit = booleanPreferencesKey(SIMPLE_EDIT)
        val fontSize = intPreferencesKey(FONT_SIZE)
        val fontStyle = intPreferencesKey(FONT_STYLE)
        val appearanceMigrated = booleanPreferencesKey(APPEARANCE_MIGRATED)
    }

    /** The theme last read from the store, so the first frame after the splash is already right. */
    @Volatile
    var latestThemeSettings: ThemeSettings? = null
        private set

    val isGridNoteListFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[isGridNoteList] == true
    }

    val themeSettingsFlow: Flow<ThemeSettings> = dataStore.data.map { preferences ->
        ThemeSettings(
            dynamic = preferences[isDynamicTheme] == true,
            type = enumAtOrDefault(preferences[themeType], ThemeType.SYSTEM),
            palette = enumAtOrDefault(preferences[themePalette], ThemePalette.DEFAULT),
            fontStyle = enumAtOrDefault(preferences[fontStyle], FontStyleType.DEFAULT),
        )
    }.distinctUntilChanged().onEach { latestThemeSettings = it }

    /**
     * Runs once, on the first start of a version with palettes and new font defaults. Installs
     * set up before it had the Forest (green) scheme and, unless a font was picked, the Modern
     * font set (`fontStyle ?: 0`); they keep both instead of switching to the new defaults
     * (Paper, Editorial). A fresh install, [existingInstall] false, gets the new defaults.
     */
    suspend fun migrateAppearance(existingInstall: Boolean) {
        if (dataStore.data.first()[appearanceMigrated] == true) return
        dataStore.edit { preferences ->
            if (preferences[appearanceMigrated] == true) return@edit
            if (existingInstall) {
                if (preferences[themePalette] == null) preferences[themePalette] = ThemePalette.FOREST.ordinal
                if (preferences[fontStyle] == null) preferences[fontStyle] = FontStyleType.MODERN_SIMPLE.ordinal
            }
            preferences[appearanceMigrated] = true
        }
    }

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
        enumAtOrDefault(preferences[fontStyle], FontStyleType.DEFAULT)
    }.distinctUntilChanged()

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

    suspend fun setThemePalette(value: ThemePalette) {
        dataStore.edit { preferences ->
            preferences[themePalette] = value.ordinal
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

/**
 * Reads a persisted enum ordinal. An absent key or an ordinal this version does not know (a
 * downgrade, a corrupted file) falls back to [default] instead of crashing the first frame.
 */
internal inline fun <reified T : Enum<T>> enumAtOrDefault(ordinal: Int?, default: T): T =
    ordinal?.let { enumValues<T>().getOrNull(it) } ?: default
