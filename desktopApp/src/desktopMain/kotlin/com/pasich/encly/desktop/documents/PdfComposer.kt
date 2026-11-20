package com.pasich.encly.desktop.documents

import com.pasich.encly.data.export.DocumentBlock
import com.pasich.encly.data.export.ListKind
import com.pasich.encly.data.export.NoteDocument
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode
import org.apache.pdfbox.util.Matrix
import java.awt.Color
import java.io.Closeable

/** The text sizes, margins and colours of a printed note, in PDF points (1/72 inch). */
@Suppress("MagicNumber") // The page's geometry and palette themselves.
internal object PageStyle {
    const val MARGIN = 54f
    const val BODY = 11f
    const val LEADING = 1.38f
    const val BLOCK_GAP = 7f
    const val INDENT = 18f
    val HEADING_SIZES = floatArrayOf(22f, 17f, 14f, 12f)
    const val TITLE_SIZE = 24f
    const val META_SIZE = 9f
    val INK = Color(0x1A, 0x1A, 0x1A)
    val QUIET = Color(0x6B, 0x6B, 0x6B)
    val RULE = Color(0xB5, 0xB5, 0xB5)
    const val SYNTHETIC_BOLD = 0.035f
}

/**
 * Draws a [NoteDocument] onto PDF pages: the title, then headings, paragraphs, quotes, lists, checklists, links
 * (as text) and rules, flowing onto as many pages as it needs. The embedded fonts of [layout] draw everything
 * (Latin and Cyrillic, Arabic); a character none of them has is drawn as `?`, never an error. A block reads in the
 * direction of its first strong character: right-to-left blocks are right-aligned, with their markers and bars on
 * the right. Bold is the same font, stroked a little. Nothing here reads a file or uses the network.
 */
@Suppress("MagicNumber", "TooManyFunctions") // One page layout: proportions of a glyph and a drawing step each.
internal class PdfComposer(
    private val document: PDDocument,
    private val layout: TextLayout,
    private val paper: PDRectangle,
) : Closeable {
    private var stream: PDPageContentStream? = null
    private var y = 0f
    private val left = PageStyle.MARGIN
    private val width = paper.width - 2 * PageStyle.MARGIN
    private val right = left + width

    fun compose(note: NoteDocument) {
        newPage()
        paragraph(note.title, PageStyle.TITLE_SIZE, bold = true, color = PageStyle.INK)
        if (note.tags.isNotEmpty()) {
            paragraph(note.tags.joinToString(", "), PageStyle.META_SIZE, color = PageStyle.QUIET)
        }
        y -= PageStyle.BLOCK_GAP
        note.blocks.forEach { block ->
            draw(block)
            y -= PageStyle.BLOCK_GAP
        }
    }

    override fun close() {
        stream?.close()
        stream = null
    }

    private fun draw(block: DocumentBlock) {
        when (block) {
            is DocumentBlock.Heading -> {
                val size = PageStyle.HEADING_SIZES[(block.level - 1).coerceIn(0, PageStyle.HEADING_SIZES.lastIndex)]
                // A heading is never the last line of a page.
                ensureRoom(size * PageStyle.LEADING + PageStyle.BODY * PageStyle.LEADING * 2)
                paragraph(block.text, size, bold = true)
            }

            is DocumentBlock.Paragraph -> paragraph(block.text, PageStyle.BODY)

            is DocumentBlock.Quote -> quote(block.text)

            is DocumentBlock.Link -> link(block)

            DocumentBlock.Separator -> rule()

            is DocumentBlock.Items -> items(block)
        }
    }

    private fun quote(text: String) {
        val rtl = layout.isRtl(text)
        val from = if (rtl) left else left + PageStyle.INDENT
        val to = if (rtl) right - PageStyle.INDENT else right
        wrap(clean(text), PageStyle.BODY, width - PageStyle.INDENT).forEach { line ->
            ensureRoom(lineHeight(PageStyle.BODY))
            val top = y
            text(line, rtl, Column(from, to), PageStyle.BODY, PageStyle.QUIET)
            // The quote's bar runs beside every line of the block, page by page.
            bar(if (rtl) right - 3f else left + 3f, top, y)
        }
    }

    private fun link(block: DocumentBlock.Link) {
        if (block.title.isNotEmpty() && block.title != block.url) {
            paragraph(block.title, PageStyle.BODY)
        }
        // Only the address as text: nothing in the file opens it by itself.
        paragraph(block.url, PageStyle.BODY - 1f, color = PageStyle.QUIET)
    }

    private fun items(block: DocumentBlock.Items) {
        block.items.forEachIndexed { index, item ->
            val rtl = layout.isRtl(item.text)
            val from = if (rtl) left else left + PageStyle.INDENT + MARKER_ROOM
            val to = if (rtl) right - PageStyle.INDENT - MARKER_ROOM else right
            val lines = wrap(clean(item.text), PageStyle.BODY, width - PageStyle.INDENT - MARKER_ROOM)
            ensureRoom(lineHeight(PageStyle.BODY))
            val baseline = y - PageStyle.BODY
            // The marker sits at the start of the item: on the left for left-to-right text, else on the right.
            val markerEdge = if (rtl) right - PageStyle.INDENT else left + PageStyle.INDENT
            val side = PageStyle.BODY - 1f
            when (block.kind) {
                ListKind.BULLET ->
                    bullet(if (rtl) markerEdge - 3f else markerEdge + 3f, baseline + PageStyle.BODY * 0.32f)

                ListKind.NUMBERED -> drawNumber(
                    "${index + 1}.",
                    rtl,
                    if (rtl) markerEdge + 2f else markerEdge - 2f,
                    baseline,
                )

                ListKind.CHECKLIST ->
                    checkbox(if (rtl) markerEdge - side else markerEdge, baseline, item.checked)
            }
            lines.forEach { line ->
                ensureRoom(lineHeight(PageStyle.BODY))
                text(line, rtl, Column(from, to), PageStyle.BODY, if (item.checked) PageStyle.QUIET else PageStyle.INK)
            }
            y -= LIST_GAP
        }
    }

    private fun paragraph(text: String, size: Float, bold: Boolean = false, color: Color = PageStyle.INK) {
        val rtl = layout.isRtl(text)
        wrap(clean(text), size, width).forEach { line ->
            ensureRoom(lineHeight(size))
            text(line, rtl, Column(left, right), size, color, bold)
        }
    }

    /** Where text may run: from the [from] x to the [to] x. */
    private class Column(val from: Float, val to: Float)

    /**
     * Draws [line] at the cursor, against the left edge or the right edge of [column] as its direction [rtl]
     * says, and moves the cursor down one line.
     */
    private fun text(line: String, rtl: Boolean, column: Column, size: Float, color: Color, bold: Boolean = false) {
        val baseline = y - size
        if (line.isNotEmpty()) {
            val runs = layout.visual(line, rtl)
            drawRuns(runs, if (rtl) column.to - lineWidth(runs, size) else column.from, baseline, size, color, bold)
        }
        y -= lineHeight(size)
    }

    /** Draws a list number on [baseline] with its end at [edge]: the right end for [rtl] text, else the left end. */
    private fun drawNumber(text: String, rtl: Boolean, edge: Float, baseline: Float) {
        val runs = layout.visual(text, rtl)
        drawRuns(
            runs,
            if (rtl) edge - lineWidth(runs, PageStyle.BODY) else edge,
            baseline,
            PageStyle.BODY,
            PageStyle.INK,
        )
    }

    private fun lineWidth(runs: List<GlyphRun>, size: Float): Float =
        runs.sumOf { it.face.font.getStringWidth(it.text).toDouble() }.toFloat() / FONT_UNITS * size

    private fun drawRuns(
        runs: List<GlyphRun>,
        x: Float,
        baseline: Float,
        size: Float,
        color: Color,
        bold: Boolean = false,
    ) {
        val cs = checkNotNull(stream)
        cs.beginText()
        cs.setNonStrokingColor(color)
        if (bold) {
            cs.setRenderingMode(RenderingMode.FILL_STROKE)
            cs.setStrokingColor(color)
            cs.setLineWidth(size * PageStyle.SYNTHETIC_BOLD)
        } else {
            cs.setRenderingMode(RenderingMode.FILL)
        }
        cs.setTextMatrix(Matrix.getTranslateInstance(x, baseline))
        // Each run moves the pen on, so the runs of different fonts follow one another.
        runs.forEach { run ->
            cs.setFont(run.face.font, size)
            cs.showText(run.text)
        }
        cs.endText()
    }

    private fun bullet(cx: Float, cy: Float) {
        val cs = checkNotNull(stream)
        cs.setNonStrokingColor(PageStyle.INK)
        circle(cs, cx, cy, BULLET_RADIUS)
        cs.fill()
    }

    private fun checkbox(x: Float, baseline: Float, checked: Boolean) {
        val cs = checkNotNull(stream)
        val side = PageStyle.BODY - 1f
        val bottom = baseline - 1f
        cs.setStrokingColor(PageStyle.INK)
        cs.setLineWidth(0.9f)
        cs.addRect(x, bottom, side, side)
        cs.stroke()
        if (checked) {
            cs.moveTo(x + side * 0.2f, bottom + side * 0.5f)
            cs.lineTo(x + side * 0.43f, bottom + side * 0.22f)
            cs.lineTo(x + side * 0.82f, bottom + side * 0.8f)
            cs.stroke()
        }
    }

    private fun rule() {
        ensureRoom(PageStyle.BODY)
        val cs = checkNotNull(stream)
        val at = y - PageStyle.BODY / 2
        cs.setStrokingColor(PageStyle.RULE)
        cs.setLineWidth(0.8f)
        cs.moveTo(left, at)
        cs.lineTo(left + width, at)
        cs.stroke()
        y -= PageStyle.BODY
    }

    private fun bar(x: Float, from: Float, to: Float) {
        val cs = checkNotNull(stream)
        cs.setStrokingColor(PageStyle.RULE)
        cs.setLineWidth(2f)
        cs.moveTo(x, from)
        cs.lineTo(x, to)
        cs.stroke()
    }

    private fun circle(cs: PDPageContentStream, cx: Float, cy: Float, r: Float) {
        val k = KAPPA * r
        cs.moveTo(cx + r, cy)
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        cs.closePath()
    }

    private fun lineHeight(size: Float) = size * PageStyle.LEADING

    /** Starts a new page when [needed] points do not fit below the cursor. */
    private fun ensureRoom(needed: Float) {
        if (y - needed < PageStyle.MARGIN) newPage()
    }

    private fun newPage() {
        stream?.close()
        val page = PDPage(paper)
        document.addPage(page)
        stream = PDPageContentStream(document, page)
        y = paper.height - PageStyle.MARGIN
    }

    /** [text] with tabs widened, non-breaking spaces made plain, line ends made `\n` and control characters dropped. */
    private fun clean(text: String): String = buildString(text.length) {
        val flat = text.replace("\r\n", "\n").replace('\r', '\n')
        flat.forEach { char ->
            when {
                char == '\n' -> append('\n')
                char == '\t' -> append("    ")
                char.code == NO_BREAK_SPACE -> append(' ')
                Character.isISOControl(char) -> Unit
                else -> append(char)
            }
        }
    }

    /** [text] broken into lines that fit [maxWidth] at [size]; an empty line stays an empty line. */
    private fun wrap(text: String, size: Float, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            if (paragraph.isBlank()) {
                lines += ""
                return@forEach
            }
            var current = ""
            for (word in paragraph.split(' ')) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (measure(candidate, size) <= maxWidth) {
                    current = candidate
                    continue
                }
                if (current.isNotEmpty()) lines += current
                current = ""
                var rest = word
                // A word wider than the line is broken where it must be.
                while (measure(rest, size) > maxWidth) {
                    val cut = fitting(rest, size, maxWidth)
                    lines += rest.substring(0, cut)
                    rest = rest.substring(cut)
                }
                current = rest
            }
            lines += current
        }
        return lines.dropLastWhile { it.isEmpty() }.ifEmpty { listOf("") }
    }

    private fun measure(text: String, size: Float): Float = layout.width(text) / FONT_UNITS * size

    /**
     * How many chars of [word] fit in [maxWidth] (at least one code point, so the loop always ends). A combining
     * mark stays with its letter.
     */
    private fun fitting(word: String, size: Float, maxWidth: Float): Int {
        var end = word.offsetByCodePoints(0, 1)
        while (end < word.length) {
            val next = word.offsetByCodePoints(end, 1)
            if (measure(word.substring(0, next), size) > maxWidth) break
            end = next
        }
        while (end < word.length && isMark(word.codePointAt(end))) end += Character.charCount(word.codePointAt(end))
        return end
    }

    private fun isMark(point: Int) = Character.getType(point).let {
        it == Character.NON_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt()
    }

    private companion object {
        const val FONT_UNITS = 1000f
        const val MARKER_ROOM = 16f
        const val LIST_GAP = 2f
        const val BULLET_RADIUS = 1.9f
        const val KAPPA = 0.5523f
        const val NO_BREAK_SPACE = 0x00A0
    }
}
