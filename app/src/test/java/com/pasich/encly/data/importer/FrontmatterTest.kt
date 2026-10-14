package com.pasich.encly.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontmatterTest {
    @Test
    fun readsScalarsListsAndInlineListsAndIgnoresTheRest() {
        val doc = Frontmatter.split(
            """
            ---
            title: "A \"quoted\" title"
            created: 2024-03-01
            tags:
              - one
              - 'two'
            aliases: [x, y]
            nested:
              deep: value
            note: |
              block text
            ---
            body here
            """.trimIndent(),
        )
        assertEquals("A \"quoted\" title", doc.value("title"))
        assertEquals("2024-03-01", doc.value("created"))
        assertEquals(listOf("one", "two"), doc.list("tags"))
        assertEquals(listOf("x", "y"), doc.list("aliases"))
        assertFalse(doc.has("deep"))
        assertEquals("body here", doc.body)
    }

    @Test
    fun aCommaSeparatedScalarIsAList() {
        val doc = Frontmatter.split("---\ntags: a, b ,c\n---\nx")
        assertEquals(listOf("a", " b ", "c").map { it }, doc.list("tags"))
    }

    @Test
    fun withoutAClosingMarkerTheWholeFileIsBody() {
        val text = "---\ntitle: x\nno end"
        val doc = Frontmatter.split(text)
        assertFalse(doc.hasFrontmatter)
        assertEquals(text, doc.body)
    }

    @Test
    fun aFileThatDoesNotStartWithTheMarkerHasNone() {
        val doc = Frontmatter.split("intro\n---\ntitle: x\n---\n")
        assertNull(doc.value("title"))
        assertTrue(doc.body.startsWith("intro"))
    }

    @Test
    fun aCommentOrAnchorIsNeverExecuted() {
        val doc = Frontmatter.split("---\nrun: !!python/object/apply:os.system [\"x\"]\ntitle: ok\n---\n")
        assertEquals("ok", doc.value("title"))
    }
}
