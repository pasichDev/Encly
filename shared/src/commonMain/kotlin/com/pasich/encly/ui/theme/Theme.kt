package com.pasich.encly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.platform.LocalAppWindow
import com.pasich.encly.presentation.viewmodel.ThemeViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun AppTheme(themeViewModel: ThemeViewModel = metroViewModel(), content: @Composable () -> Unit) {
    val themeSettings by themeViewModel.themeSettingsFlow.collectAsStateWithLifecycle()
    val isDarkTheme = themeSettings.isDark(isSystemInDarkTheme())

    val window = LocalAppWindow.current
    SideEffect {
        window.setLightStatusBar(light = !isDarkTheme)
        // Defense in depth: the host sets this before the first frame, and the theme reasserts it for
        // the Compose window. This is intentionally not user-configurable.
        window.enforceSecure()
    }

    EnclyTheme(settings = themeSettings, dark = isDarkTheme) {
        // The window behind Compose (activity recreation, IME and window transitions) follows
        // the palette and the app's mode, not the static, system-night-only window_background.
        val background = MaterialTheme.colorScheme.background.toArgb()
        SideEffect { window.setBackgroundColor(background) }
        content()
    }
}

/**
 * The Encly theme for already resolved settings: the colour scheme of the palette in the given
 * mode (or of the wallpaper when dynamic colour is on and the platform has it), typography from the font
 * choice, shapes and spacing. Previews can use it without a ViewModel.
 */
@Composable
fun EnclyTheme(settings: ThemeSettings, dark: Boolean, content: @Composable () -> Unit) {
    val wallpaper = if (settings.dynamic) wallpaperColorScheme(dark) else null
    val colorScheme = if (wallpaper != null) {
        if (settings.palette == ThemePalette.MIDNIGHT) wallpaper.withTrueBlackSurfaces() else wallpaper
    } else {
        colorSchemeFor(settings.palette, dark)
    }
    val fonts = settings.fontStyle.fontSet()
    val typography = remember(fonts) { appTypography(fonts) }
    val enclyTypography = remember(fonts) { enclyTypography(fonts) }
    val profile = LocalDensityProfile.current
    val spacing = remember(profile) { enclySpacingFor(profile) }

    CompositionLocalProvider(
        LocalEnclyColors provides enclyColorsFor(settings.palette, dark),
        LocalEnclyTypography provides enclyTypography,
        LocalSpacing provides spacing,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = EnclyShapes,
            content = content,
        )
    }
}

/** Encly tokens that Material's theme does not carry. */
object EnclyTheme {
    val colors: EnclyColors
        @Composable
        @ReadOnlyComposable
        get() = LocalEnclyColors.current

    val typography: EnclyTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalEnclyTypography.current

    val spacing: EnclySpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current
}

/**
 * The colour scheme the system derives from the user's wallpaper (Android 12+) in the given mode, or null
 * where the platform has none.
 */
@Composable
internal expect fun wallpaperColorScheme(dark: Boolean): ColorScheme?
