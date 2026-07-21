package com.pasich.encly.core.security

import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.pasich.encly.core.security.wrapper.IntWrapper
import com.pasich.encly.core.security.wrapper.StringWrapper
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

enum class AuthType {
    NONE, PIN, SEED_PHRASE
}

enum class AuthStrategy {
    NONE, PIN, PIN_BIOMETRIC, SEED_PHRASE, SEED_PHRASE_BIOMETRIC, RECOVERY_DATA
}

/**
 * Handles local PIN and biometric authentication settings.
 *
 * The PIN is stored as a PBKDF2-HMAC-SHA256 hash with a per-PIN random salt, then
 * encrypted under the seed-derived AUTH key. Repeated failures trigger a progressive
 * lockout. This class governs the auth strategy ([AuthStrategy]); it does not by
 * itself release any data-encryption key.
 */
@Singleton
class AuthenticationManager @Inject constructor(
    private val secureStoragePrefs: SharedPreferences,
    private val seedPhraseManager: SeedPhraseManager
) {
    companion object {
        private const val PIN_CODE_KEY = "code_auth"
        private const val AUTH_TYPE_KEY = "auth_type"
        private const val BIOMETRIC_ENABLED_KEY = "biometric_enabled"
        private const val AES_MODE = "AES/CBC/PKCS7Padding"
        private const val IV_SIZE = 16 // 128 bits

        // PIN hardening
        private const val PIN_ATTEMPTS_KEY = "pin_attempts"
        private const val PIN_LOCKOUT_UNTIL_KEY = "pin_lockout_until"
        private const val MAX_ATTEMPTS = 5
        private const val PIN_SALT_SIZE = 16
        private const val PBKDF2_ITERATIONS = 600_000
    }

    /**
     * Enables PIN authentication by storing the PIN hash in secure storage.
     *
     * @param code The PIN code entered by the user, as a string.
     */
    fun activatePinAuth(code: String): Boolean {
        if (getAuthType() == AuthType.PIN) {
            return false
        }

        val pinChars = code.toCharArray()
        return try {
            // Per-PIN random salt + slow KDF (PBKDF2-HMAC-SHA256, 600k) instead of
            // unsalted SHA-256. Format: base64(salt):base64(hash).
            val salt = ByteArray(PIN_SALT_SIZE).also { SecureRandom().nextBytes(it) }
            val hash = derivePinHash(pinChars, salt)
            val stored = Base64.encodeToString(salt, Base64.NO_WRAP) + ":" +
                    Base64.encodeToString(hash, Base64.NO_WRAP)
            SensitiveDataCleaner.clear(hash)

            // Wrap + encrypt with the seed-derived AES key
            val encryptedPin = encryptData(StringWrapper.wrapMasterKey(stored))

            secureStoragePrefs.edit {
                putString(PIN_CODE_KEY, encryptedPin)
                putInt(AUTH_TYPE_KEY, IntWrapper.wrap(AuthType.PIN.ordinal))
                remove(PIN_ATTEMPTS_KEY)
                remove(PIN_LOCKOUT_UNTIL_KEY)
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            SensitiveDataCleaner.clear(pinChars)
        }
    }

    /**
     * Verifies the entered PIN by comparing its hash against the stored one.
     *
     * @param inputCode The PIN code entered by the user to verify.
     * @return true if the PIN codes match, false otherwise.
     */
    fun verifyPinAuth(inputCode: String): Boolean {
        // Rate-limit: while locked out, do not even check
        if (remainingLockoutMillis() > 0) return false

        val encryptedPin = secureStoragePrefs.getString(PIN_CODE_KEY, null) ?: return false
        val decryptedPinWrapped = decryptData(encryptedPin) ?: return false
        val stored = StringWrapper.unwrapMasterKey(decryptedPinWrapped) ?: return false

        val parts = stored.split(":")
        if (parts.size != 2) return false

        val pinChars = inputCode.toCharArray()
        return try {
            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
            val storedHash = Base64.decode(parts[1], Base64.NO_WRAP)
            val inputHash = derivePinHash(pinChars, salt)
            // Constant-time comparison
            val matches = MessageDigest.isEqual(storedHash, inputHash)
            SensitiveDataCleaner.clear(inputHash)
            if (matches) resetAttempts() else recordFailedAttempt()
            matches
        } catch (_: Exception) {
            false
        } finally {
            SensitiveDataCleaner.clear(pinChars)
        }
    }

    /**
     * Cancels (removes) PIN authentication if the entered code is correct.
     * Checks whether the entered PIN matches the stored one.
     * If so, removes the stored PIN and changes the auth type to NONE.
     *
     * @param inputCode The PIN code entered by the user to verify.
     * @return true if the PIN is correct and authentication was cancelled, false otherwise.
     */
    fun cancelPinAuth(inputCode: String): Boolean {
        if (verifyPinAuth(inputCode)) {
            secureStoragePrefs.edit {
                remove(PIN_CODE_KEY)
                putBoolean(BIOMETRIC_ENABLED_KEY, false)
                putInt(AUTH_TYPE_KEY, IntWrapper.wrap(AuthType.NONE.ordinal))
            }
            return true
        }
        return false
    }

    /**
     * Determines the user's active authorization strategy (AuthStrategy),
     * based on the stored auth type (AuthType) and the biometric state.
     *
     * @return AuthStrategy describing exactly how the user must authenticate:
     * - NONE — no authentication configured
     * - PIN — PIN code only
     * - PIN_BIOMETRIC — PIN code + biometrics
     * - SEED_PHRASE — seed phrase only
     * - SEED_PHRASE_BIOMETRIC — seed phrase + biometrics
     */
    fun isAuthStrategy(): AuthStrategy {
        val enableBiometric = secureStoragePrefs.getBoolean(BIOMETRIC_ENABLED_KEY, false)
        val authType = getAuthType()
        val hasPinCode = !secureStoragePrefs.getString(PIN_CODE_KEY, null).isNullOrBlank()

        return when (authType) {
            AuthType.NONE -> AuthStrategy.NONE
            AuthType.PIN -> {
                if (!hasPinCode) {
                    AuthStrategy.RECOVERY_DATA
                } else if (enableBiometric) {
                    AuthStrategy.PIN_BIOMETRIC
                } else {
                    AuthStrategy.PIN
                }
            }

            AuthType.SEED_PHRASE -> {
                if (enableBiometric) {
                    AuthStrategy.SEED_PHRASE_BIOMETRIC
                } else {
                    AuthStrategy.SEED_PHRASE
                }
            }
        }
    }

    /**
     * Enables biometric authentication if a PIN or Seed Phrase (master key) is already configured.
     *
     * @return true if biometrics was successfully enabled, false if no prior authentication is configured.
     */
    fun activateBiometricAuth(): Boolean {
        val hasPinCode = !secureStoragePrefs.getString(PIN_CODE_KEY, null).isNullOrBlank()
        val isMasterKeyAvailable = seedPhraseManager.hasStoredSeed()
        val isMasterKeyUserCreated = seedPhraseManager.isUserManuallyCreatedKeyByDecryption()
        val authType = getAuthType()

        val canEnableBiometric = when (authType) {
            AuthType.PIN -> hasPinCode
            AuthType.SEED_PHRASE -> isMasterKeyAvailable && isMasterKeyUserCreated
            else -> false
        }
        if (canEnableBiometric) {
            secureStoragePrefs.edit {
                putBoolean(BIOMETRIC_ENABLED_KEY, true)
            }
            return true
        }

        return false
    }


    /**
     * Checks whether biometric authentication is enabled.
     *
     * @return true if biometrics is enabled, false otherwise.
     */
    fun isBiometricEnabled(): Boolean {
        return secureStoragePrefs.getBoolean(BIOMETRIC_ENABLED_KEY, false)
    }

    /**
     * Disables biometric authentication by resetting the flag in SharedPreferences.
     */
    fun deactivateBiometricAuth() {
        secureStoragePrefs.edit {
            putBoolean(BIOMETRIC_ENABLED_KEY, false)
        }
    }

    /**
     * Returns the user's stored authorization type.
     *
     * Reads the AuthType value from SharedPreferences under the AUTH_TYPE_KEY key.
     * If the value is missing or invalid, returns AuthType.NONE.
     *
     * @return AuthType — the active authorization type:
     * - NONE — no authentication configured
     * - PIN — a PIN code is used
     * - SEED_PHRASE — a seed phrase is used
     */
    fun getAuthType(): AuthType {
        val authOrdinal = secureStoragePrefs.getInt(AUTH_TYPE_KEY, AuthType.NONE.ordinal)
        return AuthType.entries.getOrNull(IntWrapper.unwrap(authOrdinal)) ?: AuthType.NONE
    }


    /**
     * Milliseconds of PIN lockout still remaining (0 = not locked out).
     * The UI uses this to show a countdown and block input.
     */
    fun remainingLockoutMillis(): Long {
        val until = secureStoragePrefs.getLong(PIN_LOCKOUT_UNTIL_KEY, 0L)
        val now = System.currentTimeMillis()
        return if (until > now) until - now else 0L
    }

    /** Derives the PIN hash via salted PBKDF2-HMAC-SHA256. */
    private fun derivePinHash(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Records a failed attempt; enables a progressive lockout past MAX_ATTEMPTS. */
    private fun recordFailedAttempt() {
        val attempts = secureStoragePrefs.getInt(PIN_ATTEMPTS_KEY, 0) + 1
        secureStoragePrefs.edit {
            putInt(PIN_ATTEMPTS_KEY, attempts)
            if (attempts >= MAX_ATTEMPTS) {
                // 30s * 2^(over), capped at 15 min
                val over = (attempts - MAX_ATTEMPTS).coerceIn(0, 5)
                val lockMs = (30_000L shl over).coerceAtMost(15 * 60_000L)
                putLong(PIN_LOCKOUT_UNTIL_KEY, System.currentTimeMillis() + lockMs)
            }
        }
    }

    /** Clears the attempt counter and lockout (successful login / fresh activation). */
    private fun resetAttempts() {
        secureStoragePrefs.edit {
            remove(PIN_ATTEMPTS_KEY)
            remove(PIN_LOCKOUT_UNTIL_KEY)
        }
    }

    /**
     * Encrypts a text string using the AES key.
     * @return Base64 string containing the encrypted data + IV.
     */
    private fun encryptData(data: String): String? {
        return try {
            val secretKey = seedPhraseManager.getEncryptionKeyForData(SaltData.AUTH)
            val cipher = Cipher.getInstance(AES_MODE)
            val iv = ByteArray(IV_SIZE)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

            // Prepend the IV to the encrypted data (the IV is needed for decryption)
            val combined = iv + encryptedBytes

            Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decrypts a text string using the AES key.
     */
    private fun decryptData(encryptedData: String): String? {
        return try {
            val secretKey = seedPhraseManager.getEncryptionKeyForData(SaltData.AUTH)

            val combined = Base64.decode(encryptedData, Base64.DEFAULT)

            val iv = combined.copyOfRange(0, IV_SIZE)
            val encryptedBytes = combined.copyOfRange(IV_SIZE, combined.size)

            val cipher = Cipher.getInstance(AES_MODE)
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

}