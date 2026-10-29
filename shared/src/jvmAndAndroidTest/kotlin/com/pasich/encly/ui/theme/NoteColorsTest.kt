package com.pasich.encly.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.pasich.encly.domain.model.NoteColor
import com.pasich.encly.domain.model.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteColorsTest {

    private val coloured = NoteColor.entries.filter { it != NoteColor.NONE }

    /** Every palette in light and dark, plus the Material baseline schemes a wallpaper scheme resembles. */
    private val schemes = ThemePalette.entries.flatMap { palette ->
        listOf(false, true).map { dark -> "$palette/${if (dark) "dark" else "light"}" to colorSchemeFor(palette, dark) }
    } + listOf("baseline/light" to lightColorScheme(), "baseline/dark" to darkColorScheme())

    @Test
    fun textOnATintedCardMeetsContrastInEveryThemeAndMode() {
        schemes.forEach { (name, scheme) ->
            coloured.forEach { color ->
                val style = checkNotNull(noteColorStyle(color, scheme))
                assertContrast("$name $color title", style.onCard, style.card)
                assertContrast("$name $color excerpt and meta", style.onCardVariant, style.card)
            }
        }
    }

    @Test
    fun textOnATintedEditorPageMeetsContrastInEveryThemeAndMode() {
        schemes.forEach { (name, scheme) ->
            coloured.forEach { color ->
                val style = checkNotNull(noteColorStyle(color, scheme))
                assertContrast("$name $color body", scheme.onSurface, style.page)
                assertContrast("$name $color overline", style.onPageVariant, style.page)
            }
        }
    }

    @Test
    fun theCheckOnASwatchReadsOnItsHue() {
        schemes.forEach { (name, scheme) ->
            coloured.forEach { color ->
                val style = checkNotNull(noteColorStyle(color, scheme))
                assertContrast("$name $color check", style.onAccent, style.accent, GRAPHIC)
            }
        }
    }

    @Test
    fun theHueShowsUpAgainstTheCardAndTheNeutralCard() {
        schemes.forEach { (name, scheme) ->
            coloured.forEach { color ->
                val style = checkNotNull(noteColorStyle(color, scheme))
                assertTrue("$name $color card is tinted", style.card != scheme.surfaceContainer)
                assertTrue("$name $color page is tinted", style.page != scheme.surface)
                assertContrast("$name $color edge", style.accent, style.card, EDGE)
            }
        }
    }

    @Test
    fun everyColourLooksDifferentOnATheme() {
        schemes.forEach { (name, scheme) ->
            val cards = coloured.map { checkNotNull(noteColorStyle(it, scheme)).card }
            assertEquals("$name cards are distinct", coloured.size, cards.toSet().size)
            val accents = coloured.map { checkNotNull(noteColorStyle(it, scheme)).accent }
            assertEquals("$name accents are distinct", coloured.size, accents.toSet().size)
        }
    }

    @Test
    fun noColourKeepsTheThemeSurfaces() {
        schemes.forEach { (_, scheme) -> assertNull(noteColorStyle(NoteColor.NONE, scheme)) }
    }

    @Test
    fun darkSchemesAreDetected() {
        assertTrue(colorSchemeFor(ThemePalette.MIDNIGHT, true).isDark())
        assertTrue(!colorSchemeFor(ThemePalette.PAPER, false).isDark())
    }

    @Test
    fun keysAreStableAndUnknownOnesReadAsNone() {
        assertEquals(
            listOf("none", "red", "orange", "yellow", "green", "teal", "blue", "purple", "pink"),
            NoteColor.entries.map { it.key },
        )
        NoteColor.entries.forEach { assertEquals(it, NoteColor.fromKey(it.key)) }
        assertEquals(NoteColor.NONE, NoteColor.fromKey("magenta"))
        assertEquals(NoteColor.NONE, NoteColor.fromKey(null))
    }

    private fun assertContrast(label: String, fg: Color, bg: Color, minimum: Double = TEXT) {
        val ratio = contrast(fg, bg)
        assertTrue("$label contrast $ratio < $minimum", ratio >= minimum)
    }

    private companion object {
        const val TEXT = NOTE_TEXT_CONTRAST
        const val GRAPHIC = 3.0
        const val EDGE = 1.2
    }
}
