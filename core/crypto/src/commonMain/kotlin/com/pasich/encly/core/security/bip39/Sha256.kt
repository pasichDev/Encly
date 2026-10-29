package com.pasich.encly.core.security.bip39

/** The one primitive BIP-39 needs from the platform: the checksum is the start of a SHA-256. */
internal fun interface Sha256 {
    fun digest(input: ByteArray): ByteArray
}
