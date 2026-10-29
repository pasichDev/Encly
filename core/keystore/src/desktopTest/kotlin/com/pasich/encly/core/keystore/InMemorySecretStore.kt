package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.PinKeySlot
import java.security.SecureRandom

/** A [SecretStore] in memory, with failures to inject and a record of every array it handed out. */
internal class InMemorySecretStore : SecretStore {
    private val secrets = mutableMapOf<PinKeySlot, ByteArray>()

    /** Every array [read] and [create] returned, so that a test can check that the caller wiped it. */
    val handedOut = mutableListOf<ByteArray>()

    var readFailure: SecretStoreException? = null
    var createFailure: SecretStoreException? = null
    var deleteFailure: SecretStoreException? = null

    fun put(slot: PinKeySlot, secret: ByteArray) {
        secrets[slot] = secret
    }

    /** The stored secret itself, without counting as handed out. */
    fun peek(slot: PinKeySlot): ByteArray? = secrets[slot]

    override fun read(slot: PinKeySlot): ByteArray? {
        readFailure?.let { throw it }
        return secrets[slot]?.copyOf()?.also { handedOut += it }
    }

    override fun create(slot: PinKeySlot): ByteArray {
        createFailure?.let { throw it }
        val secret = ByteArray(SecretStore.SECRET_SIZE).also(SecureRandom()::nextBytes)
        secrets[slot] = secret
        return secret.copyOf().also { handedOut += it }
    }

    override fun delete(slot: PinKeySlot) {
        deleteFailure?.let { throw it }
        secrets.remove(slot)
    }
}
