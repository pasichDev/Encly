package com.pasich.encly.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pasich.encly.data.database.SecureDatabaseManager
import javax.inject.Inject
import javax.inject.Singleton

enum class InitialStatus {
    NO, MAIN, ONBOARDING, LOSS_DATABASE, LOSS_CRYPTO, AUTH, SETUP_AUTH
}


/**
 * Startup security gate. On construction it determines the initial app state
 * ([InitialStatus]) — onboarding, crypto/database loss, auth required, or main —
 * used to route the first screen after launch.
 */
@Singleton
class SecurityManager @Inject constructor(
    private val secureStoragePrefs: SharedPreferences,
    private val seedPhraseManager: SeedPhraseManager,
    private val secureDatabaseManager: SecureDatabaseManager,
    private val authenticationManager: AuthenticationManager
) {

    companion object {
        private const val ONBOARDING_SHOWN_KEY = "onboarding_shown"
    }

    var securityStatus = InitialStatus.NO

    init {
        securityStatus = initializeSecurity()
    }

    /**
     * Computes the initial app state and, unless a lock is configured, unlocks the DB.
     *
     * If the user configured a PIN-based lock, the database is NOT unlocked here —
     * [InitialStatus.AUTH] is returned so the UI shows the lock screen, and unlock
     * happens only after successful authentication via [unlockAfterAuth].
     */
    private fun initializeSecurity(): InitialStatus {
        if (isOnboardingShow()) {
            return InitialStatus.ONBOARDING
        }

        // Key integrity check
        if (!seedPhraseManager.verificationKeyData()) {
            return InitialStatus.LOSS_CRYPTO
        }

        // PIN is mandatory. Exhaustive over AuthStrategy so no state can silently
        // fall through to an unauthenticated unlock: lock if a PIN is set, else force setup.
        return when (authenticationManager.isAuthStrategy()) {
            AuthStrategy.PIN, AuthStrategy.PIN_BIOMETRIC -> InitialStatus.AUTH
            AuthStrategy.NONE,
            AuthStrategy.RECOVERY_DATA,
            AuthStrategy.SEED_PHRASE,
            AuthStrategy.SEED_PHRASE_BIOMETRIC -> InitialStatus.SETUP_AUTH
        }
    }

    /** Derives the DB key from the seed and unlocks the encrypted database. */
    private fun unlockDatabase(): Boolean =
        secureDatabaseManager.unlockDatabase(
            seedPhraseManager.getEncryptionKeyForData(SaltData.DATABASE)
        )

    /** Auth strategy configured for unlocking the app (PIN / PIN+biometric / none / …). */
    fun authStrategy(): AuthStrategy = authenticationManager.isAuthStrategy()

    /** Whether biometric unlock is enabled in settings. */
    fun isBiometricEnabled(): Boolean = authenticationManager.isBiometricEnabled()

    /** Remaining PIN lockout in milliseconds (0 = not locked out). */
    fun pinLockoutRemainingMillis(): Long = authenticationManager.remainingLockoutMillis()

    /** Verifies the app PIN against the stored hash (raw digits, leading zeros preserved). */
    fun verifyPin(pin: String): Boolean = authenticationManager.verifyPinAuth(pin)

    /**
     * Unlocks the encrypted database after successful authentication. Call only from
     * the lock screen once the user has passed PIN/biometric. Updates [securityStatus].
     */
    fun unlockAfterAuth(): Boolean {
        val ok = unlockDatabase()
        if (ok) securityStatus = InitialStatus.MAIN
        return ok
    }


    /** Чи потрібно показувати онбординг
     *  Умови для цього, неіснуючий хеш seed-фрази, невідмічений онбординг
     */
    fun isOnboardingShow(): Boolean {
        return !isOnboardingShown() && !seedPhraseManager.hasStoredSeed()
    }


    /** Чи вже показували онбординг */
    private fun isOnboardingShown(): Boolean {
        return secureStoragePrefs.getBoolean(ONBOARDING_SHOWN_KEY, false)
    }

    /**
     * Marks onboarding as completed. Only records it once the keys are valid
     * (integrity verified) — otherwise there is nothing to protect yet.
     */
    fun setOnboardingShown(): Boolean {
        if (seedPhraseManager.verificationKeyData()) {
            secureStoragePrefs.edit {
                putBoolean(ONBOARDING_SHOWN_KEY, true)
            }
            return true
        }
        return false
    }

    /** Створює Seed phase для показу користувачеві **/
    fun generateMnemonicCode(): CharArray {
        return seedPhraseManager.generateMnemonic().chars
    }

    /**
     * Перевіряє, чи був ключ шифрування створений вручну користувачем (через введення сід-фрази),
     * а не згенерований автоматично під час першого запуску.
     *
     * @return true — якщо ключ створено вручну користувачем, false — якщо згенеровано автоматично
     */
  //  fun isUserCreatedKey(): Boolean {
 //       return seedPhraseManager.isUserManuallyCreatedKeyByDecryption()
 //   }

    /**
     * Wipes all encrypted data and security state (DB files, seed prefs, Keystore keys,
     * integrity HMAC, auth prefs) for the unrecoverable-loss path, then resets to
     * onboarding. Everything is lost by design (zero-knowledge model).
     */
    fun wipeAndReset() {
        secureDatabaseManager.wipe()
        seedPhraseManager.wipe()
        secureStoragePrefs.edit { clear() }
        securityStatus = InitialStatus.ONBOARDING
    }

    fun getSettingsAuth(): AuthSettings {
        return AuthSettings(
            authType = authenticationManager.getAuthType(),
            isBiometricEnabled = authenticationManager.isBiometricEnabled(),
            isUserCreatedSeedKey = seedPhraseManager.isUserManuallyCreatedKeyByDecryption()
        )
    }


}


data class AuthSettings(
    val authType: AuthType,
    val isBiometricEnabled: Boolean,
    val isUserCreatedSeedKey: Boolean
)
