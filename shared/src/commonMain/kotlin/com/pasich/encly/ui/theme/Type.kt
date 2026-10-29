package com.pasich.encly.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pasich.encly.domain.model.FontStyleType
import com.pasich.encly.shared.resources.Res
import com.pasich.encly.shared.resources.ibm_plex_sans_variable
import com.pasich.encly.shared.resources.playfair_display_variable
import com.pasich.encly.shared.resources.poppins_bold
import com.pasich.encly.shared.resources.poppins_medium
import com.pasich.encly.shared.resources.poppins_regular
import com.pasich.encly.shared.resources.source_sans3_variable
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

// Fonts are bundled OFL files in composeResources/font (licenses in /licenses/fonts). Encly must not fetch
// fonts through the Google Play Services font provider: that is a network request to Google,
// breaks the "fully offline" promise and leaves devices without GMS with no fonts.
// Variable fonts cover several weights from one file via FontVariation settings (API 26+).

@Composable
private fun playfair(): FontFamily = variableFontFamily(Res.font.playfair_display_variable)

@Composable
private fun sourceSans(): FontFamily = variableFontFamily(Res.font.source_sans3_variable)

@Composable
private fun ibmPlex(): FontFamily = variableFontFamily(Res.font.ibm_plex_sans_variable)

// No SemiBold file is bundled; a 600 request resolves to the nearest face (Bold).
@Composable
private fun poppins(): FontFamily {
    val regular = Font(Res.font.poppins_regular, FontWeight.Normal)
    val medium = Font(Res.font.poppins_medium, FontWeight.Medium)
    val bold = Font(Res.font.poppins_bold, FontWeight.Bold)
    return remember(regular, medium, bold) { FontFamily(regular, medium, bold) }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun variableFontFamily(resource: FontResource): FontFamily {
    val fonts = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            resource = resource,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    }
    return remember(fonts) { FontFamily(fonts) }
}

/**
 * The families a font choice (Settings → Appearance → Font) is made of: [display] and [body], and the two
 * that ignore the choice, IBM Plex Sans for data ([plex]) and Playfair Display for the wordmark ([serif]).
 */
@Immutable
data class FontSet(val display: FontFamily, val body: FontFamily, val plex: FontFamily, val serif: FontFamily) {
    companion object {
        /** The platform's own font for all four: before the bundled files are read, and in previews. */
        val System = FontSet(FontFamily.Default, FontFamily.Default, FontFamily.Default, FontFamily.Default)
    }
}

/** Editorial, Modern and Technical (design spec §2.2). */
@Composable
fun FontStyleType.fontSet(): FontSet {
    val plex = ibmPlex()
    val serif = playfair()
    return when (this) {
        FontStyleType.COZY_EDITOR -> FontSet(display = serif, body = sourceSans(), plex = plex, serif = serif)
        FontStyleType.MODERN_SIMPLE -> FontSet(display = poppins(), body = sourceSans(), plex = plex, serif = serif)
        FontStyleType.TECH_MINIMAL -> FontSet(display = plex, body = plex, plex = plex, serif = serif)
    }
}

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    tracking: TextUnit = 0.em,
    fontStyle: FontStyle = FontStyle.Normal,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontStyle = fontStyle,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking,
)

/**
 * The Material 3 type scale (design spec §2.3). Headings follow the chosen display family, text
 * the chosen body family. The overline ([Typography.labelSmall]) is always IBM Plex Sans.
 */
fun appTypography(fonts: FontSet): Typography {
    val d = fonts.display
    val b = fonts.body
    val semi = FontWeight.SemiBold
    return Typography(
        displayLarge = style(d, semi, size = 44, lineHeight = 48, tracking = (-0.02).em),
        displayMedium = style(d, semi, size = 40, lineHeight = 44, tracking = (-0.02).em),
        displaySmall = style(d, semi, size = 34, lineHeight = 40, tracking = (-0.01).em),
        headlineLarge = style(d, semi, size = 32, lineHeight = 36, tracking = (-0.01).em),
        headlineMedium = style(d, semi, size = 30, lineHeight = 34, tracking = (-0.01).em),
        headlineSmall = style(d, semi, size = 28, lineHeight = 32),
        titleLarge = style(d, semi, size = 21, lineHeight = 26),
        titleMedium = style(d, semi, size = 19, lineHeight = 24),
        titleSmall = style(b, semi, size = 17, lineHeight = 22),
        bodyLarge = style(b, FontWeight.Normal, size = 17, lineHeight = 26),
        bodyMedium = style(b, FontWeight.Normal, size = 15, lineHeight = 21),
        bodySmall = style(b, FontWeight.Normal, size = 14, lineHeight = 20),
        labelLarge = style(b, semi, size = 16, lineHeight = 22),
        labelMedium = style(b, semi, size = 14, lineHeight = 20),
        labelSmall = style(fonts.plex, FontWeight.Medium, size = 12, lineHeight = 16, tracking = 0.08.em),
    )
}

/** Text styles outside the Material scale (design spec §2.3, second table). */
@Immutable
data class EnclyTypography(
    val buttonPrimary: TextStyle,
    val chip: TextStyle,
    val stepLabel: TextStyle,
    val dataLarge: TextStyle,
    val dataIndex: TextStyle,
    val dataSmall: TextStyle,
    val keypadDigit: TextStyle,
    val phraseInput: TextStyle,
    val quote: TextStyle,
    val wordmark: TextStyle,
    val meta: TextStyle,
    val heroSub: TextStyle,
    /** Dialog titles: headlineMedium sized down to 24/30. */
    val dialogTitle: TextStyle,
    /** Sub-screen top bars: headlineSmall at 26. */
    val subScreenTitle: TextStyle,
    /** Theme swatch labels, 13/18; the selected one in bold. */
    val swatchLabel: TextStyle,
    /** The "Aa" sample of a font choice, in that choice's display family. */
    val fontSample: TextStyle,
)

/** Data styles (IBM Plex Sans) and the wordmark (Playfair Display) ignore the font choice. */
fun enclyTypography(fonts: FontSet): EnclyTypography {
    val b = fonts.body
    val plex = FontWeight.Medium
    return EnclyTypography(
        buttonPrimary = style(b, FontWeight.SemiBold, size = 17, lineHeight = 22, tracking = 0.01.em),
        chip = style(b, FontWeight.SemiBold, size = 15, lineHeight = 20),
        stepLabel = style(fonts.plex, plex, size = 12, lineHeight = 16, tracking = 0.06.em),
        dataLarge = style(fonts.plex, plex, size = 18, lineHeight = 24, tracking = 0.01.em),
        dataIndex = style(fonts.plex, plex, size = 13, lineHeight = 16),
        dataSmall = style(fonts.plex, plex, size = 12, lineHeight = 16),
        keypadDigit = style(fonts.plex, plex, size = 26, lineHeight = 32),
        phraseInput = style(fonts.plex, FontWeight.Normal, size = 17, lineHeight = 26),
        // No italic Playfair file is bundled; Compose synthesises the slant.
        quote = style(fonts.display, plex, size = 19, lineHeight = 27, fontStyle = FontStyle.Italic),
        wordmark = style(fonts.serif, FontWeight.Bold, size = 20, lineHeight = 24, tracking = (-0.01).em),
        meta = style(b, FontWeight.Normal, size = 13, lineHeight = 18),
        heroSub = style(b, FontWeight.Normal, size = 18, lineHeight = 27),
        dialogTitle = style(fonts.display, FontWeight.SemiBold, size = 24, lineHeight = 30),
        subScreenTitle = style(fonts.display, FontWeight.SemiBold, size = 26, lineHeight = 32),
        swatchLabel = style(b, FontWeight.Medium, size = 13, lineHeight = 18),
        fontSample = style(fonts.display, FontWeight.SemiBold, size = 24, lineHeight = 28),
    )
}

val LocalEnclyTypography = staticCompositionLocalOf { enclyTypography(FontSet.System) }
