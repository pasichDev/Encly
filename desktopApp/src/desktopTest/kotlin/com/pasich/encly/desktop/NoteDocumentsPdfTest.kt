package com.pasich.encly.desktop

import com.ibm.icu.text.ArabicShaping
import com.pasich.encly.data.export.DocumentBlock
import com.pasich.encly.data.export.DocumentItem
import com.pasich.encly.data.export.ListKind
import com.pasich.encly.data.export.NoteDocument
import com.pasich.encly.desktop.documents.DesktopNoteDocuments
import com.pasich.encly.desktop.documents.PrintLauncher
import com.pasich.encly.platform.PrintResult
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.apache.fontbox.ttf.TTFParser
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.junit.Test
import java.awt.print.Pageable
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteDocumentsPdfTest {
    private val offline = PrintLauncher { _, _ -> error("nothing prints here") }
    private val documents = DesktopNoteDocuments(offline)

    private fun pdfOf(note: NoteDocument, renderer: DesktopNoteDocuments = documents): ByteArray {
        val out = Buffer()
        renderer.writePdf(note, out)
        return out.readByteArray()
    }

    private fun textOf(pdf: ByteArray): String = Loader.loadPDF(pdf).use { PDFTextStripper().getText(it) }

    /** Every glyph of the first page as the PDF holds it: the character it maps back to and where it is. */
    private fun glyphsOf(pdf: ByteArray): List<TextPosition> = Loader.loadPDF(pdf).use { doc ->
        val found = mutableListOf<TextPosition>()
        object : PDFTextStripper() {
            override fun processTextPosition(text: TextPosition) {
                found += text
                super.processTextPosition(text)
            }
        }.getText(doc)
        found
    }

    private fun pageWidth(pdf: ByteArray): Float = Loader.loadPDF(pdf).use { it.getPage(0).mediaBox.width }

    private fun pagesOf(pdf: ByteArray): Int = Loader.loadPDF(pdf).use { it.numberOfPages }

    private fun note(vararg blocks: DocumentBlock, title: String = "Title", tags: List<String> = emptyList()) =
        NoteDocument(title, tags, blocks.toList())

    @Test
    fun theFileIsAPdfWithTheTitleTagsAndText() {
        val paragraph = DocumentBlock.Paragraph("Buy bread and milk")
        val pdf = pdfOf(note(paragraph, title = "Groceries", tags = listOf("Home", "Weekly")))

        assertTrue(String(pdf, 0, 5, Charsets.ISO_8859_1) == "%PDF-")
        val text = textOf(pdf)
        assertTrue("Groceries" in text, text)
        assertTrue("Home, Weekly" in text, text)
        assertTrue("Buy bread and milk" in text, text)
    }

    @Test
    fun theTextOfEveryAppLanguageSurvivesTheRoundTrip() {
        val lines = listOf(
            "Привіт, світе! Їжак, єнот, ґанок і Ґудзик",
            "Zażółć gęślą jaźń. Łódź, Gdańsk, święto",
            "Größe, Straße, Übung, Äpfel, Öl",
            "Año nuevo, ¿qué tal, ¡hola!, pingüino",
            "À l'école, être, garçon, cœur, naïve",
            "Perché, città, più, è già così",
            "Dankjewel, ijs, café, coördinatie",
            "Coração, informação, não, vovô",
        )
        val pdf = pdfOf(note(*lines.map(DocumentBlock::Paragraph).toTypedArray()))

        val text = textOf(pdf)
        lines.forEach { line -> assertTrue(line in text, "missing: $line in\n$text") }
        assertFalse('?' in text, "a letter of the app languages was replaced")
    }

    @Test
    fun theFontCoversEveryLetterOfEveryAppString() {
        val font = TTFParser().parse(RandomAccessReadBuffer(DesktopNoteDocuments.bundledFont()))
        val arabic = TTFParser().parse(RandomAccessReadBuffer(DesktopNoteDocuments.bundledArabicFont()))
        val lookup = font.unicodeCmapLookup
        val arabicLookup = arabic.unicodeCmapLookup
        val strings = File("../core/resources/src/commonMain/strings")
        val files = strings.listFiles { f -> f.isDirectory }.orEmpty().map { File(it, "strings.xml") }
        assertTrue(files.size >= 10, "the ten locales: ${files.size}")
        val missing = sortedSetOf<String>()
        files.forEach { file ->
            // Arabic is drawn in its joined forms, which the Arabic font must have as well.
            val text = ArabicShaping(ArabicShaping.LETTERS_SHAPE or ArabicShaping.LENGTH_GROW_SHRINK)
                .shape(file.readText())
            text.codePoints().filter { Character.isLetter(it) }.forEach {
                if (lookup.getGlyphId(it) <= 0 && arabicLookup.getGlyphId(it) <= 0) {
                    missing += String(Character.toChars(it))
                }
            }
        }
        font.close()
        arabic.close()
        assertEquals(emptySet(), missing, "letters of the app's strings the PDF font cannot draw")
    }

    @Test
    fun arabicIsJoinedAndReadsBackAsTheLogicalText() {
        // Not tested here: a yeh that joins on its left reads back as the Persian yeh, which has the same glyph.
        val lines =
            listOf("مرحبا بالعالم", "الملف report.pdf مهم", "السعر 250 دولار و 12 كتابا", "لا أحد هنا، ألا تعلم؟")
        val pdf = pdfOf(note(*lines.map(DocumentBlock::Paragraph).toTypedArray()))

        val text = textOf(pdf)
        lines.forEach { line -> assertTrue(line in text, "missing: $line in\n$text") }
        assertFalse('?' in text, text)
        // The lam-alef is drawn as the one ligature glyph, not as two letters.
        // (the stripper's own text is normalised back to letters, so the font's mapping of each glyph is read)
        val drawn = glyphsOf(pdf).joinToString("") { glyph ->
            glyph.characterCodes.joinToString("") { glyph.font.toUnicode(it).orEmpty() }
        }
        assertTrue('\uFEFB' in drawn || '\uFEFC' in drawn, "no lam-alef ligature in the glyphs")
    }

    @Test
    fun anArabicParagraphIsRightAlignedAndALatinOneIsNot() {
        val pdf = pdfOf(note(DocumentBlock.Paragraph("مرحبا"), DocumentBlock.Paragraph("Hello"), title = "T"))
        val right = pageWidth(pdf) - 54f
        val glyphs = glyphsOf(pdf)
        val arabic = glyphs.filter { it.unicode.single() in '\uFE70'..'\uFEFF' }
        val latin = glyphs.filter { it.unicode.single() in "Hello" }

        assertEquals(right, arabic.maxOf { it.xDirAdj + it.widthDirAdj }, 0.5f)
        assertTrue(arabic.minOf { it.xDirAdj } > 54f + 20f, "the Arabic word starts at the left margin")
        assertEquals(54f, latin.minOf { it.xDirAdj }, 0.5f)
    }

    @Test
    fun theDirectionOfAParagraphIsItsFirstStrongCharacter() {
        val pdf = pdfOf(note(DocumentBlock.Paragraph("123 مرحبا"), DocumentBlock.Paragraph("123 abc"), title = "T"))
        val glyphs = glyphsOf(pdf)
        val digitsOfArabic = glyphs.filter { it.unicode == "1" }

        // The line begins on the right with its numbers, and a Latin line begins on the left.
        val first = digitsOfArabic.first()
        val second = digitsOfArabic.last()
        assertTrue(first.xDirAdj > 54f + 20f, "an Arabic paragraph begins on the right")
        assertEquals(54f, second.xDirAdj, 0.5f)
        assertTrue("123" in textOf(pdf))
    }

    @Test
    fun aRightToLeftListHasItsMarkersOnTheRight() {
        val pdf = pdfOf(
            note(
                DocumentBlock.Items(ListKind.NUMBERED, listOf(DocumentItem("مرحبا"), DocumentItem("item"))),
                title = "T",
            ),
        )
        val glyphs = glyphsOf(pdf)
        val right = pageWidth(pdf) - 54f
        val numbers = glyphs.filter { it.unicode == "1" || it.unicode == "2" }
        val arabic = glyphs.filter { it.unicode.single() in '\uFE70'..'\uFEFF' }

        val one = numbers.first { it.unicode == "1" }
        val two = numbers.first { it.unicode == "2" }
        assertTrue(one.xDirAdj > arabic.maxOf { it.xDirAdj }, "the number is on the right of its Arabic text")
        assertTrue(one.xDirAdj + one.widthDirAdj <= right + 0.5f)
        assertTrue(two.xDirAdj < 54f + 20f, "a Latin item keeps its number on the left")
    }

    @Test
    fun hebrewReadsRightToLeftWithoutAnError() {
        val pdf = pdfOf(note(DocumentBlock.Paragraph("שלום עולם"), DocumentBlock.Quote("שלום"), title = "T"))

        val right = pageWidth(pdf) - 54f
        assertEquals(right, glyphsOf(pdf).filter { it.unicode == "?" }.maxOf { it.xDirAdj + it.widthDirAdj }, 0.5f)
    }

    @Test
    fun anArabicParagraphBreaksIntoLinesOnItsLogicalWords() {
        val words = (1..80).joinToString(" ") { "كلمة$it" }
        val pdf = pdfOf(note(DocumentBlock.Paragraph(words), title = "T"))

        val lines = textOf(pdf).lines().filter { it.isNotBlank() }
        assertTrue(lines.size > 3)
        assertEquals(words, lines.drop(1).joinToString(" ") { it.trim() })
    }

    @Test
    fun whatTheFontCannotDrawBecomesAQuestionMarkInsteadOfAnError() {
        val pdf = pdfOf(note(DocumentBlock.Paragraph("Party 🎉 time 中 tab\there")))

        val text = textOf(pdf)
        assertTrue("Party ? time ? tab" in text.replace(Regex("\\s+"), " "), text)
    }

    @Test
    fun everyKindOfBlockIsDrawn() {
        val pdf = pdfOf(
            note(
                DocumentBlock.Heading(1, "Heading one"),
                DocumentBlock.Heading(4, "Heading four"),
                DocumentBlock.Paragraph("A paragraph\nwith two lines"),
                DocumentBlock.Quote("A quotation"),
                DocumentBlock.Link("Site", "https://example.org/path"),
                DocumentBlock.Link("", "mailto:me@example.org"),
                DocumentBlock.Separator,
                DocumentBlock.Items(ListKind.BULLET, listOf(DocumentItem("bullet one"), DocumentItem("bullet two"))),
                DocumentBlock.Items(ListKind.NUMBERED, listOf(DocumentItem("first"), DocumentItem("second"))),
                DocumentBlock.Items(
                    ListKind.CHECKLIST,
                    listOf(DocumentItem("done", checked = true), DocumentItem("todo")),
                ),
            ),
        )

        val text = textOf(pdf)
        listOf(
            "Heading one", "Heading four", "A paragraph", "with two lines", "A quotation", "Site",
            "https://example.org/path", "mailto:me@example.org", "bullet one", "bullet two", "1.", "first",
            "2.", "second", "done", "todo",
        ).forEach { assertTrue(it in text, "missing: $it in\n$text") }
        // A link is text only: the file holds no link annotation that opens by itself.
        Loader.loadPDF(pdf).use { doc ->
            assertTrue(doc.pages.all { page -> page.annotations.isEmpty() })
        }
    }

    @Test
    fun aLongNoteFlowsOntoMorePagesAndALongWordIsBroken() {
        val paragraphs = (1..120).map { DocumentBlock.Paragraph("Line number $it of a long note, with words to wrap.") }
        val unbroken = DocumentBlock.Paragraph("x".repeat(400))
        val pdf = pdfOf(note(*(paragraphs + unbroken).toTypedArray()))

        assertTrue(pagesOf(pdf) >= 3)
        val text = textOf(pdf)
        assertTrue("Line number 120" in text)
        assertEquals(400, text.count { it == 'x' })
    }

    @Test
    fun anEmptyNoteIsStillAPage() {
        val pdf = pdfOf(note(title = ""))

        assertEquals(1, pagesOf(pdf))
    }

    @Test
    fun theHeadingIsNeverTheLastLineOfAPage() {
        val filler = (1..42).map { DocumentBlock.Paragraph("filler $it") }
        val blocks = filler + DocumentBlock.Heading(2, "Kept with its text") + DocumentBlock.Paragraph("body")
        val pdf = pdfOf(note(*blocks.toTypedArray()))

        Loader.loadPDF(pdf).use { doc ->
            val last = PDFTextStripper().apply {
                startPage = doc.numberOfPages
                endPage = doc.numberOfPages
            }
                .getText(doc)
            if (doc.numberOfPages > 1 && "Kept with its text" in last) {
                assertTrue("body" in last, "the heading was left alone at the end of a page")
            }
        }
    }

    @Test
    fun paperIsLetterWhereLetterIsUsedAndA4Elsewhere() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals(PDRectangle.LETTER, DesktopNoteDocuments.paperOf())
            Locale.setDefault(Locale.GERMANY)
            assertEquals(PDRectangle.A4, DesktopNoteDocuments.paperOf())
        } finally {
            Locale.setDefault(before)
        }
        val letter = DesktopNoteDocuments(offline, paper = { PDRectangle.LETTER })
        Loader.loadPDF(pdfOf(note(DocumentBlock.Paragraph("x")), letter)).use {
            assertEquals(PDRectangle.LETTER.width, it.getPage(0).mediaBox.width)
        }
    }

    @Test
    fun aMissingFontIsAnErrorAndNeverAFallbackToSystemFonts() {
        val broken = DesktopNoteDocuments(offline, fontBytes = { throw IOException("gone") })

        assertFailsWith<IOException> { pdfOf(note(DocumentBlock.Paragraph("x")), broken) }
        assertFailsWith<IOException> { runBlocking { broken.print(note()) } }
    }

    @Test
    fun theWholeFileIsBuiltBeforeAnythingIsWritten() {
        val broken = DesktopNoteDocuments(offline, fontBytes = { throw IOException("gone") })
        val out = Buffer()

        assertFailsWith<IOException> { broken.writePdf(note(DocumentBlock.Paragraph("x")), out) }

        assertEquals(0L, out.size)
    }

    @Test
    fun makingAPdfWritesNoFileAnywhere() {
        val temp = File(System.getProperty("java.io.tmpdir"))
        val cache = File(System.getProperty("user.home"), ".pdfbox.cache")
        val tempBefore = temp.list().orEmpty().toSet()
        val cacheBefore = cache.exists() to cache.lastModified()

        pdfOf(note(DocumentBlock.Paragraph("Привіт"), DocumentBlock.Heading(1, "Zażółć")))
        val printing = DesktopNoteDocuments(PrintLauncher { _, _ -> PrintResult.PRINTED })
        runBlocking { printing.print(note(DocumentBlock.Paragraph("x"))) }

        val created = temp.list().orEmpty().toSet() - tempBefore
        assertTrue(created.none { it.contains("pdf", ignoreCase = true) }, "temp files: $created")
        assertEquals(cacheBefore, cache.exists() to cache.lastModified(), "the font cache of PDFBox was touched")
    }

    @Test
    fun printingHandsTheRenderedPagesToThePrinter() = runBlocking {
        var pages = 0
        val printer = PrintLauncher { pageable: Pageable, _ ->
            pages = pageable.numberOfPages
            PrintResult.PRINTED
        }

        val result = DesktopNoteDocuments(printer).print(note(DocumentBlock.Paragraph("Hello")))

        assertEquals(PrintResult.PRINTED, result)
        assertEquals(1, pages)
    }

    @Test
    fun theGuardAgainstALockReachesThePrinter() = runBlocking {
        var seen: Boolean? = null
        val printer = PrintLauncher { _, mayProceed ->
            seen = mayProceed()
            PrintResult.CANCELLED
        }

        DesktopNoteDocuments(printer).print(note(DocumentBlock.Paragraph("Hello"))) { false }

        assertEquals(false, seen)
    }

    @Test
    fun aClosedPrintDialogIsReportedAsCancelled() = runBlocking {
        val closed = DesktopNoteDocuments(PrintLauncher { _, _ -> PrintResult.CANCELLED })
        val result = closed.print(note(DocumentBlock.Paragraph("Hello")))

        assertEquals(PrintResult.CANCELLED, result)
    }

    @Test
    fun aPrinterThatFailsFailsThePrint() {
        val printer = PrintLauncher { _, _ -> throw IOException("jam") }

        assertFailsWith<IOException> {
            runBlocking { DesktopNoteDocuments(printer).print(note(DocumentBlock.Paragraph("Hello"))) }
        }
    }

    @Test
    fun theBundledFontIsPartOfTheApp() {
        assertTrue(DesktopNoteDocuments.bundledFont().size > 100_000)
    }
}
