package com.pasich.encly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.domain.repository.SettingsRepository
import com.pasich.encly.utils.DeviceCapabilities
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings → Appearance: palette, mode, dynamic colour and font. Every change is written to
 * DataStore at once; the theme (ThemeViewModel) observes the same flow, so the app restyles live.
 */
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
@ViewModelKey
@Inject
class AppearanceViewModel(
    private val settingsRepository: SettingsRepository,
    private val deviceCapabilities: DeviceCapabilities,
) : ViewModel() {

    val themeSettings: StateFlow<ThemeSettings> = settingsRepository.themeSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeSettings(),
    )

    /** The text size every note uses until it sets its own (the editor menu overrides it per note). */
    val defaultFontSize: StateFlow<Int> = settingsRepository.fontSizeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DEFAULT_FONT_SIZE,
    )

    /** The line spacing every note uses until it sets its own. */
    val defaultLineSpacing: StateFlow<NoteLineSpacing> = settingsRepository.noteLineSpacingFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = NoteLineSpacing.DEFAULT,
    )

    fun setDefaultFontSize(size: Int) {
        viewModelScope.launch { settingsRepository.setFontSize(size) }
    }

    fun setDefaultLineSpacing(value: NoteLineSpacing) {
        viewModelScope.launch { settingsRepository.setNoteLineSpacing(value) }
    }

    fun supportsDynamicColors(): Boolean = deviceCapabilities.supportsDynamicColors()

    fun selectPalette(palette: ThemePalette) {
        viewModelScope.launch { settingsRepository.setThemePalette(palette) }
    }

    /**
     * Midnight has no light variant, so the mode cannot change while it is selected. The stored
     * mode is kept untouched and applies again after switching to another palette.
     */
    fun selectMode(mode: ThemeType) {
        if (!isModeSelectable(themeSettings.value.palette)) return
        viewModelScope.launch { settingsRepository.setThemeType(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicTheme(enabled) }
    }

    fun selectFontStyle(style: FontStyleType) {
        viewModelScope.launch { settingsRepository.setFontStyle(style) }
    }

    companion object {
        private const val DEFAULT_FONT_SIZE = 16

        fun isModeSelectable(palette: ThemePalette): Boolean = !palette.isAlwaysDark

        /** Which explanation the mode section shows under the segmented control. */
        fun modeNote(settings: ThemeSettings, systemInDarkTheme: Boolean): ModeNote = when {
            settings.palette.isAlwaysDark -> ModeNote.ALWAYS_DARK_PALETTE
            settings.type == ThemeType.LIGHT -> ModeNote.ALWAYS_LIGHT
            settings.type == ThemeType.DARK -> ModeNote.ALWAYS_DARK
            systemInDarkTheme -> ModeNote.SYSTEM_SHOWING_DARK
            else -> ModeNote.SYSTEM_SHOWING_LIGHT
        }
    }
}

enum class ModeNote { SYSTEM_SHOWING_LIGHT, SYSTEM_SHOWING_DARK, ALWAYS_LIGHT, ALWAYS_DARK, ALWAYS_DARK_PALETTE }
