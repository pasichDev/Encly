package com.pasich.encly.editor.ui.blocks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun webAndMailLinksAreRead() {
        assertEquals(
            NoteLink.Web("https://www.Example.com:8443/a/b/", "www.example.com", "/a/b"),
            parseNoteLink("https://www.Example.com:8443/a/b/"),
        )
        assertEquals(
            NoteLink.Mail("mailto:me@example.com?subject=hi", "me@example.com"),
            parseNoteLink("mailto:me@example.com?subject=hi"),
        )
        assertTrue(isOpenableLink("http://[::1]/x"))
    }

    @Test
    fun onlyWebAndMailLinksMayBeOpenedOrSaved() {
        listOf(
            "file:///data/data/com.pasich.encly/databases/vault.db",
            "content://com.android.contacts/contacts",
            "intent://scan/#Intent;scheme=zxing;end",
            "javascript:alert(1)",
            "tel:123",
            "ftp://example.com",
        ).forEach { url ->
            assertTrue(parseNoteLink(url) is NoteLink.Blocked, url)
            assertFalse(isOpenableLink(url), url)
            assertNull(buildLinkData(url), url)
        }
    }

    @Test
    fun anAddressThatReadsAsTwoHostsIsNeverShownAsEither() {
        listOf(
            "https://evil.tld\\@bank.com",
            "https://bank.com@evil.tld/login",
            "https://user:pass@bank.com",
            "https://%65vil.com",
            "https://bank.com evil.tld",
            "https://bank.com\u0000.evil.tld",
            "https:///path",
        ).forEach { url -> assertTrue(parseNoteLink(url) is NoteLink.Blocked, url) }
    }

    @Test
    fun aHostInAnotherScriptIsShownAsBrowsersShowIt() {
        // A Cyrillic "а" in place of the Latin "a".
        val link = parseNoteLink("https://\u0430pple.com/login") as NoteLink.Web

        assertEquals("xn--pple-43d.com", link.host)
    }

    @Test
    fun theCardShowsTheHostTheLinkOpens() {
        val link = buildLinkData("example.com/docs?page=2")!!

        assertEquals("https://example.com/docs?page=2", link.url)
        assertEquals("example.com", link.title)
        assertEquals("example.com" to "/docs?page=2", parseNoteLink(link.url).cardLines())
        assertEquals("host.com" to "", parseNoteLink("https://www.host.com").cardLines())
    }
}
