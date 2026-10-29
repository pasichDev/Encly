package com.pasich.encly.data.datasource.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.domain.model.SwipeAction
import com.pasich.encly.domain.model.SwipeSettings
import com.pasich.encly.domain.model.SwipeSide
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsLocalDataSourceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = scope) {
            folder.newFile("settings.preferences_pb").also { it.delete() }
        }
    }
    private val source by lazy { SettingsLocalDataSource(dataStore) }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun emptyStoreGivesDefaults() = runTest {
        assertEquals(ThemeSettings(), source.themeSettingsFlow.first())
        assertEquals(FontStyleType.DEFAULT, source.fontStyleFlow.first())
        assertEquals(NoteLineSpacing.DEFAULT, source.noteLineSpacingFlow.first())
    }

    @Test
    fun legacyPerNoteTextKeysAreRemovedAndOtherSettingsKept() = runTest {
        dataStore.edit {
            it[intPreferencesKey("note_text_size_7")] = 24
            it[intPreferencesKey("note_line_spacing_7")] = 1
            it[intPreferencesKey("note_text_size_1200")] = 12
        }
        source.setFontSize(20)

        source.removeLegacyNoteTextKeys()

        val keys = dataStore.data.first().asMap().keys.map { it.name }
        assertEquals(listOf("font_size"), keys)
        assertEquals(20, source.fontSizeFlow.first())
    }

    @Test
    fun removingLegacyKeysWithNoneLeftChangesNothing() = runTest {
        source.setFontSize(20)

        source.removeLegacyNoteTextKeys()

        assertEquals(20, source.fontSizeFlow.first())
    }

    @Test
    fun swipeDefaultsPinRightArchiveLeft() = runTest {
        assertEquals(
            SwipeSettings(enabled = true, right = SwipeAction.PIN, left = SwipeAction.ARCHIVE),
            source.swipeSettingsFlow.first(),
        )
    }

    @Test
    fun swipeSettingsRoundTrip() = runTest {
        source.setSwipeEnabled(false)
        source.setSwipeAction(SwipeSide.RIGHT, SwipeAction.NEXT_COLOR)
        source.setSwipeAction(SwipeSide.LEFT, SwipeAction.DELETE)

        assertEquals(SwipeSettings(false, SwipeAction.NEXT_COLOR, SwipeAction.DELETE), source.swipeSettingsFlow.first())
    }

    @Test
    fun anUnknownStoredSwipeActionFallsBackToTheDefault() = runTest {
        dataStore.edit { it[intPreferencesKey(SWIPE_RIGHT)] = 99 }

        assertEquals(SwipeAction.PIN, source.swipeSettingsFlow.first().right)
    }

    @Test
    fun compactLineSpacingRoundTrips() = runTest {
        source.setNoteLineSpacing(NoteLineSpacing.COMPACT)

        assertEquals(NoteLineSpacing.COMPACT, SettingsLocalDataSource(dataStore).noteLineSpacingFlow.first())
        source.setNoteLineSpacing(NoteLineSpacing.DEFAULT)
        assertEquals(NoteLineSpacing.DEFAULT, source.noteLineSpacingFlow.first())
    }

    @Test
    fun storedLineSpacingValuesKeepTheirMeaning() = runTest {
        // The preference is the enum's ordinal: 0 and 1 were written by earlier versions.
        assertEquals(0, NoteLineSpacing.DEFAULT.ordinal)
        assertEquals(1, NoteLineSpacing.COMPACT.ordinal)
        assertEquals(2, NoteLineSpacing.COMFORTABLE.ordinal)
        val expected = mapOf(
            0 to NoteLineSpacing.DEFAULT,
            1 to NoteLineSpacing.COMPACT,
            2 to NoteLineSpacing.COMFORTABLE,
        )
        expected.forEach { (stored, spacing) ->
            dataStore.edit { it[intPreferencesKey(NOTE_LINE_SPACING)] = stored }
            assertEquals(spacing, source.noteLineSpacingFlow.first())
        }
        source.setNoteLineSpacing(NoteLineSpacing.COMFORTABLE)
        assertEquals(2, dataStore.data.first()[intPreferencesKey(NOTE_LINE_SPACING)])
    }

    @Test
    fun appearanceRoundTrips() = runTest {
        source.setThemePalette(ThemePalette.GRAPHITE)
        source.setThemeType(ThemeType.DARK)
        source.setDynamicTheme(true)
        source.setFontStyle(FontStyleType.MODERN_SIMPLE)
        assertEquals(
            ThemeSettings(
                dynamic = true,
                type = ThemeType.DARK,
                palette = ThemePalette.GRAPHITE,
                fontStyle = FontStyleType.MODERN_SIMPLE,
            ),
            source.themeSettingsFlow.first(),
        )
    }

    @Test
    fun unknownOrdinalsFallBackInsteadOfCrashing() = runTest {
        dataStore.edit {
            it[intPreferencesKey(THEME_PALETTE)] = 99
            it[intPreferencesKey(THEME_TYPE)] = -1
            it[intPreferencesKey(FONT_STYLE)] = 7
            it[intPreferencesKey(NOTE_LINE_SPACING)] = 7
        }
        assertEquals(ThemeSettings(), source.themeSettingsFlow.first())
        assertEquals(FontStyleType.DEFAULT, source.fontStyleFlow.first())
        assertEquals(NoteLineSpacing.DEFAULT, source.noteLineSpacingFlow.first())
    }

    @Test
    fun anExistingInstallWithoutAPaletteKeepsForestAndItsFont() = runTest {
        source.migrateAppearance(existingInstall = true)

        val settings = source.themeSettingsFlow.first()
        assertEquals(ThemePalette.FOREST, settings.palette)
        assertEquals(FontStyleType.MODERN_SIMPLE, settings.fontStyle)
        assertEquals(settings, source.latestThemeSettings)
    }

    @Test
    fun anExistingInstallKeepsAFontItPicked() = runTest {
        source.setFontStyle(FontStyleType.TECH_MINIMAL)

        source.migrateAppearance(existingInstall = true)

        assertEquals(FontStyleType.TECH_MINIMAL, source.themeSettingsFlow.first().fontStyle)
        assertEquals(ThemePalette.FOREST, source.themeSettingsFlow.first().palette)
    }

    @Test
    fun aFreshInstallGetsTheNewDefaultsAndIsNeverMigratedLater() = runTest {
        source.migrateAppearance(existingInstall = false)
        // Onboarding completes later; the next start must not switch it to Forest.
        source.migrateAppearance(existingInstall = true)

        assertEquals(ThemeSettings(), source.themeSettingsFlow.first())
    }

    @Test
    fun theMigrationRunsOnlyOnce() = runTest {
        source.migrateAppearance(existingInstall = true)
        source.setThemePalette(ThemePalette.OCEAN)

        source.migrateAppearance(existingInstall = true)

        assertEquals(ThemePalette.OCEAN, source.themeSettingsFlow.first().palette)
    }
}
