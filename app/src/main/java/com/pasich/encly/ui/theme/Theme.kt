package com.pasich.encly.ui.theme

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.presentation.viewmodel.ThemeViewModel

@Composable
fun AppTheme(themeViewModel: ThemeViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val themeSettings by themeViewModel.themeSettingsFlow.collectAsStateWithLifecycle()
    val isDarkTheme = themeSettings.isDark(isSystemInDarkTheme())

    val activity = LocalView.current.context as Activity
    WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
        isAppearanceLightStatusBars = !isDarkTheme
    }
    // Defense in depth: MainActivity sets this before the first frame, and the theme
    // reasserts it for the Compose window. This is intentionally not user-configurable.
    activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

    EnclyTheme(settings = themeSettings, dark = isDarkTheme) {
        // The window behind Compose (activity recreation, IME and window transitions) follows
        // the palette and the app's mode, not the static, system-night-only window_background.
        val background = MaterialTheme.colorScheme.background.toArgb()
        SideEffect { activity.window.setBackgroundDrawable(background.toDrawable()) }
        content()
    }
}

/**
 * The Encly theme for already resolved settings: the colour scheme of the palette in the given
 * mode (or of the wallpaper when dynamic colour is on, Android 12+), typography from the font
 * choice, shapes and spacing. Previews can use it without a ViewModel.
 */
@Composable
fun EnclyTheme(settings: ThemeSettings, dark: Boolean, content: @Composable () -> Unit) {
    val colorScheme = if (settings.dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        val wallpaper = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (settings.palette == ThemePalette.MIDNIGHT) wallpaper.withTrueBlackSurfaces() else wallpaper
    } else {
        colorSchemeFor(settings.palette, dark)
    }
    val fonts = settings.fontStyle.fontSet()
    val typography = remember(fonts) { appTypography(fonts) }
    val enclyTypography = remember(fonts) { enclyTypography(fonts) }

    CompositionLocalProvider(
        LocalEnclyColors provides enclyColorsFor(settings.palette, dark),
        LocalEnclyTypography provides enclyTypography,
        LocalSpacing provides EnclySpacing(),
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
