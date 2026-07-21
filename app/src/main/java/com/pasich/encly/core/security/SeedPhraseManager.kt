package com.pasich.encly.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount
import com.pasich.encly.core.security.SecurityConstants.ENCRYPTED_BLOCK_KEY
import com.pasich.encly.core.security.SecurityConstants.ENCRYPTED_BLOCK_KEY_TWO
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

enum class SaltData {
    DATABASE, AUTH
}

object SecurityConstants {
    const val ENCRYPTED_BLOCK_KEY = "encrypted_seed_block"

    // Decoy for verifying whether the user created the key themselves: if ENCRYPTED_BLOCK_KEY == ENCRYPTED_BLOCK_KEY_TWO then it was set up manually
    const val ENCRYPTED_BLOCK_KEY_TWO = "encrypted_seed_block_two"
}

/**
 * Manages the BIP39 seed phrase that anchors all data encryption.
 *
 * The seed's SHA-256 hash is sealed with an AndroidKeyStore AES-GCM key and stored
 * in SharedPreferences. Per-type data keys are derived from that hash via HKDF-SHA256
 * using a per-install random salt. Losing the seed makes the data unrecoverable by
 * design (zero-knowledge model).
 */
@Singleton
class SeedPhraseManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val hmacIntegrityManager: HmacIntegrityManager
) {
    companion object {
        const val PREF_NAME = "security_prefs"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "seed_master_key"

        const val GCM_TAG_LENGTH = 128
        const val IV_LENGTH = 12

        /** SharedPreferences key for the per-install random HKDF salt. */
        const val KDF_SALT_KEY = "kdf_salt_v2"
    }


    private val prefs by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val keyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    private fun generateKeyIfMissing() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).apply {
                setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                setKeySize(256)
            }.build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        generateKeyIfMissing()
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /** Generates a new 12-word seed phrase. */
    fun generateMnemonic(): MnemonicCode = MnemonicCode(WordCount.COUNT_12)

    /** Validates the phrase (throws an exception if invalid). */
    fun isValidMnemonic(phrase: CharArray): Boolean = try {
        MnemonicCode(phrase).validate()
        true
    } catch (_: Exception) {
        false
    }

    /** Computes the SHA-256 hash of the phrase. */
    fun hashMnemonic(phrase: CharArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(String(phrase).toByteArray(Charsets.UTF_8))
    }

    /** Encrypts the hash + IV together and stores them in SharedPreferences. */
    fun storeSeedHash(phrase: CharArray, typeKey: String): Boolean {
        val hash = hashMnemonic(phrase)

        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(hash)

            // Combine IV + encrypted hash
            val combined = ByteArray(iv.size + encrypted.size).apply {
                System.arraycopy(iv, 0, this, 0, iv.size)
                System.arraycopy(encrypted, 0, this, iv.size, encrypted.size)
            }

            val base64 = Base64.encodeToString(combined, Base64.NO_WRAP)

            prefs.edit {
                putString(typeKey, base64)
            }
            if (typeKey == ENCRYPTED_BLOCK_KEY) {
                // Store the HMAC for future verification
                hmacIntegrityManager.storeHmac(hash)
            }
            return true
        } catch (_: Exception) {
            return false
        } finally {
            // Do NOT clear `phrase` — the caller may reuse it (saveKeysStore calls this twice).
            SensitiveDataCleaner.clear(hash)
        }
    }

    /** 🧠 Verifies the phrase by decrypting the block from SharedPreferences. */
    fun verifyMnemonic(phrase: CharArray): Boolean {
        val base64 = prefs.getString(ENCRYPTED_BLOCK_KEY, null) ?: return false
        return try {
            val combined = Base64.decode(base64, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH) return false

            val iv = combined.copyOfRange(0, IV_LENGTH)
            val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decryptedHash = cipher.doFinal(encrypted)

            val inputHash = hashMnemonic(phrase)
            val matches = inputHash.contentEquals(decryptedHash)
            SensitiveDataCleaner.clear(decryptedHash)
            SensitiveDataCleaner.clear(inputHash)
            matches
        } catch (_: Exception) {
            false
        } finally {
            SensitiveDataCleaner.clear(phrase)
        }
    }


    /**
     * Returns an AES-256 key for the given data type, derived from the seed-phrase
     * hash via HKDF-SHA256 (RFC 5869) with a per-install random salt.
     *
     * The IKM (seed hash) already carries high entropy (a 12-word BIP39 phrase ≈ 128
     * bits), so HKDF is a better and faster fit here than PBKDF2/Argon2. Keys for
     * different data types are separated by `info = saltType.name`.
     *
     * NOTE: changing this derivation makes data encrypted with the old scheme unreadable.
     *
     * @throws IllegalStateException if the seed hash is missing or cannot be decrypted
     */
    fun getEncryptionKeyForData(saltType: SaltData): SecretKey {
        val ikm = decryptSeedHash(ENCRYPTED_BLOCK_KEY)
            ?: throw IllegalStateException("Cannot decrypt seed hash")
        val salt = getOrCreateKdfSalt()
        val info = saltType.name.toByteArray(Charsets.UTF_8)

        val okm = hkdfSha256(ikm = ikm, salt = salt, info = info, length = 32)
        SensitiveDataCleaner.clear(ikm)

        val key = SecretKeySpec(okm, "AES")
        SensitiveDataCleaner.clear(okm)
        return key
    }

    /** Returns (creating on first use) the 16-byte per-install random HKDF salt. */
    private fun getOrCreateKdfSalt(): ByteArray {
        prefs.getString(KDF_SALT_KEY, null)?.let {
            return Base64.decode(it, Base64.NO_WRAP)
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit { putString(KDF_SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP)) }
        return salt
    }

    /**
     * HKDF-SHA256 (RFC 5869). A single expand round suffices for length ≤ 32,
     * since an HMAC-SHA256 block is 32 bytes.
     */
    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..32) { "This HKDF impl supports 1..32 output bytes" }
        val mac = Mac.getInstance("HmacSHA256")
        // Extract: PRK = HMAC(salt, IKM)
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        // Expand: T(1) = HMAC(PRK, info || 0x01)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(0x01.toByte())
        val okm = mac.doFinal().copyOf(length)
        SensitiveDataCleaner.clear(prk)
        return okm
    }


    /** Whether the seed-phrase hash is stored. */
    fun hasStoredSeed(): Boolean = prefs.contains(ENCRYPTED_BLOCK_KEY)


    /**
     * Verifies the stored key using HMAC.
     * Used to protect against corruption/tampering of the app data.
     */
    fun verificationKeyData(): Boolean {
        val hash = decryptSeedHash(ENCRYPTED_BLOCK_KEY) ?: return false
        return !hmacIntegrityManager.isHashTampered(hash)
    }

    /**
     * Full wipe for unrecoverable-loss reset: clears all seed prefs (hashes, KDF salt),
     * deletes the Keystore master key, and wipes the integrity HMAC. After this the app
     * starts fresh from onboarding.
     */
    fun wipe() {
        prefs.edit { clear() }
        try {
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
        hmacIntegrityManager.wipe()
    }


    /**
     * Tells whether the seed phrase was created by the user (USER_MANAGED).
     *
     * Onboarding stores both blocks equal for user-managed (`saveKeysStore(target, target)`)
     * and two different random phrases for auto-managed (`saveKeysStore()`).
     * Hence: equal blocks ⇒ user-created (true), different ⇒ auto-generated (false).
     */
    fun isUserManuallyCreatedKeyByDecryption(): Boolean {
        val block = decryptSeedHash(ENCRYPTED_BLOCK_KEY) ?: return false
        val blockTwo = decryptSeedHash(ENCRYPTED_BLOCK_KEY_TWO) ?: return false

        return block.contentEquals(blockTwo)
    }

    private fun decryptSeedHash(key: String): ByteArray? {
        val base64 = prefs.getString(key, null) ?: return null
        val combined = Base64.decode(base64, Base64.NO_WRAP)
        if (combined.size < IV_LENGTH) return null

        val iv = combined.copyOfRange(0, IV_LENGTH)
        val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            cipher.doFinal(encrypted)
        } catch (_: Exception) {
            null
        }
    }

}
