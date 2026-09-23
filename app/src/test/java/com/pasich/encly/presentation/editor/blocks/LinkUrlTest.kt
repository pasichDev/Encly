package com.pasich.encly.presentation.editor.blocks

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkUrlTest {

    @Test
    fun bareAddressGetsHttps() {
        assertEquals("https://example.com/a", normalizeLinkUrl("  example.com/a "))
    }

    @Test
    fun explicitSchemesAreKept() {
        assertEquals("http://example.com", normalizeLinkUrl("http://example.com"))
        assertEquals("mailto:me@example.com", normalizeLinkUrl("mailto:me@example.com"))
    }

    @Test
    fun blankStaysBlank() {
        assertEquals("", normalizeLinkUrl("   "))
    }
}
