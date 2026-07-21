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
        private const val IV_SIZE = 16 // 128 біт

        // PIN hardening
        private const val PIN_ATTEMPTS_KEY = "pin_attempts"
        private const val PIN_LOCKOUT_UNTIL_KEY = "pin_lockout_until"
        private const val MAX_ATTEMPTS = 5
        private const val PIN_SALT_SIZE = 16
        private const val PBKDF2_ITERATIONS = 600_000
    }

    /**
     * Активує PIN-аутентифікацію, зберігаючи хеш PIN-коду у захищеному сховищі.
     *
     * @param code Введений користувачем PIN-код у вигляді рядка.
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
     * Перевіряє введений PIN-код, порівнюючи його хеш із збереженим.
     *
     * @param inputCode Введений користувачем PIN-код для перевірки.
     * @return true, якщо PIN-коди співпадають, інакше false.
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
     * Відміняє (видаляє) автентифікацію за PIN-кодом, якщо введений код вірний.
     * Перевіряє, чи співпадає введений PIN з збереженим.
     * Якщо так, видаляє збережений PIN та змінює тип автентифікації на NONE.
     *
     * @param inputCode Введений користувачем PIN-код для перевірки.
     * @return true, якщо PIN вірний і автентифікація скасована, інакше false.
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
     * Визначає активну стратегію авторизації користувача (AuthStrategy),
     * базуючись на збереженому типі авторизації (AuthType) та стані біометрії.
     *
     * @return AuthStrategy, яка відображає, як саме користувач має проходити авторизацію:
     * - NONE — не налаштовано жодної авторизації
     * - PIN — лише пін-код
     * - PIN_BIOMETRIC — пін-код + біометрія
     * - SEED_PHRASE — лише сід-фраза
     * - SEED_PHRASE_BIOMETRIC — сід-фраза + біометрія
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
     * Активує біометричну автентифікацію, якщо вже налаштовано PIN або Seed Phrase (master key).
     *
     * @return true, якщо біометрію успішно активовано, false — якщо попередня автентифікація не налаштована.
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
     * Перевіряє, чи активована біометрична автентифікація.
     *
     * @return true, якщо біометрія увімкнена, false — якщо ні.
     */
    fun isBiometricEnabled(): Boolean {
        return secureStoragePrefs.getBoolean(BIOMETRIC_ENABLED_KEY, false)
    }

    /**
     * Деактивує біометричну автентифікацію, скидаючи прапорець у SharedPreferences.
     */
    fun deactivateBiometricAuth() {
        secureStoragePrefs.edit {
            putBoolean(BIOMETRIC_ENABLED_KEY, false)
        }
    }

    /**
     * Повертає збережений тип авторизації користувача.
     *
     * Метод зчитує значення AuthType (тип авторизації) з SharedPreferences за ключем AUTH_TYPE_KEY.
     * Якщо значення не знайдено або воно некоректне, повертається AuthType.NONE.
     *
     * @return AuthType — тип активної авторизації:
     * - NONE — авторизація не налаштована
     * - PIN — використовується PIN-код
     * - SEED_PHRASE — використовується сід-фраза
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
     * Шифрує текстовий рядок з використанням AES ключа
     * @return Base64-рядок із зашифрованими даними + IV
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

            // З'єднуємо IV + зашифровані дані (для розшифровки знадобиться IV)
            val combined = iv + encryptedBytes

            Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Розшифровує текстовий рядок із AES ключем
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