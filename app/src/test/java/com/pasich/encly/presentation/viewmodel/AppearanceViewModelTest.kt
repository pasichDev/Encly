package com.pasich.encly.presentation.viewmodel

import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.utils.DeviceCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {

    private val stored = MutableStateFlow(ThemeSettings())
    private val repository = mock(SettingsRepository::class.java)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        `when`(repository.themeSettingsFlow).thenReturn(stored)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AppearanceViewModel(repository, mock(DeviceCapabilities::class.java))

    @Test
    fun choicesArePersisted() = runTest {
        val vm = viewModel()
        vm.selectPalette(ThemePalette.OCEAN)
        vm.selectMode(ThemeType.DARK)
        vm.setDynamicColor(true)
        vm.selectFontStyle(FontStyleType.TECH_MINIMAL)
        verify(repository).setThemePalette(ThemePalette.OCEAN)
        verify(repository).setThemeType(ThemeType.DARK)
        verify(repository).setDynamicTheme(true)
        verify(repository).setFontStyle(FontStyleType.TECH_MINIMAL)
    }

    @Test
    fun modeIsLockedWhileMidnightIsSelected() = runTest {
        stored.value = ThemeSettings(palette = ThemePalette.MIDNIGHT, type = ThemeType.LIGHT)
        val vm = viewModel()
        vm.selectMode(ThemeType.SYSTEM)
        verify(repository, never()).setThemeType(ThemeType.SYSTEM)
        // The stored mode is untouched, so it applies again after leaving Midnight.
        assertEquals(ThemeType.LIGHT, vm.themeSettings.value.type)
        assertFalse(vm.themeSettings.value.copy(palette = ThemePalette.PAPER).isDark(systemInDarkTheme = true))
    }

    @Test
    fun modeSelectableForEveryPaletteButMidnight() {
        ThemePalette.entries.forEach { palette ->
            assertEquals(palette != ThemePalette.MIDNIGHT, AppearanceViewModel.isModeSelectable(palette))
        }
    }

    @Test
    fun modeNoteDescribesWhatIsShown() {
        val base = ThemeSettings()
        assertEquals(ModeNote.SYSTEM_SHOWING_LIGHT, AppearanceViewModel.modeNote(base, systemInDarkTheme = false))
        assertEquals(ModeNote.SYSTEM_SHOWING_DARK, AppearanceViewModel.modeNote(base, systemInDarkTheme = true))
        assertEquals(ModeNote.ALWAYS_LIGHT, AppearanceViewModel.modeNote(base.copy(type = ThemeType.LIGHT), true))
        assertEquals(ModeNote.ALWAYS_DARK, AppearanceViewModel.modeNote(base.copy(type = ThemeType.DARK), false))
        assertEquals(
            ModeNote.ALWAYS_DARK_PALETTE,
            AppearanceViewModel.modeNote(base.copy(palette = ThemePalette.MIDNIGHT, type = ThemeType.LIGHT), false),
        )
        assertTrue(ThemeSettings(palette = ThemePalette.MIDNIGHT).isDark(systemInDarkTheme = false))
    }
}
