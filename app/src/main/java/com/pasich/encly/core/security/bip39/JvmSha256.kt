package com.pasich.encly.core.security.bip39

import java.security.MessageDigest

/** The only JVM-specific line of the BIP-39 code; swap it for a common SHA-256 when there is one. */
internal object JvmSha256 : Sha256 {
    override fun digest(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)
}
