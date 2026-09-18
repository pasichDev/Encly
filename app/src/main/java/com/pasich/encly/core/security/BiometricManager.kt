package com.pasich.encly.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager as AndroidBiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Biometric v2 unlock slot.
 *
 * The wrapping key lives in AndroidKeyStore and is auth-per-use. The DEK can only be wrapped or
 * unwrapped by the Cipher instance returned from a successful BIOMETRIC_STRONG CryptoObject
 * prompt. A plain "prompt succeeded" boolean never releases the database key.
 */
@Singleton
class BiometricManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    enum class BiometricType {
        APP_UNLOCK,
        MASTER_KEY_ACCESS,
        SETTINGS_TOGGLE,
        GENERAL
    }

    interface BiometricCallback {
        fun onSuccess()
        fun onError(errorCode: Int, errorMessage: String)
        fun onFailed()
        fun onCancelled() {}
    }

    data class PromptConfig(
        val title: String,
        val subtitle: String,
        val description: String? = null,
        val negativeButtonText: String = "Скасувати"
    )

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "encly_biometric_wrap_v2"
        private const val PREF_NAME = "encly_biometric_v2"
        private const val SLOT_KEY = "biometric_slot"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val keyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    fun isStrongBiometricAvailable(): Boolean {
        val manager = AndroidBiometricManager.from(context)
        return manager.canAuthenticate(AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            AndroidBiometricManager.BIOMETRIC_SUCCESS
    }

    fun hasSlot(): Boolean =
        keyStore.containsAlias(KEY_ALIAS) && !prefs.getString(SLOT_KEY, null).isNullOrBlank()

    fun enroll(
        activity: FragmentActivity,
        dek: ByteArray,
        onResult: (Boolean) -> Unit
    ) {
        if (!isStrongBiometricAvailable() || dek.size != 32) {
            onResult(false)
            return
        }

        val dekCopy = dek.copyOf()
        try {
            deleteKeyOnly()
            val key = generateAuthBoundKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)

            promptWithCipher(
                activity = activity,
                type = BiometricType.SETTINGS_TOGGLE,
                cipher = cipher,
                onSuccess = { authenticatedCipher ->
                    try {
                        val encrypted = authenticatedCipher.doFinal(dekCopy)
                        val iv = authenticatedCipher.iv
                        val combined = ByteArray(iv.size + encrypted.size).also { out ->
                            System.arraycopy(iv, 0, out, 0, iv.size)
                            System.arraycopy(encrypted, 0, out, iv.size, encrypted.size)
                        }
                        prefs.edit {
                            putString(SLOT_KEY, Base64.encodeToString(combined, Base64.NO_WRAP))
                        }
                        SensitiveDataCleaner.clear(encrypted)
                        SensitiveDataCleaner.clear(combined)
                        onResult(true)
                    } catch (_: Exception) {
                        disable()
                        onResult(false)
                    } finally {
                        SensitiveDataCleaner.clear(dekCopy)
                    }
                },
                onFailure = {
                    SensitiveDataCleaner.clear(dekCopy)
                    deleteKeyOnly()
                    onResult(false)
                }
            )
        } catch (_: Exception) {
            SensitiveDataCleaner.clear(dekCopy)
            disable()
            onResult(false)
        }
    }

    /**
     * Returns the DEK only after a successful auth-bound CryptoObject operation.
     * The caller owns the returned bytes and must zeroize them.
     */
    fun unlock(
        activity: FragmentActivity,
        onResult: (ByteArray?) -> Unit
    ) {
        val encoded = prefs.getString(SLOT_KEY, null)
        if (!isStrongBiometricAvailable() || encoded.isNullOrBlank()) {
            onResult(null)
            return
        }

        try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH) {
                SensitiveDataCleaner.clear(combined)
                onResult(null)
                return
            }

            val iv = combined.copyOfRange(0, IV_LENGTH)
            val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
            val key = getAuthBoundKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )

            promptWithCipher(
                activity = activity,
                type = BiometricType.APP_UNLOCK,
                cipher = cipher,
                onSuccess = { authenticatedCipher ->
                    val dek = try {
                        authenticatedCipher.doFinal(ciphertext)
                    } catch (_: Exception) {
                        null
                    } finally {
                        SensitiveDataCleaner.clear(iv)
                        SensitiveDataCleaner.clear(ciphertext)
                        SensitiveDataCleaner.clear(combined)
                    }
                    onResult(dek)
                },
                onFailure = {
                    SensitiveDataCleaner.clear(iv)
                    SensitiveDataCleaner.clear(ciphertext)
                    SensitiveDataCleaner.clear(combined)
                    onResult(null)
                }
            )
        } catch (_: KeyPermanentlyInvalidatedException) {
            disable()
            onResult(null)
        } catch (_: Exception) {
            onResult(null)
        }
    }

    /** Generic re-auth prompt for settings actions that do not themselves release a key. */
    fun authenticate(
        activity: FragmentActivity,
        type: BiometricType,
        callback: BiometricCallback,
        customConfig: PromptConfig? = null
    ) {
        if (!isStrongBiometricAvailable()) {
            callback.onError(-1, "Біометрична автентифікація недоступна")
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    callback.onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        callback.onCancelled()
                    } else {
                        callback.onError(errorCode, errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    callback.onFailed()
                }
            }
        )
        prompt.authenticate(createPromptInfo(type, customConfig))
    }

    private fun promptWithCipher(
        activity: FragmentActivity,
        type: BiometricType,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onFailure: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticatedCipher = result.cryptoObject?.cipher
                    if (authenticatedCipher != null) onSuccess(authenticatedCipher) else onFailure()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure()
                }

                override fun onAuthenticationFailed() {
                    // Keep the system prompt alive. A failed scan is not a terminal result.
                }
            }
        )
        prompt.authenticate(
            createPromptInfo(type),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    private fun createPromptInfo(
        type: BiometricType,
        customConfig: PromptConfig? = null
    ): BiometricPrompt.PromptInfo {
        val config = customConfig ?: when (type) {
            BiometricType.APP_UNLOCK -> PromptConfig(
                title = "Розблокування Encly",
                subtitle = "Підтвердіть біометрію для доступу до зашифрованого ключа",
                negativeButtonText = "Використати PIN"
            )
            BiometricType.MASTER_KEY_ACCESS -> PromptConfig(
                title = "Доступ до ключа",
                subtitle = "Підтвердіть біометрію"
            )
            BiometricType.SETTINGS_TOGGLE -> PromptConfig(
                title = "Підтвердження безпеки",
                subtitle = "Підтвердіть біометрію для зміни захисту"
            )
            BiometricType.GENERAL -> PromptConfig(
                title = "Автентифікація",
                subtitle = "Підтвердіть свою особу"
            )
        }

        return BiometricPrompt.PromptInfo.Builder()
            .setTitle(config.title)
            .setSubtitle(config.subtitle)
            .setNegativeButtonText(config.negativeButtonText)
            .setAllowedAuthenticators(AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG)
            .apply { config.description?.let(::setDescription) }
            .build()
    }

    private fun generateAuthBoundKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun getAuthBoundKey(): SecretKey {
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Biometric key is missing")
        return entry.secretKey
    }

    private fun deleteKeyOnly() {
        try {
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
    }

    fun disable() {
        prefs.edit { clear() }
        deleteKeyOnly()
    }
}
