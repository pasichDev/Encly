package com.pasich.encly.core.security.wrapper

object IntWrapper {

    /**
     * Wraps an Int by appending a random numeric "salt"
     * and encoding its length in the last digit.
     *
     * Format: [target][salt][@saltLength]
     */
    fun wrap(target: Int): Int {
        val saltLength = (2..4).random()
        val salt = (1..saltLength).map { (0..9).random() }.joinToString("")
        return "$target$salt$saltLength".toInt()
    }

    /**
     * Unwraps a wrapped Int and returns the original number (target).
     *
     * @return the original Int, or 0 if it could not be parsed.
     */
    fun unwrap(wrapped: Int): Int {
        val wrappedStr = wrapped.toString()
        return try {
            if (wrappedStr.length < 3) return 0

            val saltLengthChar = wrappedStr.last()
            val saltLength = saltLengthChar.toString().toInt()
            val targetEnd = wrappedStr.length - saltLength - 1

            if (targetEnd <= 0) return 0

            wrappedStr.substring(0, targetEnd).toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
