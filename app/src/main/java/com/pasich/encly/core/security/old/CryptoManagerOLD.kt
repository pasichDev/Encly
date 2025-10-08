package com.pasich.encly.core.security.old

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoManager @Inject constructor() {
    
    companion object {
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA512"
        private const val PBKDF2_ITERATIONS = 100000
        private const val MASTER_KEY_SIZE = 256
        private const val SALT_SIZE = 32
        private const val IV_SIZE = 12
        private const val GCM_TAG_LENGTH = 16
    }

    /**
     * Генерує master key з сід-фрази
     */
    fun deriveMasterKey(seedPhrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(seedPhrase, salt, PBKDF2_ITERATIONS, MASTER_KEY_SIZE)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val key = factory.generateSecret(spec)
        return key.encoded
    }

    /**
     * Генерує ключі для шифрування з master key
     */
    fun deriveEncryptionKey(masterKey: ByteArray, purpose: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val keySpec = SecretKeySpec(masterKey, HMAC_ALGORITHM)
        mac.init(keySpec)
        return mac.doFinal(purpose.toByteArray()).copyOf(32) // 256 bits
    }

    /**
     * Шифрує дані з використанням AES-GCM
     */
    fun encrypt(data: ByteArray, key: ByteArray): EncryptedData {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data)
        
        return EncryptedData(encryptedData, iv)
    }

    /**
     * Дешифрує дані
     */
    fun decrypt(encryptedData: EncryptedData, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val secretKey = SecretKeySpec(key, "AES")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, encryptedData.iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(encryptedData.data)
    }

    /**
     * Генерує криптографічно стійку сіль
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Генерує HMAC для автентифікації даних
     */
    fun generateHMAC(data: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val keySpec = SecretKeySpec(key, HMAC_ALGORITHM)
        mac.init(keySpec)
        return mac.doFinal(data)
    }

    /**
     * Перевіряє HMAC
     */
    fun verifyHMAC(data: ByteArray, hmac: ByteArray, key: ByteArray): Boolean {
        val expectedHmac = generateHMAC(data, key)
        return hmac.contentEquals(expectedHmac)
    }

    /**
     * Безпечно очищує ByteArray
     */
    fun clearSensitiveData(data: ByteArray) {
        data.fill(0)
    }

    /**
     * Безпечно очищує CharArray
     */
    fun clearSensitiveData(data: CharArray) {
        data.fill(0.toChar())
    }
}

data class EncryptedData(
    val data: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedData

        if (!data.contentEquals(other.data)) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}
