package com.pasich.encly.core.security.wrapper

object StringWrapper {

    private const val PREFIX = "W" // formal prefix for recognizing the format
    private const val SALT_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    // Generate a random salt of a given length
    private fun generateSalt(length: Int): String {
        return (1..length).map { SALT_CHARS.random() }.joinToString("")
    }

    // Key-wrapping function
    fun wrapMasterKey(masterKey: String): String {
        val saltLength = (4..8).random() // Random number from 4 to 8
        val salt = generateSalt(saltLength)
        return PREFIX + saltLength + salt + masterKey
    }

    // Key-unwrapping function
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