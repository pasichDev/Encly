package com.pasich.encly.data.datasource.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.pasich.encly.domain.enums.NoteSortOption
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.domain.model.SwipeAction
import com.pasich.encly.domain.model.SwipeSettings
import com.pasich.encly.domain.model.SwipeSide
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

const val IS_DYNAMIC_THEME = "is_dynamic_theme"
const val THEME_TYPE = "theme_type"
const val THEME_PALETTE = "theme_palette"
const val IS_GRID = "is_grid_note_list"
const val SORT_NOTES = "sort"
const val SHOW_TASKS = "show_tasks"
const val SIMPLE_EDIT = "simple_edit"
const val FONT_SIZE = "font_size"
const val FONT_STYLE = "font_style"
const val NOTE_LINE_SPACING = "note_line_spacing"
const val MIN_FONT_SIZE = 10
const val MAX_FONT_SIZE = 32
const val SWIPE_ENABLED = "swipe_enabled"
const val SWIPE_RIGHT = "swipe_right"
const val SWIPE_LEFT = "swipe_left"
const val APPEARANCE_MIGRATED = "appearance_migrated_v1"

@SingleIn(AppScope::class)
@Suppress("TooManyFunctions") // One small getter/setter pair per persisted editor or display setting.
@Inject
class SettingsLocalDataSource(private val dataStore: DataStore<Preferences>) {

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
        val noteLineSpacing = intPreferencesKey(NOTE_LINE_SPACING)
        val swipeEnabled = booleanPreferencesKey(SWIPE_ENABLED)
        val swipeRight = intPreferencesKey(SWIPE_RIGHT)
        val swipeLeft = intPreferencesKey(SWIPE_LEFT)
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

    val noteLineSpacingFlow: Flow<NoteLineSpacing> = dataStore.data.map { preferences ->
        enumAtOrDefault(preferences[noteLineSpacing], NoteLineSpacing.DEFAULT)
    }.distinctUntilChanged()

    val swipeSettingsFlow: Flow<SwipeSettings> = dataStore.data.map { preferences ->
        SwipeSettings(
            enabled = preferences[swipeEnabled] ?: true,
            right = enumAtOrDefault(preferences[swipeRight], SwipeAction.PIN),
            left = enumAtOrDefault(preferences[swipeLeft], SwipeAction.ARCHIVE),
        )
    }.distinctUntilChanged()

    /**
     * A development build kept a note's own text size and line spacing here, by note id. They live
     * on the note in the vault now (the ids were also the same in the real and the decoy vault, and
     * outlived an erase), so any such key is removed.
     */
    suspend fun removeLegacyNoteTextKeys() {
        if (dataStore.data.first().asMap().keys.none { it.name.isLegacyNoteTextKey() }) return
        dataStore.edit { preferences ->
            preferences.asMap().keys.filter { it.name.isLegacyNoteTextKey() }.forEach { preferences.remove(it) }
        }
    }

    private fun String.isLegacyNoteTextKey() = startsWith("note_text_size_") || startsWith("note_line_spacing_")

    suspend fun setSwipeEnabled(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[swipeEnabled] = value
        }
    }

    suspend fun setSwipeAction(side: SwipeSide, action: SwipeAction) {
        dataStore.edit { preferences ->
            preferences[if (side == SwipeSide.RIGHT) swipeRight else swipeLeft] = action.ordinal
        }
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
            preferences[fontSize] = value.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        }
    }

    suspend fun setFontStyle(value: FontStyleType) {
        dataStore.edit { preferences ->
            preferences[fontStyle] = value.ordinal
        }
    }

    suspend fun setNoteLineSpacing(value: NoteLineSpacing) {
        dataStore.edit { preferences ->
            preferences[noteLineSpacing] = value.ordinal
        }
    }
}

/**
 * Reads a persisted enum ordinal. An absent key or an ordinal this version does not know (a
 * downgrade, a corrupted file) falls back to [default] instead of crashing the first frame.
 */
internal inline fun <reified T : Enum<T>> enumAtOrDefault(ordinal: Int?, default: T): T =
    ordinal?.let { enumValues<T>().getOrNull(it) } ?: default
