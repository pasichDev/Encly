package com.pasich.encly.core.keystore

import com.pasich.encly.core.security.PinKeySlot
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinCrypt
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom

/**
 * Windows: the secret is encrypted with DPAPI (`CryptProtectData`, user scope, no UI) and the blob is kept in
 * `%APPDATA%\Encly\`. Only the same Windows user (on the same machine, or a roaming profile with the same
 * credentials) can unprotect it, so the file is useless to anyone who copies it away.
 *
 * The blob is bound to an Encly-specific entropy as well, so another program of the same user cannot unprotect
 * it by calling `CryptUnprotectData` without knowing it. That is defence against a careless program, not against
 * malware running as the user, which DPAPI cannot stop.
 *
 * A blob that DPAPI rejects as damaged or as sealed under a key state this user does not have (see
 * LOST_ERRORS) is reported as lost; any other DPAPI failure and every I/O failure is not.
 */
@Suppress("TooGenericExceptionCaught") // JNA reports a failed native call as a RuntimeException (Win32Exception).
internal class WindowsDpapiSecretStore(
    private val directory: Path = defaultDirectory(),
    private val random: SecureRandom = SecureRandom(),
) : SecretStore {
    override fun read(slot: PinKeySlot): ByteArray? {
        val file = fileOf(slot)
        val blob = try {
            if (!Files.exists(file)) return null
            Files.readAllBytes(file)
        } catch (e: IOException) {
            throw SecretStoreException(lost = false, message = "The DPAPI blob could not be read", cause = e)
        }
        try {
            return Crypt32Util.cryptUnprotectData(blob, ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null)
        } catch (e: Win32Exception) {
            // Only the codes that say the data or its key state is bad are final; anything else may pass.
            throw SecretStoreException(
                lost = isFinal(e),
                message = "The DPAPI blob does not unprotect (error ${e.errorCode})",
                cause = e,
            )
        }
    }

    override fun create(slot: PinKeySlot): ByteArray {
        val secret = ByteArray(SecretStore.SECRET_SIZE).also(random::nextBytes)
        try {
            val blob = try {
                Crypt32Util.cryptProtectData(
                    secret,
                    ENTROPY,
                    WinCrypt.CRYPTPROTECT_UI_FORBIDDEN,
                    DESCRIPTION,
                    null,
                )
            } catch (e: Win32Exception) {
                throw SecretStoreException(lost = false, message = "DPAPI could not protect the secret", cause = e)
            }
            write(fileOf(slot), blob)
            return secret
        } catch (e: Exception) {
            SensitiveDataCleaner.clear(secret)
            throw e
        }
    }

    override fun delete(slot: PinKeySlot) {
        try {
            Files.deleteIfExists(fileOf(slot))
        } catch (e: IOException) {
            throw SecretStoreException(lost = false, message = "The DPAPI blob could not be deleted", cause = e)
        }
    }

    private fun isFinal(e: Win32Exception): Boolean = e.errorCode in LOST_ERRORS || e.hr.toInt() in LOST_ERRORS

    private fun fileOf(slot: PinKeySlot): Path = directory.resolve(slot.entryName() + FILE_EXTENSION)

    /** Writes next to the target and renames over it, so a crash leaves the old blob or the new one, never half. */
    private fun write(target: Path, blob: ByteArray) {
        val temp = target.resolveSibling(target.fileName.toString() + TEMP_EXTENSION)
        try {
            Files.createDirectories(directory)
            Files.newByteChannel(
                temp,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { channel ->
                channel.write(ByteBuffer.wrap(blob))
                (channel as? FileChannel)?.force(true)
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            runCatching { Files.deleteIfExists(temp) }
            throw SecretStoreException(lost = false, message = "The DPAPI blob could not be written", cause = e)
        }
    }

    private companion object {
        const val FILE_EXTENSION = ".dpapi"
        const val TEMP_EXTENSION = ".tmp"
        const val DESCRIPTION = "Encly PIN factor"

        /** ERROR_INVALID_DATA, NTE_BAD_DATA, NTE_BAD_KEY, NTE_BAD_KEY_STATE. */
        val LOST_ERRORS = setOf(13, 0x80090005.toInt(), 0x80090003.toInt(), 0x8009000B.toInt())

        /** Not a secret: it scopes the blob to Encly. */
        val ENTROPY: ByteArray = "$APPLICATION_ID/pin-factor-v1".toByteArray(Charsets.UTF_8)

        fun defaultDirectory(): Path {
            val appData = System.getenv("APPDATA")
            val base = if (!appData.isNullOrBlank()) {
                Paths.get(appData)
            } else {
                Paths.get(System.getProperty("user.home"), "AppData", "Roaming")
            }
            return base.resolve("Encly")
        }
    }
}
