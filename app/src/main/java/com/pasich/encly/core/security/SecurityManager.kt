package com.pasich.encly.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pasich.encly.data.database.SecureDatabaseManager
import javax.inject.Inject
import javax.inject.Singleton

enum class InitialStatus {
    NO, MAIN, ONBOARDING, LOSS_DATABASE, LOSS_CRYPTO, AUTH
}


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
     * Метод ініціалізації безпеки повертає InitialStatus та вказує нвагіцію після запуску
     */
    private fun initializeSecurity(): InitialStatus {
        // Якщо хочеш перевірити ключ, можна зробити так:
        // if (!seedPhraseManager.isUserManuallyCreatedKeyByDecryption()) {
        //     // Логіка, якщо ключ створений не вручну
        // }

        // Перевірка онбордингу
        if (isOnboardingShow()) {
            return InitialStatus.ONBOARDING
        }

        // Перевірка ключів на цілісність
        if (!seedPhraseManager.verificationKeyData()) {
            return InitialStatus.LOSS_CRYPTO
        }

        // TODO: Додати перевірку автентифікації пін+біометрія або майстерключ

        // Перевірка доступності та розблокування бази даних
        if (!secureDatabaseManager.unlockDatabase(seedPhraseManager.getEncryptionKeyForData(SaltData.DATABASE))) {
            return InitialStatus.LOSS_DATABASE
        }

        return InitialStatus.MAIN
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

    /** Позначити, що онбординг уже пройдено */
    fun setOnboardingShown(): Boolean {
        if (!seedPhraseManager.verificationKeyData()) {
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
