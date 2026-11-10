package com.pasich.encly.editor.ui.blocks

/*
 * The one platform-specific call left in the editor: converting an internationalized host name to
 * its ASCII (punycode) form. Browsers show a name in other scripts this way because it can imitate
 * another site ("аpple.com" with a Cyrillic "а"), and the link card does the same, so the card can
 * never name a different site than the one the link opens.
 *
 * Android and the desktop JVM use java.net.IDN, which also normalizes and rejects invalid names.
 * iOS has no such call in Kotlin/Native's Foundation, so it prepares the name the way Nameprep does (NFKC from
 * Foundation, the mapping and the prohibited characters of IDNA 2003) and encodes it with the pure-Kotlin [Punycode]
 * encoder; AsciiHostTest pins both to the JVM answers.
 */

/** [host] (lower case, with a non-ASCII character) in its ASCII form; null when it is not a valid name. */
internal expect fun toAsciiHost(host: String): String?
