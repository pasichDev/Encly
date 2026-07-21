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
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

enum class SaltData {
    DATABASE, AUTH, MEDIA
}

object SecurityConstants {
    const val ENCRYPTED_BLOCK_KEY = "encrypted_seed_block"

    // Обманка для верифікації чи користувач самстворив ключ якщо ENCRYPTED_BLOCK_KEY == ENCRYPTED_BLOCK_KEY_TWO то ручне налаштування
    const val ENCRYPTED_BLOCK_KEY_TWO = "encrypted_seed_block_two"
}

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

    /** Генерує нову 12-слівну сид-фразу */
    fun generateMnemonic(): MnemonicCode = MnemonicCode(WordCount.COUNT_12)

    /** Валідує фразу (кидає виняток якщо недійсна) */
    fun isValidMnemonic(phrase: CharArray): Boolean = try {
        MnemonicCode(phrase).validate()
        true
    } catch (_: Exception) {
        false
    }

    /** Обчислює SHA-256 хеш фрази */
    fun hashMnemonic(phrase: CharArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(String(phrase).toByteArray(Charsets.UTF_8))
    }

    /** Шифрує хеш + IV разом і зберігає в SharedPreferences */
    fun storeSeedHash(phrase: CharArray, typeKey: String): Boolean {
        val hash = hashMnemonic(phrase)

        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(hash)

            // Об’єднуємо IV + зашифрований хеш
            val combined = ByteArray(iv.size + encrypted.size).apply {
                System.arraycopy(iv, 0, this, 0, iv.size)
                System.arraycopy(encrypted, 0, this, iv.size, encrypted.size)
            }

            val base64 = Base64.encodeToString(combined, Base64.NO_WRAP)

            prefs.edit {
                putString(typeKey, base64)
            }
            if (typeKey == ENCRYPTED_BLOCK_KEY) {
                // Зберігаємо Hmac для майбутньої перевірки
                hmacIntegrityManager.storeHmac(hash)
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /** 🧠 Перевіряє фразу, розшифровуючи блок з SharedPreferences */
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
            inputHash.contentEquals(decryptedHash)
        } catch (_: Exception) {
            false
        } finally {
            SensitiveDataCleaner.clear(phrase)
        }
    }


    /**
     * Повертає SecretKey для шифрування даних, базуючись на хеші сид-фрази (розшифрованому)
     * та переданій солі.
     *
     * @param @salt Сіль, яка використовується для генерації унікального ключа для конкретного типу даних
     * @return SecretKey для AES шифрування
     * @throws IllegalStateException якщо хеш сид-фрази не збережено або не вдалося розшифрувати
     */
    fun getEncryptionKeyForData(saltType: SaltData): SecretKey {
        val slay = "ABCDGHFJKALDGFHJBASOKPDNNCVSH*(E"

        // Формуємо сіль у байтах, додаючи потрібну кількість символів з slay
        val saltBytes = when (saltType) {
            SaltData.DATABASE -> saltType.name.toByteArray(Charsets.UTF_8) + slay.take(3)
                .toByteArray(Charsets.UTF_8)

            SaltData.AUTH -> saltType.name.toByteArray(Charsets.UTF_8) + slay.take(7)
                .toByteArray(Charsets.UTF_8)

            SaltData.MEDIA -> saltType.name.toByteArray(Charsets.UTF_8) + slay.take(1)
                .toByteArray(Charsets.UTF_8)
        }

        // Розшифровуємо хеш сид-фрази (має бути байтовий масив)
        val decryptedHash = decryptSeedHash(ENCRYPTED_BLOCK_KEY)
            ?: throw IllegalStateException("Cannot decrypt seed hash")

        // Перетворюємо байти у CharArray (припустимо, що decryptedHash - це UTF-8 рядок)
        val passwordChars = String(decryptedHash, Charsets.UTF_8).toCharArray()

        // Створюємо специфікацію для PBKDF2
        val specPbkdf2 = PBEKeySpec(passwordChars, saltBytes, 10000, 256)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val tmp = factory.generateSecret(specPbkdf2)

        SensitiveDataCleaner.clear(decryptedHash)
        SensitiveDataCleaner.clear(passwordChars)
        SensitiveDataCleaner.clear(saltBytes)

        // Повертаємо SecretKeySpec для AES
        return SecretKeySpec(tmp.encoded, "AES")
    }


    /** Чи збережено хеш сідфрази */
    fun hasStoredSeed(): Boolean = prefs.contains(ENCRYPTED_BLOCK_KEY)


    /**
     * Верифікації збереженого ключа за допомогою HMAC
     * Використовується для захисту від пошкодження додатку
     */
    fun verificationKeyData(): Boolean {
        val hash = decryptSeedHash(ENCRYPTED_BLOCK_KEY) ?: return false
        return !hmacIntegrityManager.isHashTampered(hash)
    }

    /** Очищення збережених даних */
    fun clearStoredSeed() {
        prefs.edit {
            remove(ENCRYPTED_BLOCK_KEY)
        }
    }


    /**
     * Перевіряє, чи сид-фраза була створена вручну користувачем.
     * Якщо значення `ENCRYPTED_BLOCK_KEY` і `ENCRYPTED_BLOCK_KEY_TWO` однакові — користувач не вводив фразу.
     * Якщо різні — користувач ввів власну фразу.
     *
     * @return true якщо фраза створена вручну, false якщо ні
     */
    fun isUserManuallyCreatedKeyByDecryption(): Boolean {
        val auto = decryptSeedHash(ENCRYPTED_BLOCK_KEY) ?: return false
        val manual = decryptSeedHash(ENCRYPTED_BLOCK_KEY_TWO) ?: return false

        return !auto.contentEquals(manual)
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
