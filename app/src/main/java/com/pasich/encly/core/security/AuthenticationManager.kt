package com.pasich.encly.core.security

import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.pasich.encly.core.security.wrapper.IntWrapper
import com.pasich.encly.core.security.wrapper.StringWrapper
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

enum class AuthType {
    NONE, PIN, SEED_PHRASE
}

enum class AuthStrategy {
    NONE, PIN, PIN_BIOMETRIC, SEED_PHRASE, SEED_PHRASE_BIOMETRIC, RECOVERY_DATA
}

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

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(code.toByteArray(Charsets.UTF_8))

            // Перетворюємо байти в HEX рядок
            val hashHex = hashBytes.joinToString("") { "%02x".format(it) }

            // Обгортаємо ключ (префікс + сіль + хеш)
            val pinWrapped = StringWrapper.wrapMasterKey(hashHex)

            // Шифруємо
            val encryptedPin = encryptData(pinWrapped)

            // Зберігаємо зашифрований рядок у SharedPreferences
            secureStoragePrefs.edit {
                putString(PIN_CODE_KEY, encryptedPin)
                putInt(AUTH_TYPE_KEY, IntWrapper.wrap(AuthType.PIN.ordinal))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Перевіряє введений PIN-код, порівнюючи його хеш із збереженим.
     *
     * @param inputCode Введений користувачем PIN-код для перевірки.
     * @return true, якщо PIN-коди співпадають, інакше false.
     */
    fun verifyPinAuth(inputCode: String): Boolean {
        // Отримуємо зашифрований PIN з SharedPreferences
        val encryptedPin = secureStoragePrefs.getString(PIN_CODE_KEY, null) ?: return false

        // Розшифровуємо
        val decryptedPinWrapped = decryptData(encryptedPin) ?: return false

        // Розгортаємо обгортку, щоб отримати хеш
        val storedHash = StringWrapper.unwrapMasterKey(decryptedPinWrapped) ?: return false

        // Генеруємо хеш від введеного PIN (як в активації)
        val digest = MessageDigest.getInstance("SHA-256")
        val inputHashBytes = digest.digest(inputCode.toByteArray(Charsets.UTF_8))
        val inputHashHex = inputHashBytes.joinToString("") { "%02x".format(it) }

        // Порівнюємо
        return storedHash == inputHashHex
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


    /// TODO Реалізувати метод авторизації мастерключ та виключення

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