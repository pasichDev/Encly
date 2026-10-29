package com.pasich.encly.editor.codec

import kotlin.test.Test
import kotlin.test.assertEquals

class InlineHtmlTest {

    @Test
    fun formattingTagsAreRemoved() {
        assertEquals(
            "bold italic marked code link",
            InlineHtml.toPlainText(
                "<b>bold</b> <i>italic</i> <mark class=\"cdx-marker\">marked</mark> " +
                    "<code class=\"inline-code\">code</code> <a href=\"https://example.org\">link</a>",
            ),
        )
    }

    @Test
    fun lineBreaksBecomeNewlines() {
        assertEquals("a\nb\nc\nd", InlineHtml.toPlainText("a<br>b<BR/>c<br />d"))
    }

    @Test
    fun entitiesAreDecodedOnce() {
        assertEquals(
            "<b> & \"q\" 'a' x y",
            InlineHtml.toPlainText("&lt;b&gt; &amp; &quot;q&quot; &#39;a&apos; x&nbsp;y"),
        )
        assertEquals("&lt;", InlineHtml.toPlainText("&amp;lt;"))
        assertEquals("€ 😀", InlineHtml.toPlainText("&#8364; &#x1F600;"))
    }

    @Test
    fun unknownOrInvalidEntitiesStayAsTyped() {
        assertEquals("&bogus; &#0; &#xD800; &#99999999;", InlineHtml.toPlainText("&bogus; &#0; &#xD800; &#99999999;"))
    }

    @Test
    fun theEdgesOfTheCodePointRangeAreDecidedExactly() {
        // One past the last code point and the last low surrogate stay as typed; the code points around
        // the surrogates, the first of the supplementary planes and the last one are text.
        assertEquals(
            "&#1114112; &#xDFFF; ퟿  𐀀 􏿿 \u0001",
            InlineHtml.toPlainText("&#1114112; &#xDFFF; &#xD7FF; &#xE000; &#x10000; &#x10FFFF; &#1;"),
        )
    }

    @Test
    fun textThatOnlyLooksLikeMarkupIsKept() {
        assertEquals("a < b > c, 3<4", InlineHtml.toPlainText("a < b > c, 3<4"))
    }

    @Test
    fun invisibleEditorCharactersAreDropped() {
        val input = "${Char(0x200B)}a${Char(0x00A0)}b${Char(0xFEFF)}"
        assertEquals("a b", InlineHtml.toPlainText(input))
    }

    @Test
    fun onlyKnownTagsGoWhenAskedAndOtherAngleBracketTextStays() {
        assertEquals(
            "bold link\nMap<String, Int> <name> a<b && c>d <script>x",
            InlineHtml.toPlainText(
                "<b>bold</b> <a href=\"u\">link</a><br>Map<String, Int> <name> a<b && c>d <script>x",
                knownTagsOnly = true,
            ),
        )
        // The hand-off still removes every tag.
        assertEquals("Dear , x", InlineHtml.toPlainText("Dear <name>, x"))
    }
}
