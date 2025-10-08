package com.pasich.encly.core.security.wrapper

object IntWrapper {

    /**
     * Обгортає Int шляхом додавання випадкової числової "солі"
     * та вказання її довжини в останній цифрі.
     *
     * Формат: [target][salt][@saltLength]
     */
    fun wrap(target: Int): Int {
        val saltLength = (2..4).random()
        val salt = (1..saltLength).map { (0..9).random() }.joinToString("")
        return "$target$salt$saltLength".toInt()
    }

    /**
     * Розгортає обгорнутий Int і повертає оригінальне число (target).
     *
     * @return оригінальний Int або 0, якщо не вдалося розпарсити
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
