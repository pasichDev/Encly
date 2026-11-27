package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.KeyStoreProblem
import com.pasich.encly.core.security.PinFactorException
import com.pasich.encly.core.security.PinHardwareFactor
import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.core.security.SensitiveDataCleaner
import java.nio.file.Path
import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * [PinHardwareFactor] for the desktop. The contract is the one of the Android Keystore factor:
 * `hw = HMAC-SHA256(deviceSecret, data)`, where the caller passes `data = salt ‖ pin`.
 *
 * The `deviceSecret` is 32 random bytes from SecureRandom, created once per OS user and slot (on Linux also per
 * data directory) and kept ONLY in the operating system's secret store of that user (Windows DPAPI, the macOS
 * login Keychain, the freedesktop Secret Service); it never touches Encly's own files unprotected. Unlike
 * Android StrongBox or TEE the secret is not hardware-bound: it is as safe as the OS user account (see
 * SECURITY.md).
 *
 * States, mirroring the Android factor:
 * - [ensureKey] creates the secret when there is none and returns true then. A secret that is there but
 *   unreadable counts as existing (like an invalidated Android key): [mac] reports it as lost, and
 *   [reset] replaces it.
 * - [mac] with no secret in the store, or one of the wrong size, fails with `PinFactorException(lost = true)`; where
 *   a missing secret does not prove it is gone ([SecretStore.missingMeansLost] is false: the Linux Secret Service,
 *   which may be another provider or a closed keyring) the failure is `notFound = true` instead, with `lost = false`.
 *   Any other failure of the store (locked, not running, I/O) is `lost = false` and may be transient. A store that
 *   is locked or not running (a [SecretStoreUnavailableException] cause) also sets `unavailable = true`, and
 *   `problem` says which.
 * - [delete] never throws.
 *
 * Every copy of the secret that this class controls is wiped after use. The JCA `Mac` and the native
 * calls make copies of their own that no code here can reach.
 */
public class DesktopPinFactor internal constructor(private val store: SecretStore) : PinHardwareFactor {
    @Suppress("ReturnCount") // Exists, unreadable (also exists), or created.
    @Throws(PinFactorException::class)
    override fun ensureKey(slot: PinKeySlot): Boolean {
        val existing = try {
            store.read(slot)
        } catch (e: SecretStoreException) {
            // Like an invalidated Android key: it is there, so it is not replaced here. [mac] reports it as
            // lost and the caller decides (a PIN change resets it; an unlock goes to the recovery phrase).
            if (e.lost) return false
            throw e.toFactorException()
        }
        if (existing != null) {
            SensitiveDataCleaner.clear(existing)
            return false
        }
        reset(slot)
        return true
    }

    @Throws(PinFactorException::class)
    override fun reset(slot: PinKeySlot) {
        try {
            try {
                store.delete(slot)
            } catch (_: SecretStoreException) {
                // An item that cannot be deleted is replaced by [SecretStore.create] (which replaces by contract): the
                // new vault must never keep the secret of an erased one. A store that is really down fails there.
            }
            SensitiveDataCleaner.clear(store.create(slot))
        } catch (e: SecretStoreException) {
            throw e.toFactorException(lost = false)
        }
    }

    @Throws(PinFactorException::class)
    override fun mac(slot: PinKeySlot, data: ByteArray): ByteArray {
        val secret = try {
            store.read(slot)
        } catch (e: SecretStoreException) {
            throw e.toFactorException()
        } ?: throw PinFactorException(lost = store.missingMeansLost, notFound = !store.missingMeansLost)
        try {
            if (secret.size != SecretStore.SECRET_SIZE) throw PinFactorException(lost = true)
            return hmacSha256(secret, data)
        } finally {
            SensitiveDataCleaner.clear(secret)
        }
    }

    override fun delete(slot: PinKeySlot) {
        try {
            store.delete(slot)
        } catch (_: SecretStoreException) {
            // Documented: delete never throws.
        }
    }

    override val missingKeyMayReturn: Boolean get() = !store.missingMeansLost

    override fun prepare() {
        try {
            store.prepare()
        } catch (e: SecretStoreException) {
            throw e.toFactorException(lost = false)
        }
    }

    override fun abandonPending(): Unit = store.abandonPending()

    /**
     * A store that is not usable right now (a locked or missing Secret Service) is reported as `unavailable`, so
     * the screen can ask the user to unlock the keyring instead of showing a generic failure.
     */
    private fun SecretStoreException.toFactorException(lost: Boolean = this.lost) = PinFactorException(
        lost = lost,
        cause = this,
        unavailable = !lost && cause is SecretStoreUnavailableException,
        problem = if (!lost && cause is SecretStoreUnavailableException) problem ?: KeyStoreProblem.OTHER else null,
    )

    private fun hmacSha256(secret: ByteArray, data: ByteArray): ByteArray = try {
        Mac.getInstance(HMAC_ALGORITHM).run {
            init(SecretKeySpec(secret, HMAC_ALGORITHM))
            doFinal(data)
        }
    } catch (e: GeneralSecurityException) {
        throw PinFactorException(lost = false, cause = e)
    }

    public companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"

        /**
         * The factor for the operating system this JVM runs on. Throws [SecretStoreUnavailableException] when
         * the system has no supported secret store. On Linux, a missing Secret Service (a headless session) shows
         * up later, on the first operation, as a [PinFactorException] with a [SecretStoreUnavailableException]
         * cause: it can start later, so it is not a permanent state. [dataDirectory] is where this install keeps
         * its vault; on Linux it scopes the secret, so two installs of one user keep their own (see
         * [LinuxSecretServiceStore]).
         */
        @Throws(SecretStoreUnavailableException::class)
        public fun create(dataDirectory: Path): DesktopPinFactor = DesktopPinFactor(
            selectSecretStore(
                osName = System.getProperty("os.name").orEmpty(),
                windows = { WindowsDpapiSecretStore(directory = dataDirectory) },
                macos = { MacKeychainSecretStore() },
                linux = { LinuxSecretServiceStore(vault = dataDirectory.toAbsolutePath().normalize().toString()) },
            ),
        )
    }
}
