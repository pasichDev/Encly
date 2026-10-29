package com.pasich.encly.core.security

/**
 * The biometric unlock slot, as the vault logic sees it. The Android implementation is
 * [KeystoreBiometricManager]: the DEK is wrapped by a Keystore key that only a successful
 * BIOMETRIC_STRONG prompt releases, so a plain "prompt succeeded" boolean never opens the database.
 */
public interface BiometricManager {
    public enum class BiometricType {
        APP_UNLOCK,
        MASTER_KEY_ACCESS,
        SETTINGS_TOGGLE,
        GENERAL,
    }

    public interface BiometricCallback {
        public fun onSuccess()
        public fun onError(errorCode: Int, errorMessage: String)
        public fun onFailed()
        public fun onCancelled() {}
    }

    /** Prompt text. A null [negativeButtonText] falls back to the localized "Cancel". */
    public data class PromptConfig(
        val title: String,
        val subtitle: String,
        val description: String? = null,
        val negativeButtonText: String? = null,
    )

    public fun isStrongBiometricAvailable(): Boolean

    /** Whether strong biometrics can be used, or why not. */
    public fun strongBiometricStatus(): BiometricStatus

    public fun hasSlot(): Boolean

    /** Wraps [dek] under a new auth-bound key after a prompt on [host]; [onResult] says whether the slot was stored. */
    public fun enroll(host: BiometricHost, dek: ByteArray, onResult: (Boolean) -> Unit)

    /**
     * Returns the DEK only after a successful auth-bound operation. The caller owns the returned
     * bytes and must zeroize them.
     */
    public fun unlock(host: BiometricHost, onResult: (ByteArray?) -> Unit)

    /** Generic re-auth prompt for settings actions that do not themselves release a key. */
    public fun authenticate(
        host: BiometricHost,
        type: BiometricType,
        callback: BiometricCallback,
        customConfig: PromptConfig? = null,
    )

    /** Removes the slot and the key behind it. */
    public fun disable()

    public companion object {
        /** The [VaultStore] key prefix of the slot's entries. */
        public const val SLOT_PREFIX: String = "bio."
    }
}

/** Whether strong (class 3) biometrics can unlock the vault on this device. */
public enum class BiometricStatus {
    AVAILABLE,

    /** The hardware is there, but no fingerprint (or other strong biometric) is enrolled yet. */
    NOT_ENROLLED,

    /** No suitable hardware, or it cannot be used right now. */
    UNAVAILABLE,
    ;

    public companion object
}
