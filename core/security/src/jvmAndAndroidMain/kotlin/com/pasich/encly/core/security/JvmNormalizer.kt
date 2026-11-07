package com.pasich.encly.core.security

import java.nio.CharBuffer
import java.text.Normalizer

/*
 * The JVM and Android side of Passwords.normalize. Text that is already in NFC is copied as it is, so
 * only text that really changes is turned into a String by the normalizer.
 */

internal actual fun normalizeNfc(text: CharArray): CharArray {
    val view = CharBuffer.wrap(text)
    return if (Normalizer.isNormalized(view, Normalizer.Form.NFC)) {
        text.copyOf()
    } else {
        Normalizer.normalize(view, Normalizer.Form.NFC).toCharArray()
    }
}
