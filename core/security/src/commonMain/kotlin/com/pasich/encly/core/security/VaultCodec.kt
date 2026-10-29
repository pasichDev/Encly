package com.pasich.encly.core.security

import kotlinx.io.IOException

/** Big-endian integers, as `DataOutputStream` and `ByteBuffer` write them. */
internal object BigEndian {
    private const val BYTE_MASK = 0xFF

    fun bytesOf(value: Int): ByteArray = ByteArray(Int.SIZE_BYTES).also { write(it, 0, value) }

    fun bytesOf(value: Long): ByteArray = ByteArray(Long.SIZE_BYTES).also {
        for (i in it.indices) it[i] = (value ushr (Byte.SIZE_BITS * (Long.SIZE_BYTES - 1 - i))).toByte()
    }

    /** Writes [value] at [at] in [into]; returns the position after it. */
    fun write(into: ByteArray, at: Int, value: Int): Int {
        for (i in 0 until Int.SIZE_BYTES) {
            into[at + i] = (value ushr (Byte.SIZE_BITS * (Int.SIZE_BYTES - 1 - i))).toByte()
        }
        return at + Int.SIZE_BYTES
    }

    fun readInt(bytes: ByteArray, at: Int = 0): Int {
        var value = 0
        for (i in 0 until Int.SIZE_BYTES) value = (value shl Byte.SIZE_BITS) or (bytes[at + i].toInt() and BYTE_MASK)
        return value
    }

    fun readLong(bytes: ByteArray): Long {
        var value = 0L
        for (i in 0 until Long.SIZE_BYTES) {
            value = (value shl Byte.SIZE_BITS) or (bytes[i].toLong() and BYTE_MASK.toLong())
        }
        return value
    }
}

/**
 * Java's "modified UTF-8", the encoding of `DataOutputStream.writeUTF` keys: NUL is two bytes
 * and a character outside the BMP is its two surrogates, three bytes each. The vault file keeps
 * this form so every file an earlier build wrote still reads back.
 */
internal object ModifiedUtf8 {
    private const val ONE_BYTE_MAX = 0x7F
    private const val TWO_BYTE_MAX = 0x7FF
    private const val SIX_BITS = 0x3F
    private const val FIVE_BITS = 0x1F
    private const val FOUR_BITS = 0x0F
    private const val SIX = 6
    private const val TWELVE = 12
    private const val LEAD_2 = 0xC0
    private const val LEAD_3 = 0xE0
    private const val CONTINUATION = 0x80
    private const val CONTINUATION_MASK = 0xC0
    private const val LEAD_MASK_2 = 0xE0
    private const val LEAD_MASK_3 = 0xF0
    private const val BYTE_MASK = 0xFF
    private const val MAX_LENGTH = 0xFFFF
    private const val THREE_BYTES = 3

    fun encode(text: String): ByteArray {
        val out = ArrayList<Byte>(text.length)
        for (char in text) {
            val code = char.code
            when {
                code in 1..ONE_BYTE_MAX -> out.add(code.toByte())

                code <= TWO_BYTE_MAX -> {
                    out.add((LEAD_2 or (code shr SIX)).toByte())
                    out.add((CONTINUATION or (code and SIX_BITS)).toByte())
                }

                else -> {
                    out.add((LEAD_3 or (code shr TWELVE)).toByte())
                    out.add((CONTINUATION or ((code shr SIX) and SIX_BITS)).toByte())
                    out.add((CONTINUATION or (code and SIX_BITS)).toByte())
                }
            }
        }
        if (out.size > MAX_LENGTH) throw IOException("key too long")
        return out.toByteArray()
    }

    @Suppress("ThrowsCount") // Each malformed form is its own failure.
    fun decode(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size)
        var at = 0
        while (at < bytes.size) {
            val first = bytes[at].toInt() and BYTE_MASK
            when {
                first <= ONE_BYTE_MAX -> {
                    out.append(first.toChar())
                    at++
                }

                first and LEAD_MASK_2 == LEAD_2 -> {
                    if (at + 1 >= bytes.size) throw IOException("malformed key")
                    val second = bytes[at + 1].toInt() and BYTE_MASK
                    if (second and CONTINUATION_MASK != CONTINUATION) throw IOException("malformed key")
                    out.append((((first and FIVE_BITS) shl SIX) or (second and SIX_BITS)).toChar())
                    at += 2
                }

                first and LEAD_MASK_3 == LEAD_3 -> {
                    if (at + 2 >= bytes.size) throw IOException("malformed key")
                    val second = bytes[at + 1].toInt() and BYTE_MASK
                    val third = bytes[at + 2].toInt() and BYTE_MASK
                    if (second and CONTINUATION_MASK != CONTINUATION || third and CONTINUATION_MASK != CONTINUATION) {
                        throw IOException("malformed key")
                    }
                    out.append(
                        (((first and FOUR_BITS) shl TWELVE) or ((second and SIX_BITS) shl SIX) or (third and SIX_BITS))
                            .toChar(),
                    )
                    at += THREE_BYTES
                }

                else -> throw IOException("malformed key")
            }
        }
        return out.toString()
    }
}
