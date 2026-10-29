package com.pasich.encly.editor.ui.blocks

/**
 * Host names in their ASCII form (RFC 3492 Punycode, the `xn--` labels of IDNA), for the platforms
 * without a system call for it (iOS). The host arrives lower-cased, so there is no case folding or
 * Unicode normalization here; a label that cannot be encoded or is longer than DNS allows makes the
 * whole name invalid.
 */
internal object Punycode {
    private const val BASE = 36
    private const val T_MIN = 1
    private const val T_MAX = 26
    private const val SKEW = 38
    private const val DAMP = 700
    private const val INITIAL_BIAS = 72
    private const val INITIAL_N = 128
    private const val DELIMITER = '-'
    private const val PREFIX = "xn--"
    private const val ASCII_END = 0x80
    private const val MAX_LABEL = 63
    private const val SUPPLEMENTARY_START = 0x10000
    private const val SURROGATE_BITS = 10
    private const val LETTERS = 26

    /** [host] with every label that has a non-ASCII character encoded; null when it is not a valid name. */
    @Suppress("ReturnCount") // Every invalid label ends the name at once.
    fun toAsciiHost(host: String): String? {
        val labels = host.split('.')
        val encoded = ArrayList<String>(labels.size)
        for ((index, label) in labels.withIndex()) {
            // Only the root dot at the very end may leave a label empty ("example.com.").
            if (label.isEmpty()) {
                if (index == labels.lastIndex && index > 0) encoded += "" else return null
                continue
            }
            // An encoded label is never shorter than the label, so a long one is invalid before it is encoded
            // (which also keeps the arithmetic of the encoder far from overflowing).
            if (label.length > MAX_LABEL) return null
            val ascii = if (label.all { it.code < ASCII_END }) label else PREFIX + encode(label)
            if (ascii.length > MAX_LABEL) return null
            encoded += ascii
        }
        return encoded.joinToString(".")
    }

    /** The Punycode of [label] (RFC 3492 section 6.3), without the `xn--` prefix. */
    private fun encode(label: String): String {
        val input = codePoints(label)
        val output = StringBuilder()
        input.filter { it < ASCII_END }.forEach { output.append(it.toChar()) }
        val basic = output.length
        var handled = basic
        if (basic > 0) output.append(DELIMITER)
        var n = INITIAL_N
        var delta = 0
        var bias = INITIAL_BIAS
        while (handled < input.size) {
            val next = input.filter { it >= n }.min()
            delta += (next - n) * (handled + 1)
            n = next
            for (c in input) {
                if (c < n) delta++
                if (c == n) {
                    appendDelta(output, delta, bias)
                    bias = adapt(delta, handled + 1, handled == basic)
                    delta = 0
                    handled++
                }
            }
            delta++
            n++
        }
        return output.toString()
    }

    /** [delta] as a generalized variable-length integer in base 36. */
    private fun appendDelta(output: StringBuilder, delta: Int, bias: Int) {
        var q = delta
        var k = BASE
        while (true) {
            val t = (k - bias).coerceIn(T_MIN, T_MAX)
            if (q < t) break
            output.append(digit(t + (q - t) % (BASE - t)))
            q = (q - t) / (BASE - t)
            k += BASE
        }
        output.append(digit(q))
    }

    private fun adapt(delta: Int, points: Int, first: Boolean): Int {
        var d = if (first) delta / DAMP else delta / 2
        d += d / points
        var k = 0
        while (d > ((BASE - T_MIN) * T_MAX) / 2) {
            d /= BASE - T_MIN
            k += BASE
        }
        return k + (BASE - T_MIN + 1) * d / (d + SKEW)
    }

    /** 0..25 are a..z, 26..35 are 0..9. */
    private fun digit(value: Int): Char = if (value < LETTERS) 'a' + value else '0' + (value - LETTERS)

    private fun codePoints(text: String): List<Int> {
        val points = ArrayList<Int>(text.length)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val low = text.getOrNull(index + 1)
            if (char.isHighSurrogate() && low != null && low.isLowSurrogate()) {
                points += SUPPLEMENTARY_START + ((char - Char.MIN_HIGH_SURROGATE) shl SURROGATE_BITS) +
                    (low - Char.MIN_LOW_SURROGATE)
                index += 2
            } else {
                points += char.code
                index++
            }
        }
        return points
    }
}
