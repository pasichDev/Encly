package com.pasich.encly.data.datasource.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.pasich.encly.domain.model.FontStyleType
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
        }
        assertEquals(ThemeSettings(), source.themeSettingsFlow.first())
        assertEquals(FontStyleType.DEFAULT, source.fontStyleFlow.first())
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
