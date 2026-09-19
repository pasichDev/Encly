package com.pasich.encly.core.security

import android.content.SharedPreferences
import java.util.Base64
import androidx.core.content.edit
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

const val PIN_LENGTH = 6

enum class AuthType {
    NONE, PIN, SEED_PHRASE
}

enum class AuthStrategy {
    NONE, PIN, PIN_BIOMETRIC, SEED_PHRASE, SEED_PHRASE_BIOMETRIC, RECOVERY_DATA
}

/**
 * Encly v2 PIN unlock slot.
 *
 * There is no stored PIN hash. The PIN derives a KEK with PBKDF2-HMAC-SHA256 and that KEK
 * must successfully authenticate/decrypt the random database DEK using AES-256-GCM.
 */
@Singleton
class AuthenticationManager @Inject constructor(
    private val secureStoragePrefs: SharedPreferences
) {
    companion object {
        private const val PIN_SLOT_KEY = "v2_pin_slot"
        private const val PIN_SALT_KEY = "v2_pin_salt"
        private const val AUTH_TYPE_KEY = "auth_type_v2"
        private const val BIOMETRIC_ENABLED_KEY = "biometric_enabled_v2"

        private const val PIN_ATTEMPTS_KEY = "pin_attempts_v2"
        private const val PIN_LOCKOUT_UNTIL_KEY = "pin_lockout_until_v2"
        private const val MAX_ATTEMPTS = 5
        private const val PIN_SALT_SIZE = 16
        private const val PBKDF2_ITERATIONS = 600_000
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
        private const val DEK_LENGTH = 32

        private val PIN_AAD = "encly/pin/slot/v2".toByteArray(Charsets.UTF_8)
    }

    fun configurePin(code: String, dek: ByteArray): Boolean {
        if (code.length != PIN_LENGTH || !code.all(Char::isDigit) || dek.size != DEK_LENGTH) return false

        val pinChars = code.toCharArray()
        val salt = ByteArray(PIN_SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val kek = derivePinKek(pinChars, salt)
        return try {
            val wrapped = wrapDek(dek, kek)
            val committed = secureStoragePrefs.edit()
                .putString(PIN_SALT_KEY, Base64.getEncoder().encodeToString(salt))
                .putString(PIN_SLOT_KEY, Base64.getEncoder().encodeToString(wrapped))
                .putInt(AUTH_TYPE_KEY, AuthType.PIN.ordinal)
                .remove(PIN_ATTEMPTS_KEY)
                .remove(PIN_LOCKOUT_UNTIL_KEY)
                .commit()
            SensitiveDataCleaner.clear(wrapped)
            committed
        } catch (_: Exception) {
            false
        } finally {
            SensitiveDataCleaner.clear(pinChars)
            SensitiveDataCleaner.clear(salt)
            SensitiveDataCleaner.clear(kek)
        }
    }

    /**
     * Returns the unwrapped DEK on success. The caller owns and must zeroize it.
     */
    @Suppress("ReturnCount") // Early exits are fail-closed validation gates for corrupted/missing slot state.
    fun unlockWithPin(inputCode: String): ByteArray? {
        if (remainingLockoutMillis() > 0) return null
        if (inputCode.length != PIN_LENGTH || !inputCode.all(Char::isDigit)) {
            recordFailedAttempt()
            return null
        }

        val saltEncoded = secureStoragePrefs.getString(PIN_SALT_KEY, null) ?: return null
        val slotEncoded = secureStoragePrefs.getString(PIN_SLOT_KEY, null) ?: return null
        val salt = try {
            Base64.getDecoder().decode(saltEncoded)
        } catch (_: Exception) {
            return null
        }
        val wrapped = try {
            Base64.getDecoder().decode(slotEncoded)
        } catch (_: Exception) {
            SensitiveDataCleaner.clear(salt)
            return null
        }

        val pinChars = inputCode.toCharArray()
        val kek = derivePinKek(pinChars, salt)
        return try {
            val dek = unwrapDek(wrapped, kek)
            if (dek != null) {
                resetAttempts()
            } else {
                recordFailedAttempt()
            }
            dek
        } catch (_: AEADBadTagException) {
            recordFailedAttempt()
            null
        } catch (_: Exception) {
            recordFailedAttempt()
            null
        } finally {
            SensitiveDataCleaner.clear(pinChars)
            SensitiveDataCleaner.clear(salt)
            SensitiveDataCleaner.clear(wrapped)
            SensitiveDataCleaner.clear(kek)
        }
    }

    fun verifyPinAuth(inputCode: String): Boolean {
        val dek = unlockWithPin(inputCode) ?: return false
        SensitiveDataCleaner.clear(dek)
        return true
    }

    fun hasPinSlot(): Boolean =
        !secureStoragePrefs.getString(PIN_SALT_KEY, null).isNullOrBlank() &&
            !secureStoragePrefs.getString(PIN_SLOT_KEY, null).isNullOrBlank()

    fun getAuthType(): AuthType {
        val ordinal = secureStoragePrefs.getInt(AUTH_TYPE_KEY, AuthType.NONE.ordinal)
        return AuthType.entries.getOrNull(ordinal) ?: AuthType.NONE
    }

    fun isAuthStrategy(): AuthStrategy {
        val authType = getAuthType()
        val biometric = isBiometricEnabled()
        return when (authType) {
            AuthType.NONE -> AuthStrategy.NONE
            AuthType.PIN -> when {
                !hasPinSlot() -> AuthStrategy.RECOVERY_DATA
                biometric -> AuthStrategy.PIN_BIOMETRIC
                else -> AuthStrategy.PIN
            }
            AuthType.SEED_PHRASE ->
                if (biometric) AuthStrategy.SEED_PHRASE_BIOMETRIC else AuthStrategy.SEED_PHRASE
        }
    }

    fun markBiometricEnabled(enabled: Boolean) {
        secureStoragePrefs.edit { putBoolean(BIOMETRIC_ENABLED_KEY, enabled) }
    }

    fun isBiometricEnabled(): Boolean =
        secureStoragePrefs.getBoolean(BIOMETRIC_ENABLED_KEY, false)

    fun deactivateBiometricAuth() = markBiometricEnabled(false)

    fun remainingLockoutMillis(): Long {
        val until = secureStoragePrefs.getLong(PIN_LOCKOUT_UNTIL_KEY, 0L)
        val now = System.currentTimeMillis()
        return if (until > now) until - now else 0L
    }

    private fun derivePinKek(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun wrapDek(dek: ByteArray, kek: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"))
        cipher.updateAAD(PIN_AAD)
        val encrypted = cipher.doFinal(dek)
        val iv = cipher.iv
        return ByteArray(iv.size + encrypted.size).also { combined ->
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        }
    }

    private fun unwrapDek(wrapped: ByteArray, kek: ByteArray): ByteArray? {
        if (wrapped.size <= IV_LENGTH) return null
        val iv = wrapped.copyOfRange(0, IV_LENGTH)
        val ciphertext = wrapped.copyOfRange(IV_LENGTH, wrapped.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(kek, "AES"),
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )
            cipher.updateAAD(PIN_AAD)
            cipher.doFinal(ciphertext)
        } finally {
            SensitiveDataCleaner.clear(iv)
            SensitiveDataCleaner.clear(ciphertext)
        }
    }

    private fun recordFailedAttempt() {
        val attempts = secureStoragePrefs.getInt(PIN_ATTEMPTS_KEY, 0) + 1
        secureStoragePrefs.edit {
            putInt(PIN_ATTEMPTS_KEY, attempts)
            if (attempts >= MAX_ATTEMPTS) {
                val over = (attempts - MAX_ATTEMPTS).coerceIn(0, 5)
                val lockMs = (30_000L shl over).coerceAtMost(15 * 60_000L)
                putLong(PIN_LOCKOUT_UNTIL_KEY, System.currentTimeMillis() + lockMs)
            }
        }
    }

    private fun resetAttempts() {
        secureStoragePrefs.edit {
            remove(PIN_ATTEMPTS_KEY)
            remove(PIN_LOCKOUT_UNTIL_KEY)
        }
    }

    fun wipe() {
        secureStoragePrefs.edit().clear().commit()
    }
}
