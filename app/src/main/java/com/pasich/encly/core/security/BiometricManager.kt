package com.pasich.encly.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.pasich.encly.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import androidx.biometric.BiometricManager as AndroidBiometricManager

/**
 * Biometric v2 unlock slot.
 *
 * The wrapping key lives in AndroidKeyStore and is auth-per-use. The DEK can only be wrapped or
 * unwrapped by the Cipher instance returned from a successful BIOMETRIC_STRONG CryptoObject
 * prompt. A plain "prompt succeeded" boolean never releases the database key.
 */
@Singleton
@Suppress("TooManyFunctions") // Centralizes the complete auth-bound biometric slot lifecycle.
class BiometricManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: VaultStore,
) {
    enum class BiometricType {
        APP_UNLOCK,
        MASTER_KEY_ACCESS,
        SETTINGS_TOGGLE,
        GENERAL,
    }

    interface BiometricCallback {
        fun onSuccess()
        fun onError(errorCode: Int, errorMessage: String)
        fun onFailed()
        fun onCancelled() {}
    }

    /** Prompt text. A null [negativeButtonText] falls back to the localized "Cancel". */
    data class PromptConfig(
        val title: String,
        val subtitle: String,
        val description: String? = null,
        val negativeButtonText: String? = null,
    )

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "encly_biometric_wrap_v2"
        private const val SLOT_PREFIX = "bio."
        private const val SLOT_KEY = "bio.slot"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
        private const val DEK_LENGTH = 32
        private const val AES_KEY_SIZE_BITS = 256
        private const val AUTH_PER_USE_SECONDS = 0
    }

    private val keyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    fun isStrongBiometricAvailable(): Boolean = strongBiometricStatus() == BiometricStatus.AVAILABLE

    /** Whether strong biometrics can be used, or why not. */
    fun strongBiometricStatus(): BiometricStatus = BiometricStatus.fromCanAuthenticate(
        AndroidBiometricManager.from(context).canAuthenticate(AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG),
    )

    fun hasSlot(): Boolean = try {
        keyStore.containsAlias(KEY_ALIAS) && store.contains(SLOT_KEY)
    } catch (_: Exception) {
        false
    }

    fun enroll(activity: FragmentActivity, dek: ByteArray, onResult: (Boolean) -> Unit) {
        if (!isStrongBiometricAvailable() || dek.size != DEK_LENGTH) {
            onResult(false)
            return
        }

        val dekCopy = dek.copyOf()
        try {
            store.edit { remove(SLOT_KEY) }
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
                        val stored = store.edit { putBytes(SLOT_KEY, combined) }
                        SensitiveDataCleaner.clear(encrypted)
                        SensitiveDataCleaner.clear(combined)
                        if (!stored) disable()
                        onResult(stored)
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
                },
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
    fun unlock(activity: FragmentActivity, onResult: (ByteArray?) -> Unit) {
        val combined = store.getBytes(SLOT_KEY)
        if (!isStrongBiometricAvailable() || combined == null) {
            combined?.let(SensitiveDataCleaner::clear)
            onResult(null)
            return
        }

        try {
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
                GCMParameterSpec(GCM_TAG_LENGTH, iv),
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
                },
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
        customConfig: PromptConfig? = null,
    ) {
        if (!isStrongBiometricAvailable()) {
            callback.onError(-1, activity.getString(R.string.biometric_unavailable))
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
            },
        )
        prompt.authenticate(createPromptInfo(activity, type, customConfig))
    }

    private fun promptWithCipher(
        activity: FragmentActivity,
        type: BiometricType,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onFailure: () -> Unit,
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
            },
        )
        prompt.authenticate(
            createPromptInfo(activity, type),
            BiometricPrompt.CryptoObject(cipher),
        )
    }

    /**
     * Prompt text is resolved from the activity (not the application context) so it follows the
     * in-app language chosen through AppCompatDelegate.setApplicationLocales.
     */
    private fun createPromptInfo(
        activity: FragmentActivity,
        type: BiometricType,
        customConfig: PromptConfig? = null,
    ): BiometricPrompt.PromptInfo {
        val config = customConfig ?: when (type) {
            BiometricType.APP_UNLOCK -> PromptConfig(
                title = activity.getString(R.string.biometric_prompt_unlock_title),
                subtitle = activity.getString(R.string.biometric_prompt_unlock_subtitle),
                negativeButtonText = activity.getString(R.string.biometric_prompt_use_pin),
            )

            BiometricType.MASTER_KEY_ACCESS -> PromptConfig(
                title = activity.getString(R.string.biometric_prompt_key_title),
                subtitle = activity.getString(R.string.biometric_prompt_key_subtitle),
            )

            BiometricType.SETTINGS_TOGGLE -> PromptConfig(
                title = activity.getString(R.string.biometric_prompt_settings_title),
                subtitle = activity.getString(R.string.biometric_prompt_settings_subtitle),
            )

            BiometricType.GENERAL -> PromptConfig(
                title = activity.getString(R.string.biometric_prompt_general_title),
                subtitle = activity.getString(R.string.biometric_prompt_general_subtitle),
            )
        }

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(config.title)
            .setSubtitle(config.subtitle)
            .setNegativeButtonText(
                config.negativeButtonText ?: activity.getString(R.string.cancel),
            )
            .setAllowedAuthenticators(AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG)

        config.description?.let { builder.setDescription(it) }
        return builder.build()
    }

    private fun generateAuthBoundKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                AUTH_PER_USE_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG,
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
            ?: error("Biometric key is missing")
        return entry.secretKey
    }

    private fun deleteKeyOnly() {
        try {
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
    }

    fun disable() {
        store.edit { removePrefix(SLOT_PREFIX) }
        deleteKeyOnly()
    }
}

/** Whether strong (class 3) biometrics can unlock the vault on this device. */
enum class BiometricStatus {
    AVAILABLE,

    /** The hardware is there, but no fingerprint (or other strong biometric) is enrolled yet. */
    NOT_ENROLLED,

    /** No suitable hardware, or it cannot be used right now. */
    UNAVAILABLE,
    ;

    companion object {
        /** Maps a `BiometricManager.canAuthenticate` result. */
        fun fromCanAuthenticate(result: Int): BiometricStatus = when (result) {
            AndroidBiometricManager.BIOMETRIC_SUCCESS -> AVAILABLE
            AndroidBiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> NOT_ENROLLED
            else -> UNAVAILABLE
        }
    }
}
