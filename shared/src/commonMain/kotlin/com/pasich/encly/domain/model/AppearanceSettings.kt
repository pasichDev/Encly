package com.pasich.encly.domain.model

/** Light/dark mode. Persisted by ordinal: do not reorder. */
enum class ThemeType { SYSTEM, LIGHT, DARK }

/**
 * The colour family of the app. Persisted by ordinal: do not reorder, only append.
 * [MIDNIGHT] has no light variant; it is always dark, whatever [ThemeType] says.
 */
enum class ThemePalette {
    PAPER,
    FOREST,
    OCEAN,
    GRAPHITE,
    MIDNIGHT,
    ;

    val isAlwaysDark: Boolean get() = this == MIDNIGHT

    companion object {
        val DEFAULT = PAPER
    }
}

/**
 * The app-wide font set: a display family for headings and a body family for text.
 * Persisted by ordinal: do not reorder.
 */
enum class FontStyleType {
    MODERN_SIMPLE, // "Modern": Poppins + Source Sans 3
    COZY_EDITOR, // "Editorial": Playfair Display + Source Sans 3 (default)
    TECH_MINIMAL, // "Technical": IBM Plex Sans throughout
    ;

    companion object {
        val DEFAULT = COZY_EDITOR
    }
}

/** The appearance settings the theme is built from. */
data class ThemeSettings(
    val dynamic: Boolean = false,
    val type: ThemeType = ThemeType.SYSTEM,
    val palette: ThemePalette = ThemePalette.DEFAULT,
    val fontStyle: FontStyleType = FontStyleType.DEFAULT,
) {
    /** Resolves the mode to light or dark. Midnight is always dark. */
    fun isDark(systemInDarkTheme: Boolean): Boolean = palette.isAlwaysDark ||
        when (type) {
            ThemeType.SYSTEM -> systemInDarkTheme
            ThemeType.LIGHT -> false
            ThemeType.DARK -> true
        }
}
