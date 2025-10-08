package com.pasich.encly.core.security.wrapper

object StringWrapper {

    private const val PREFIX = "W" // формальний префікс для розпізнавання формату
    private const val SALT_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    // Генерація випадкової солі певної довжини
    private fun generateSalt(length: Int): String {
        return (1..length).map { SALT_CHARS.random() }.joinToString("")
    }

    // Функція обгортки ключа
    fun wrapMasterKey(masterKey: String): String {
        val saltLength = (4..8).random() // Випадкове число від 4 до 8
        val salt = generateSalt(saltLength)
        return PREFIX + saltLength + salt + masterKey
    }

    // Функція розгортки ключа
    fun unwrapMasterKey(wrappedKey: String): String? {
        if (!wrappedKey.startsWith(PREFIX)) return null
        return try {
            val lengthChar = wrappedKey[PREFIX.length].toString().toInt()
            val prefixAndSaltLength = PREFIX.length + 1 + lengthChar
            wrappedKey.substring(prefixAndSaltLength)
        } catch (_: Exception) {
            null
        }
    }
}