package com.pasich.encly.data.handoff

import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun textThatOnlyLooksLikeMarkupIsKept() {
        assertEquals("a < b > c, 3<4", InlineHtml.toPlainText("a < b > c, 3<4"))
    }

    @Test
    fun invisibleEditorCharactersAreDropped() {
        val input = "${Char(0x200B)}a${Char(0x00A0)}b${Char(0xFEFF)}"
        assertEquals("a b", InlineHtml.toPlainText(input))
    }
}
