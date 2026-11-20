package com.pasich.encly.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.pasich.encly.domain.model.NoteColor
import kotlin.math.max
import kotlin.math.min

// Note colours (the swatches a note can be marked with). Only a hue is defined here; the shades a
// card or an editor shows are derived from the active theme's own surfaces, so every colour sits
// naturally on all five palettes in light and dark mode, and on a wallpaper (dynamic) scheme.
// Colour literals belong in ui/theme and nowhere else (NoColorLiteralsTest).

/** A hue as the swatch and the accent edge show it, in light and in dark mode. */
private class Hue(val light: Color, val dark: Color)

private val hues = mapOf(
    NoteColor.RED to Hue(Color(color = 0xFFC4574F), Color(color = 0xFFE58A83)),
    NoteColor.ORANGE to Hue(Color(color = 0xFFCC7A33), Color(color = 0xFFE6A665)),
    NoteColor.YELLOW to Hue(Color(color = 0xFFB89A1E), Color(color = 0xFFE0C158)),
    NoteColor.GREEN to Hue(Color(color = 0xFF5E9B5A), Color(color = 0xFF8CC488)),
    NoteColor.TEAL to Hue(Color(color = 0xFF3A9591), Color(color = 0xFF6CC3BF)),
    NoteColor.BLUE to Hue(Color(color = 0xFF4E7FBF), Color(color = 0xFF86AEE6)),
    NoteColor.PURPLE to Hue(Color(color = 0xFF8A6BBF), Color(color = 0xFFB59CE6)),
    NoteColor.PINK to Hue(Color(color = 0xFFC26A92), Color(color = 0xFFE595BA)),
)

/** How much of the hue a card takes on, over the theme's `surfaceContainer`. */
private const val CARD_TINT_LIGHT = 0.22f
private const val CARD_TINT_DARK = 0.20f

/** The editor's page takes less of it, over the theme's `surface`: it is read for long stretches. */
private const val PAGE_TINT_LIGHT = 0.12f
private const val PAGE_TINT_DARK = 0.12f

/** Body text and meta text stay at or above this on a tinted surface (WCAG AA). */
const val NOTE_TEXT_CONTRAST = 4.5

private const val CONTRAST_OFFSET = 0.05
private const val LIGHT_SURFACE = 0.5f
private const val STEP = 0.1f
private const val STEPS = 10

/**
 * How a note marked with a colour looks.
 *
 * @property accent the hue itself: the swatch, the card's edge
 * @property onAccent a mark (the check on a selected swatch) that reads on [accent]
 * @property card the tinted card background
 * @property onCard the title and body text on [card]
 * @property onCardVariant the excerpt and meta text on [card]
 * @property page the tinted editor background
 * @property onPageVariant muted text on [page] (the overline, placeholders)
 */
@Immutable
data class NoteColorStyle(
    val accent: Color,
    val onAccent: Color,
    val card: Color,
    val onCard: Color,
    val onCardVariant: Color,
    val page: Color,
    val onPageVariant: Color,
)

/**
 * The look of [color] on [scheme]; null for [NoteColor.NONE], which keeps the theme's own
 * surfaces. [dark] is whether [scheme] is a dark one.
 */
fun noteColorStyle(color: NoteColor, scheme: ColorScheme, dark: Boolean = scheme.isDark()): NoteColorStyle? {
    val hue = hues[color] ?: return null
    val accent = if (dark) hue.dark else hue.light
    val card = lerp(scheme.surfaceContainer, accent, if (dark) CARD_TINT_DARK else CARD_TINT_LIGHT)
    val page = lerp(scheme.surface, accent, if (dark) PAGE_TINT_DARK else PAGE_TINT_LIGHT)
    return NoteColorStyle(
        accent = accent,
        onAccent = if (contrast(scheme.onSurface, accent) >= contrast(scheme.surface, accent)) {
            scheme.onSurface
        } else {
            scheme.surface
        },
        card = card,
        onCard = scheme.onSurface,
        onCardVariant = readable(scheme.onSurfaceVariant, scheme.onSurface, card),
        page = page,
        onPageVariant = readable(scheme.onSurfaceVariant, scheme.onSurface, page),
    )
}

/** Whether this scheme is a dark one: its page is darker than half. */
fun ColorScheme.isDark(): Boolean = surface.luminance() < LIGHT_SURFACE

/**
 * [muted] on [background], moved toward [strong] in steps until it reads at [NOTE_TEXT_CONTRAST]
 * or more: a tint lowers the contrast of the theme's muted text a little.
 */
private fun readable(muted: Color, strong: Color, background: Color): Color {
    for (step in 0..STEPS) {
        val candidate = lerp(muted, strong, step * STEP)
        if (contrast(candidate, background) >= NOTE_TEXT_CONTRAST) return candidate
    }
    return strong
}

/** WCAG contrast ratio of [fg] over the opaque [bg]. */
fun contrast(fg: Color, bg: Color): Double {
    val l1 = fg.compositeOver(bg).luminance() + CONTRAST_OFFSET
    val l2 = bg.luminance() + CONTRAST_OFFSET
    return max(l1, l2) / min(l1, l2)
}
