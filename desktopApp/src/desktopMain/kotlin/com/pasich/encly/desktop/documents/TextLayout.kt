package com.pasich.encly.desktop.documents

import com.ibm.icu.text.ArabicShaping
import com.ibm.icu.text.Bidi
import org.apache.pdfbox.pdmodel.font.PDFont
import java.text.Normalizer

/** One font of the fallback chain with the test for the characters it has a glyph for. */
internal class Face(val font: PDFont, private val glyphs: (Int) -> Boolean) {
    fun covers(point: Int): Boolean = glyphs(point)
}

/** A stretch of a line drawn with one [face]; [text] is only characters the face has. */
internal class GlyphRun(val face: Face, val text: String)

/**
 * What PDFBox does not do: choosing a font per character from a fallback chain, joining the letters of cursive
 * scripts (Arabic: the contextual forms and the lam-alef ligatures, by ICU4J) and putting a line of mixed
 * left-to-right and right-to-left text into reading-on-the-page order (the Unicode bidirectional algorithm, by
 * ICU4J). A line is wrapped on its logical text; only the finished line is reordered. The first face is the one
 * a character no face has is drawn with, as `?`. Pure computation: nothing here reads a file.
 */
internal class TextLayout(private val faces: List<Face>) {
    private val shaping = ArabicShaping(
        ArabicShaping.LETTERS_SHAPE or ArabicShaping.LENGTH_GROW_SHRINK or ArabicShaping.TEXT_DIRECTION_LOGICAL,
    )

    /** True when [text] reads right to left: its first strong character is Hebrew or Arabic (or a mark for it). */
    fun isRtl(text: CharSequence): Boolean = text.codePoints().toArray().asSequence().firstNotNullOfOrNull { point ->
        when (Character.getDirectionality(point)) {
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> false

            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            -> true

            else -> null
        }
    } ?: false

    /** The width of [logical] in font units of 1000 per em, as drawn. */
    fun width(logical: String): Float =
        runsOf(shaping.shape(logical)).sumOf { it.face.font.getStringWidth(it.text).toDouble() }.toFloat()

    /** The runs of one [logical] line in the order they are drawn from the left, whatever its [rtl] direction. */
    fun visual(logical: String, rtl: Boolean): List<GlyphRun> {
        val shaped = shaping.shape(logical)
        if (shaped.isEmpty()) return emptyList()
        val level = if (rtl) Bidi.DIRECTION_RIGHT_TO_LEFT else Bidi.DIRECTION_LEFT_TO_RIGHT
        val reordered = Bidi(shaped, level)
            .writeReordered(REORDERING)
        return runsOf(reordered)
    }

    private fun runsOf(text: String): List<GlyphRun> {
        val runs = mutableListOf<GlyphRun>()
        var face: Face? = null
        val current = StringBuilder()
        var index = 0
        while (index < text.length) {
            val point = text.codePointAt(index)
            index += Character.charCount(point)
            if (isInvisible(point)) continue
            val (owner, drawn) = resolve(point)
            if (owner !== face && current.isNotEmpty()) {
                runs += GlyphRun(checkNotNull(face), current.toString())
                current.clear()
            }
            face = owner
            current.appendCodePoint(drawn)
        }
        if (current.isNotEmpty()) runs += GlyphRun(checkNotNull(face), current.toString())
        return runs
    }

    /** The face and the character it draws for [point]: the base letter of a form the fonts lack, else `?`. */
    private fun resolve(point: Int): Pair<Face, Int> {
        val base = Normalizer.normalize(String(Character.toChars(point)), Normalizer.Form.NFKC)
        val letter = if (base.codePointCount(0, base.length) == 1) base.codePointAt(0) else point
        return faces.firstOrNull { it.covers(point) }?.let { it to point }
            ?: faces.firstOrNull { it.covers(letter) }?.let { it to letter }
            ?: (faces.first() to QUESTION_MARK)
    }

    /** Characters that only steer shaping and reordering and have nothing to draw. */
    private fun isInvisible(point: Int) = point in ZERO_WIDTH_FROM..ZERO_WIDTH_TO ||
        point in BIDI_EMBEDDING_FROM..BIDI_EMBEDDING_TO ||
        point in BIDI_ISOLATE_FROM..BIDI_ISOLATE_TO ||
        point in VARIATION_FROM..VARIATION_TO ||
        point == ARABIC_LETTER_MARK

    private companion object {
        const val REORDERING = (
            Bidi.DO_MIRRORING.toInt() or Bidi.KEEP_BASE_COMBINING.toInt() or
                Bidi.REMOVE_BIDI_CONTROLS.toInt()
            )
        const val QUESTION_MARK = '?'.code
        const val ZERO_WIDTH_FROM = 0x200B
        const val ZERO_WIDTH_TO = 0x200F
        const val BIDI_EMBEDDING_FROM = 0x202A
        const val BIDI_EMBEDDING_TO = 0x202E
        const val BIDI_ISOLATE_FROM = 0x2066
        const val BIDI_ISOLATE_TO = 0x2069
        const val VARIATION_FROM = 0xFE00
        const val VARIATION_TO = 0xFE0F
        const val ARABIC_LETTER_MARK = 0x061C
    }
}
