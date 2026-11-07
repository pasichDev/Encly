package com.pasich.encly.desktop.documents

import com.pasich.encly.data.export.NoteDocument
import com.pasich.encly.platform.NoteDocumentRenderer
import com.pasich.encly.platform.PrintResult
import kotlinx.io.Sink
import kotlinx.io.asOutputStream
import org.apache.fontbox.ttf.TTFParser
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.printing.PDFPageable
import java.awt.print.Pageable
import java.io.IOException
import java.util.Locale

/** Hands a finished document to the system print dialog and the printer. */
fun interface PrintLauncher {
    /**
     * Asks the user, then prints [pages]; [PrintResult.CANCELLED] when the dialog was closed or [mayProceed] said no
     * (after the dialog, or before a page). Throws on failure.
     */
    suspend fun print(pages: Pageable, mayProceed: () -> Boolean): PrintResult
}

/**
 * [NoteDocumentRenderer] of the desktop: a note as a PDF (Apache PDFBox, built entirely in memory with the
 * app's own embedded fonts) written to the stream the user picked, or the same pages sent to [printer].
 *
 * Nothing is written anywhere but the given stream: PDFBox is told to keep its working data in memory, the two fonts
 * are embedded (so no system font is looked up, and no font cache is created), and no network is used.
 */
class DesktopNoteDocuments(
    private val printer: PrintLauncher,
    private val fontBytes: () -> ByteArray = ::bundledFont,
    private val arabicFontBytes: () -> ByteArray = ::bundledArabicFont,
    private val paper: () -> PDRectangle = ::paperOf,
) : NoteDocumentRenderer {
    override fun writePdf(document: NoteDocument, out: Sink) {
        build(document).use { pdf -> pdf.save(out.asOutputStream()) }
    }

    override suspend fun print(document: NoteDocument, mayProceed: () -> Boolean): PrintResult {
        // Rendered before the dialog opens, so a note that cannot be drawn fails before the user is asked anything.
        // The document is closed when the print ends, however it ends (a lock included).
        return build(document).use { pdf -> printer.print(PDFPageable(pdf), mayProceed) }
    }

    /** The PDF of [note], open in memory until the caller closes it. */
    internal fun build(note: NoteDocument): PDDocument {
        val pdf = PDDocument(IOUtils.createMemoryOnlyStreamCache())
        var built = false
        try {
            // Latin and Cyrillic first, so Arabic is only drawn with the Arabic font where the first has no glyph.
            val layout = TextLayout(listOf(faceOf(pdf, fontBytes()), faceOf(pdf, arabicFontBytes())))
            PdfComposer(pdf, layout, paper()).use { it.compose(note) }
            built = true
            return pdf
        } finally {
            // A document that could not be drawn is closed here; a finished one is the caller's.
            if (!built) pdf.close()
        }
    }

    private fun faceOf(pdf: PDDocument, bytes: ByteArray): Face {
        val ttf = TTFParser().parse(RandomAccessReadBuffer(bytes))
        val lookup = ttf.unicodeCmapLookup
        return Face(PDType0Font.load(pdf, ttf, true)) { point -> lookup.getGlyphId(point) > 0 }
    }

    companion object {
        /** Where the app's fonts are in the classpath (the shared module's resources). */
        const val FONT_RESOURCE = "composeResources/com.pasich.encly.shared.resources/font/source_sans3_variable.ttf"

        /** Where the Arabic font is in the classpath (the desktop app's resources). */
        const val ARABIC_FONT_RESOURCE = "fonts/NotoSansArabic-Regular.ttf"

        /** The embedded font: Source Sans 3, which covers Latin (with the Central European letters) and Cyrillic. */
        fun bundledFont(): ByteArray = resource(FONT_RESOURCE)

        /** The embedded font for Arabic script: Noto Sans Arabic, with the presentation forms the shaping produces. */
        fun bundledArabicFont(): ByteArray = resource(ARABIC_FONT_RESOURCE)

        private fun resource(name: String): ByteArray {
            val stream = DesktopNoteDocuments::class.java.classLoader.getResourceAsStream(name)
                ?: throw IOException("The document font is missing from the application")
            return stream.use { it.readBytes() }
        }

        private val LETTER_REGIONS = setOf("US", "CA", "MX", "CL", "CO", "VE", "PH")

        /** Letter in the countries that use it, A4 everywhere else. */
        fun paperOf(): PDRectangle =
            if (Locale.getDefault().country in LETTER_REGIONS) PDRectangle.LETTER else PDRectangle.A4
    }
}
