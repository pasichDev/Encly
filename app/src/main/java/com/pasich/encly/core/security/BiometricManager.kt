package com.pasich.encly.core.security

/**
 * The biometric unlock slot, as the vault logic sees it. The Android implementation is
 * [KeystoreBiometricManager]: the DEK is wrapped by a Keystore key that only a successful
 * BIOMETRIC_STRONG prompt releases, so a plain "prompt succeeded" boolean never opens the database.
 */
interface BiometricManager {
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

    fun isStrongBiometricAvailable(): Boolean

    /** Whether strong biometrics can be used, or why not. */
    fun strongBiometricStatus(): BiometricStatus

    fun hasSlot(): Boolean

    /** Wraps [dek] under a new auth-bound key after a prompt on [host]; [onResult] says whether the slot was stored. */
    fun enroll(host: BiometricHost, dek: ByteArray, onResult: (Boolean) -> Unit)

    /**
     * Returns the DEK only after a successful auth-bound operation. The caller owns the returned
     * bytes and must zeroize them.
     */
    fun unlock(host: BiometricHost, onResult: (ByteArray?) -> Unit)

    /** Generic re-auth prompt for settings actions that do not themselves release a key. */
    fun authenticate(
        host: BiometricHost,
        type: BiometricType,
        callback: BiometricCallback,
        customConfig: PromptConfig? = null,
    )

    /** Removes the slot and the key behind it. */
    fun disable()

    companion object {
        /** The [VaultStore] key prefix of the slot's entries. */
        const val SLOT_PREFIX = "bio."
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

    companion object
}
