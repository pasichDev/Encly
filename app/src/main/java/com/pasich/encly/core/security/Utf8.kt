package com.pasich.encly.core.security

import java.nio.CharBuffer

internal object Utf8 {
    /**
     * [chars] in UTF-8, in a fresh array the caller wipes. No String copy is made, and the
     * encoder's intermediate buffer is zeroized before returning.
     */
    fun encode(chars: CharArray): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (buffer.hasArray()) SensitiveDataCleaner.clear(buffer.array())
        return bytes
    }
}
