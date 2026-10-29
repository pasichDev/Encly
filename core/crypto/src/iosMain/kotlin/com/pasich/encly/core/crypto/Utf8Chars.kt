package com.pasich.encly.core.crypto

private const val ASCII_MAX = 0x7F
private const val TWO_BYTE_MAX = 0x7FF
private const val SIX_BITS = 0x3F
private const val SIX = 6
private const val TWELVE = 12
private const val EIGHTEEN = 18
private const val TEN = 10
private const val LEAD_2 = 0xC0
private const val LEAD_3 = 0xE0
private const val LEAD_4 = 0xF0
private const val CONTINUATION = 0x80
private const val SUPPLEMENTARY_BASE = 0x10000
private const val THREE_BYTES = 3
private const val FOUR_BYTES = 4
private const val REPLACEMENT = '?'.code.toByte()

/**
 * [chars] in UTF-8, in a fresh array the caller wipes: what the JVM's `PBEKeySpec` feeds PBKDF2. No String
 * and no intermediate buffer is made, so nothing but the returned array holds the encoding. An unpaired
 * surrogate becomes `?`, as the JDK's encoder does. (:core:security has the same encoder for its own use; this
 * module cannot depend on it.)
 */
internal fun encodeUtf8(chars: CharArray): ByteArray {
    val bytes = ByteArray(utf8Size(chars))
    var out = 0
    var index = 0
    while (index < chars.size) {
        val char = chars[index]
        val code = char.code
        val paired = char.isHighSurrogate() && index + 1 < chars.size && chars[index + 1].isLowSurrogate()
        when {
            code <= ASCII_MAX -> bytes[out++] = code.toByte()

            code <= TWO_BYTE_MAX -> {
                bytes[out++] = (LEAD_2 or (code shr SIX)).toByte()
                bytes[out++] = (CONTINUATION or (code and SIX_BITS)).toByte()
            }

            paired -> {
                val point = SUPPLEMENTARY_BASE +
                    ((code - Char.MIN_HIGH_SURROGATE.code) shl TEN) +
                    (chars[index + 1].code - Char.MIN_LOW_SURROGATE.code)
                bytes[out++] = (LEAD_4 or (point shr EIGHTEEN)).toByte()
                bytes[out++] = (CONTINUATION or ((point shr TWELVE) and SIX_BITS)).toByte()
                bytes[out++] = (CONTINUATION or ((point shr SIX) and SIX_BITS)).toByte()
                bytes[out++] = (CONTINUATION or (point and SIX_BITS)).toByte()
                index++
            }

            char.isSurrogate() -> bytes[out++] = REPLACEMENT

            else -> {
                bytes[out++] = (LEAD_3 or (code shr TWELVE)).toByte()
                bytes[out++] = (CONTINUATION or ((code shr SIX) and SIX_BITS)).toByte()
                bytes[out++] = (CONTINUATION or (code and SIX_BITS)).toByte()
            }
        }
        index++
    }
    return bytes
}

private fun utf8Size(chars: CharArray): Int {
    var size = 0
    var index = 0
    while (index < chars.size) {
        val char = chars[index]
        val code = char.code
        size += when {
            code <= ASCII_MAX -> 1

            code <= TWO_BYTE_MAX -> 2

            char.isHighSurrogate() && index + 1 < chars.size && chars[index + 1].isLowSurrogate() -> {
                index++
                FOUR_BYTES
            }

            char.isSurrogate() -> 1

            else -> THREE_BYTES
        }
        index++
    }
    return size
}
