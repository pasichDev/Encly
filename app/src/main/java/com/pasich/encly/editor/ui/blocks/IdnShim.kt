package com.pasich.encly.editor.ui.blocks

import java.net.IDN

/*
 * The one platform-specific call left in the editor: converting an internationalized host name to
 * its ASCII (punycode) form. Browsers show a name in other scripts this way because it can imitate
 * another site ("аpple.com" with a Cyrillic "а"), and the link card does the same, so the card can
 * never name a different site than the one the link opens. IDN also normalizes and rejects invalid
 * names, which a hand-written punycode encoder would have to repeat. When the editor moves to a
 * multiplatform module this file becomes an `expect`/`actual` pair; nothing else changes.
 */

/** [host] (lower case, with a non-ASCII character) in its ASCII form; null when it is not a valid name. */
internal fun toAsciiHost(host: String): String? = runCatching { IDN.toASCII(host) }.getOrNull()
