package com.pasich.encly.data.importer

import com.pasich.encly.data.importer.ImportFixtures.describe
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownBlockMapperTest {
    private fun blocks(markdown: String) = describe(MarkdownBlockMapper.toBlocks(markdown))

    @Test
    fun headingsAreH1ToH4AndDeeperOnesH4() {
        assertEquals(
            listOf("h1:A", "h2:B", "h3:C", "h4:D", "h4:E", "h4:F"),
            blocks("# A\n## B\n### C\n#### D\n##### E\n###### F"),
        )
    }

    @Test
    fun listsAreFlatAndKeepTheirKind() {
        assertEquals(listOf("check:[x] a|[ ] b|[x] c"), blocks("- [x] a\n* [ ] b\n+ [X] c"))
        assertEquals(listOf("bullet:[ ] a|[ ] b|[ ] c"), blocks("- a\n* b\n+ c"))
        assertEquals(listOf("number:[ ] one|[ ] two"), blocks("1. one\n2) two"))
        assertEquals(listOf("bullet:[ ] a|[ ] nested|[ ] b"), blocks("- a\n    - nested\n- b"))
    }

    @Test
    fun aLooseListStaysOneBlockAndAKindChangeStartsANewOne() {
        assertEquals(listOf("bullet:[ ] a|[ ] b", "number:[ ] one"), blocks("- a\n\n- b\n1. one"))
    }

    @Test
    fun quotesRulesAndParagraphs() {
        assertEquals(
            listOf("text:first\nstill first", "quote:q1\nq2", "separator", "separator", "text:last"),
            blocks("first\nstill first\n\n> q1\n> q2\n\n---\n***\n\nlast"),
        )
    }

    @Test
    fun anIndentedLineContinuesTheListItem() {
        assertEquals(listOf("bullet:[ ] a b|[ ] c"), blocks("- a\n  b\n- c"))
    }

    @Test
    fun codeIsKeptAsTextWithoutItsFences() {
        assertEquals(listOf("text:val x = <b>1</b>\nprint(x)"), blocks("```kotlin\nval x = <b>1</b>\nprint(x)\n```"))
    }

    @Test
    fun htmlIsStrippedOutsideCodeSpansAndLinksSurvive() {
        assertEquals(
            listOf("text:bold and `<b>code</b>` <https://x.org> a & b".replace("<https://x.org>", "https://x.org")),
            blocks("<b>bold</b> and `<b>code</b>` <https://x.org> a &amp; b"),
        )
    }

    @Test
    fun noTextIsDropped() {
        assertEquals(
            listOf("text:#hashtag no space", "text:| a | b |\n|---|---|"),
            blocks("#hashtag no space\n\n| a | b |\n|---|---|"),
        )
        assertEquals(emptyList<String>(), blocks("\n\n  \n"))
    }
}
