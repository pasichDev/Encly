package com.pasich.encly.domain.model

enum class ThemeType { SYSTEM, LIGHT, DARK }

/** The appearance settings the theme is built from. */
data class ThemeSettings(val dynamic: Boolean = false, val type: ThemeType = ThemeType.SYSTEM)

enum class FontStyleType {
    MODERN_SIMPLE, // Poppins + Roboto
    COZY_EDITOR, // Playfair + Source Sans 3
    TECH_MINIMAL, // IBM Plex Sans + Inter
}
