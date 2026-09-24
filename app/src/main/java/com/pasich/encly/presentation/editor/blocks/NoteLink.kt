package com.pasich.encly.presentation.editor.blocks

import com.pasich.encly.domain.model.LinkDataBlock
import java.net.IDN

// What a note link may be. Encly never fetches a link: it shows the address and hands it to an
// app the user picks. Only web and mail links are saved or opened, and what the card shows is
// read the way that app will read it, so the card can never name a different site.

/** A note link, read from its address. */
sealed interface NoteLink {
    val url: String

    /** An http(s) link to [host]; [detail] is the rest of the address, shown under it. */
    data class Web(override val url: String, val host: String, val detail: String) : NoteLink

    /** A mailto: link to [address]. */
    data class Mail(override val url: String, val address: String) : NoteLink

    /**
     * Any other address (file:, content:, intent:, javascript:, ...) or one that could be read
     * two ways: shown as text, never opened.
     */
    data class Blocked(override val url: String) : NoteLink
}

private val URL_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
private val WEB_PREFIX = Regex("^https?://", RegexOption.IGNORE_CASE)
private const val MAILTO = "mailto:"

// Letters and digits of any script, dots and hyphens: a host name, nothing that decodes to one.
private val HOST_NAME = Regex("^[\\p{L}\\p{N}.-]+$")
private val IPV6_HOST = Regex("^\\[[0-9a-fA-F:.]+]$")
private val PORT = Regex(":\\d{1,5}$")

/**
 * Trims [rawUrl] and gives a bare address ("example.com") an https scheme, so the saved link
 * can be opened. A URL that already names a scheme (https:, mailto:, ...) is kept as typed.
 */
internal fun normalizeLinkUrl(rawUrl: String): String {
    val url = rawUrl.trim()
    return if (url.isEmpty() || URL_SCHEME.containsMatchIn(url)) url else "https://$url"
}

/** [url] read as a note link. */
internal fun parseNoteLink(url: String): NoteLink = when {
    // A backslash, a space or a control character is read differently by different parsers
    // (a browser takes "https://evil.tld\@bank.com" to evil.tld, Android's Uri to bank.com).
    url.isEmpty() || url.any(::isAmbiguous) -> NoteLink.Blocked(url)

    WEB_PREFIX.containsMatchIn(url) -> parseWebLink(url)

    url.startsWith(MAILTO, ignoreCase = true) -> parseMailLink(url)

    else -> NoteLink.Blocked(url)
}

private fun isAmbiguous(char: Char): Boolean = char == '\\' || char.isWhitespace() || char.isISOControl()

private fun parseWebLink(url: String): NoteLink {
    val afterScheme = url.substringAfter("://")
    val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
    val host = authority.replace(PORT, "")
    // User info ("name@host") is how a link pretends to be another site; a %-escape in the host
    // is one the card would show undecoded.
    val plainHost = '@' !in authority && '%' !in authority && host.isNotEmpty()
    val validHost = plainHost && (HOST_NAME.matches(host) || IPV6_HOST.matches(host))
    val detail = afterScheme.removePrefix(authority).trimEnd('/')
    val shownHost = if (validHost) displayHost(host) else null
    return if (shownHost != null) NoteLink.Web(url, host = shownHost, detail = detail) else NoteLink.Blocked(url)
}

/**
 * [host] as the card shows it: lower case, and a name in other scripts in its ASCII (punycode)
 * form, as browsers show one that could imitate another site ("аpple.com" with a Cyrillic "а").
 * Null when it is not a valid name.
 */
private fun displayHost(host: String): String? {
    val lower = host.lowercase()
    return if (lower.all { it.code < ASCII_END }) lower else runCatching { IDN.toASCII(lower) }.getOrNull()
}

private const val ASCII_END = 0x80

private fun parseMailLink(url: String): NoteLink {
    val address = url.substring(MAILTO.length).substringBefore('?')
    return if ('@' in address) NoteLink.Mail(url, address) else NoteLink.Blocked(url)
}

/** Whether Encly may hand [url] to another app to open. */
internal fun isOpenableLink(url: String): Boolean = parseNoteLink(url) !is NoteLink.Blocked

/**
 * The link to store for the address [rawUrl] the user entered or pasted; null when it may not be
 * saved (see [NoteLink.Blocked]). Offline: only the address and its host as the title.
 */
internal fun buildLinkData(rawUrl: String): LinkDataBlock? {
    val link = parseNoteLink(normalizeLinkUrl(rawUrl))
    val title = when (link) {
        is NoteLink.Web -> link.host
        is NoteLink.Mail -> link.address
        is NoteLink.Blocked -> return null
    }
    return LinkDataBlock(title = title, imageUrl = "", url = link.url, isError = false)
}

/** What a saved link's card shows: its title line and the line under it. */
internal fun NoteLink.cardLines(): Pair<String, String> = when (this) {
    is NoteLink.Web -> host.removePrefix("www.") to detail
    is NoteLink.Mail -> address to ""
    is NoteLink.Blocked -> url to ""
}
