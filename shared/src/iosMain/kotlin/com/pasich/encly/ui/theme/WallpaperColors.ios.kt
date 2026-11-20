package com.pasich.encly.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** iOS has no wallpaper-derived colours an app can read. */
@Composable
internal actual fun wallpaperColorScheme(dark: Boolean): ColorScheme? = null
