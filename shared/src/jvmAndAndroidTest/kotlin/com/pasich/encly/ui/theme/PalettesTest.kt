package com.pasich.encly.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.domain.model.ThemePalette
import com.pasich.encly.domain.model.ThemeSettings
import com.pasich.encly.domain.model.ThemeType
import com.pasich.encly.presentation.designsystem.DIMMED_CARD_ALPHA
import com.pasich.encly.presentation.designsystem.PLACEHOLDER_ALPHA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class PalettesTest {

    private val schemes = ThemePalette.entries.flatMap { palette ->
        listOf(false, true).map { dark -> "$palette/${if (dark) "dark" else "light"}" to colorSchemeFor(palette, dark) }
    }

    @Test
    fun defaultsArePaperAndEditorial() {
        assertEquals(ThemePalette.PAPER, ThemeSettings().palette)
        assertEquals(FontStyleType.COZY_EDITOR, ThemeSettings().fontStyle)
        assertEquals(ThemeType.SYSTEM, ThemeSettings().type)
        assertFalse(ThemeSettings().dynamic)
    }

    @Test
    fun midnightIsAlwaysDark() {
        ThemeType.entries.forEach { mode ->
            listOf(false, true).forEach { system ->
                assertTrue(ThemeSettings(type = mode, palette = ThemePalette.MIDNIGHT).isDark(system))
            }
        }
        assertSame(colorSchemeFor(ThemePalette.MIDNIGHT, false), colorSchemeFor(ThemePalette.MIDNIGHT, true))
        assertEquals(Color.Black, colorSchemeFor(ThemePalette.MIDNIGHT, true).surface)
    }

    @Test
    fun modeResolvesForOtherPalettes() {
        val paper = ThemeSettings(palette = ThemePalette.PAPER)
        assertTrue(paper.copy(type = ThemeType.SYSTEM).isDark(systemInDarkTheme = true))
        assertFalse(paper.copy(type = ThemeType.SYSTEM).isDark(systemInDarkTheme = false))
        assertFalse(paper.copy(type = ThemeType.LIGHT).isDark(systemInDarkTheme = true))
        assertTrue(paper.copy(type = ThemeType.DARK).isDark(systemInDarkTheme = false))
    }

    @Test
    fun lightAndDarkVariantsDiffer() {
        ThemePalette.entries.filterNot { it.isAlwaysDark }.forEach { palette ->
            val light = colorSchemeFor(palette, false)
            val dark = colorSchemeFor(palette, true)
            assertNotEquals(palette.name, light.surface, dark.surface)
            assertTrue("$palette light surface is light", light.surface.luminance() > HALF)
            assertTrue("$palette dark surface is dark", dark.surface.luminance() < HALF)
        }
    }

    @Test
    fun textMeetsContrastOnEverySurface() {
        schemes.forEach { (name, s) ->
            val surfaces = listOf(s.surface, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest)
            assertContrast("$name onSurface/surface", s.onSurface, s.surface, TEXT)
            surfaces.forEach { assertContrast("$name onSurfaceVariant", s.onSurfaceVariant, it, TEXT) }
            assertContrast("$name primary/surface", s.primary, s.surface, TEXT)
            assertContrast("$name primary/container", s.primary, s.surfaceContainer, TEXT)
            assertContrast("$name onPrimary", s.onPrimary, s.primary, TEXT)
            assertContrast("$name onPrimaryContainer", s.onPrimaryContainer, s.primaryContainer, TEXT)
            assertContrast("$name outline", s.outline, s.surface, NON_TEXT)
        }
    }

    @Test
    fun fadedTextStillMeetsContrast() {
        schemes.forEach { (name, s) ->
            listOf(s.surface, s.surfaceContainer, s.surfaceContainerHigh).forEach { bg ->
                val placeholder = s.onSurface.copy(alpha = PLACEHOLDER_ALPHA).compositeOver(bg)
                assertContrast("$name field placeholder", placeholder, bg, TEXT)
            }
            // A trashed note card: only its title is faded, on the card's surfaceContainer.
            val dimmedTitle = s.onSurface.copy(alpha = DIMMED_CARD_ALPHA).compositeOver(s.surfaceContainer)
            assertContrast("$name trashed card title", dimmedTitle, s.surfaceContainer, TEXT)
        }
    }

    @Test
    fun everyRoleIsSetExplicitly() {
        // A role left at the Material baseline would be the baseline purple (#6750A4 family).
        val baselinePurple =
            setOf(Color(red = 0x67, green = 0x50, blue = 0xA4), Color(red = 0xD0, green = 0xBC, blue = 0xFF))
        schemes.forEach { (name, s) ->
            val roles = s.roles()
            assertTrue("$name leaves a baseline role", roles.none { it in baselinePurple })
            assertTrue("$name has an unspecified role", roles.none { it == Color.Unspecified })
        }
    }

    @Test
    fun shadowIsOpaqueEnoughOnlyInDark() {
        ThemePalette.entries.forEach { palette ->
            assertTrue(
                enclyColorsFor(palette, dark = true).shadow.alpha >= enclyColorsFor(palette, dark = false).shadow.alpha,
            )
        }
    }

    @Test
    fun trueBlackKeepsTheAccent() {
        val scheme = colorSchemeFor(ThemePalette.OCEAN, dark = true).withTrueBlackSurfaces()
        assertEquals(Color.Black, scheme.surface)
        assertEquals(Color.Black, scheme.background)
        assertEquals(colorSchemeFor(ThemePalette.OCEAN, dark = true).primary, scheme.primary)
    }

    private fun ColorScheme.roles() = listOf(
        primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary,
        secondary, onSecondary, secondaryContainer, onSecondaryContainer,
        tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
        background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant, surfaceTint,
        inverseSurface, inverseOnSurface, error, onError, errorContainer, onErrorContainer,
        outline, outlineVariant, scrim, surfaceBright, surfaceDim,
        surfaceContainer, surfaceContainerHigh, surfaceContainerHighest, surfaceContainerLow, surfaceContainerLowest,
    )

    private fun assertContrast(label: String, fg: Color, bg: Color, minimum: Double) {
        val l1 = fg.luminance() + OFFSET
        val l2 = bg.luminance() + OFFSET
        val ratio = max(l1, l2) / min(l1, l2)
        assertTrue("$label contrast $ratio < $minimum", ratio >= minimum)
    }

    private companion object {
        const val TEXT = 4.5
        const val NON_TEXT = 3.0
        const val OFFSET = 0.05
        const val HALF = 0.5f
    }
}
