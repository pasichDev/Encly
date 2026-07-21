package com.pasich.encly.core.security

import android.content.Context
import androidx.biometric.BiometricManager as AndroidBiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized manager for biometric authentication.
 * Consolidates all BiometricPrompt usage scenarios in the app.
 */
@Singleton
class BiometricManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * Biometric authentication statuses.
     */
    enum class BiometricStatus {
        AVAILABLE,
        NO_HARDWARE,
        HARDWARE_UNAVAILABLE,
        NONE_ENROLLED,
        SECURITY_UPDATE_REQUIRED,
        UNSUPPORTED,
        UNKNOWN
    }

    /**
     * Biometric prompt types.
     */
    enum class BiometricType {
        APP_UNLOCK,           // App unlock
        MASTER_KEY_ACCESS,    // Master key access
        SETTINGS_TOGGLE,      // Enable/disable biometrics in settings
        GENERAL              // General use
    }

    /**
     * Biometric authentication result.
     */
    sealed class BiometricResult {
        object Success : BiometricResult()
        data class Error(val errorCode: Int, val errorMessage: String) : BiometricResult()
        object Failed : BiometricResult()
        object Cancelled : BiometricResult()
    }

    /**
     * Callback for authentication results.
     */
    interface BiometricCallback {
        fun onSuccess()
        fun onError(errorCode: Int, errorMessage: String)
        fun onFailed()
        fun onCancelled() {}
    }

    /** Checks whether weak biometric authentication is available. */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = AndroidBiometricManager.from(context)
        return when (biometricManager.canAuthenticate(AndroidBiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            AndroidBiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Checks whether strong biometric authentication is available.
     */
    fun isStrongBiometricAvailable(): Boolean {
        val biometricManager = AndroidBiometricManager.from(context)
        return when (biometricManager.canAuthenticate(AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            AndroidBiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Gets the detailed biometric authentication status.
     */
    fun getBiometricStatus(): BiometricStatus {
        val biometricManager = AndroidBiometricManager.from(context)
        return when (biometricManager.canAuthenticate(AndroidBiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            AndroidBiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            AndroidBiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            AndroidBiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            AndroidBiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            AndroidBiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            AndroidBiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricStatus.UNSUPPORTED
            AndroidBiometricManager.BIOMETRIC_STATUS_UNKNOWN -> BiometricStatus.UNKNOWN
            else -> BiometricStatus.UNKNOWN
        }
    }

    /**
     * Creates a biometric prompt for authentication.
     */
    fun createBiometricPrompt(
        activity: FragmentActivity,
        callback: BiometricCallback
    ): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val authCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                callback.onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || 
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    callback.onCancelled()
                } else {
                    callback.onError(errorCode, errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback.onFailed()
            }
        }

        return BiometricPrompt(activity, executor, authCallback)
    }

    /**
     * Creates prompt info depending on the usage type.
     */
    fun createPromptInfo(type: BiometricType, customConfig: PromptConfig? = null): BiometricPrompt.PromptInfo {
        val config = customConfig ?: getDefaultConfig(type)
        
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(config.title)
            .setSubtitle(config.subtitle)
            .setNegativeButtonText(config.negativeButtonText)

        config.description?.let { builder.setDescription(it) }
        
        builder.setAllowedAuthenticators(
            if (type == BiometricType.APP_UNLOCK) {
                AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG
            } else {
                AndroidBiometricManager.Authenticators.BIOMETRIC_WEAK
            }
        )

        return builder.build()
    }

    /**
     * Launches biometric authentication.
     */
    fun authenticate(
        activity: FragmentActivity,
        type: BiometricType,
        callback: BiometricCallback,
        customConfig: PromptConfig? = null
    ) {
        if (!isBiometricAvailable()) {
            callback.onError(-1, "Біометрична автентифікація недоступна")
            return
        }

        val prompt = createBiometricPrompt(activity, callback)
        val promptInfo = createPromptInfo(type, customConfig)
        
        try {
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            callback.onError(-1, "Помилка запуску біометричної автентифікації: ${e.message}")
        }
    }

    /**
     * Coroutine-based asynchronous version of authentication.
     */
    suspend fun authenticateAsync(
        activity: FragmentActivity,
        type: BiometricType,
        customConfig: PromptConfig? = null
    ): BiometricResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : BiometricCallback {
                override fun onSuccess() {
                    continuation.resumeWith(Result.success(BiometricResult.Success))
                }

                override fun onError(errorCode: Int, errorMessage: String) {
                    continuation.resumeWith(Result.success(BiometricResult.Error(errorCode, errorMessage)))
                }

                override fun onFailed() {
                    continuation.resumeWith(Result.success(BiometricResult.Failed))
                }

                override fun onCancelled() {
                    continuation.resumeWith(Result.success(BiometricResult.Cancelled))
                }
            }

            authenticate(activity, type, callback, customConfig)
        }
    }

    /**
     * Configuration for the prompt.
     */
    data class PromptConfig(
        val title: String,
        val subtitle: String,
        val description: String? = null,
        val negativeButtonText: String = "Скасувати"
    )

    /**
     * Returns the default configuration for each type.
     */
    private fun getDefaultConfig(type: BiometricType): PromptConfig {
        return when (type) {
            BiometricType.APP_UNLOCK -> PromptConfig(
                title = "Розблокування додатку",
                subtitle = "Використайте відбиток пальця для входу",
                description = "Підтвердіть свою особу для доступу до нотаток",
                negativeButtonText = "Використати PIN"
            )
            
            BiometricType.MASTER_KEY_ACCESS -> PromptConfig(
                title = "Доступ до сід-фрази",
                subtitle = "Підтвердіть свою особу для перегляду сід-фрази",
                description = "Використовуйте відбиток пальця або розпізнавання обличчя"
            )
            
            BiometricType.SETTINGS_TOGGLE -> PromptConfig(
                title = "Підтвердження",
                subtitle = "Підтвердіть зміну налаштувань безпеки",
                description = "Біометрична автентифікація для зміни налаштувань"
            )
            
            BiometricType.GENERAL -> PromptConfig(
                title = "Автентифікація",
                subtitle = "Підтвердіть свою особу",
                description = "Використовуйте біометричну автентифікацію"
            )
        }
    }
}

/**
 * Composable helper for working with the biometric manager.
 */
@Composable
fun rememberBiometricManager(
    context: Context
): BiometricManager {
    return remember { BiometricManager(context) }
}

/**
 * Extension for FragmentActivity for convenient use.
 */
fun FragmentActivity.authenticateWithBiometric(
    biometricManager: BiometricManager,
    type: BiometricManager.BiometricType,
    onSuccess: () -> Unit,
    onError: (String) -> Unit = {},
    onFailed: () -> Unit = {},
    onCancelled: () -> Unit = {},
    customConfig: BiometricManager.PromptConfig? = null
) {
    val callback = object : BiometricManager.BiometricCallback {
        override fun onSuccess() = onSuccess()
        override fun onError(errorCode: Int, errorMessage: String) = onError(errorMessage)
        override fun onFailed() = onFailed()
        override fun onCancelled() = onCancelled()
    }
    
    biometricManager.authenticate(this, type, callback, customConfig)
}

/**
 * Coroutine extension for asynchronous authentication.
 */
fun FragmentActivity.authenticateWithBiometricAsync(
    biometricManager: BiometricManager,
    type: BiometricManager.BiometricType,
    customConfig: BiometricManager.PromptConfig? = null,
    onResult: (BiometricManager.BiometricResult) -> Unit
) {
    lifecycleScope.launch {
        val result = biometricManager.authenticateAsync(this@authenticateWithBiometricAsync, type, customConfig)
        onResult(result)
    }
}
