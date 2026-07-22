package com.pasich.encly.domain.usecase

import com.pasich.encly.core.security.SaltData
import com.pasich.encly.core.security.SecurityConstants.ENCRYPTED_BLOCK_KEY
import com.pasich.encly.core.security.SecurityConstants.ENCRYPTED_BLOCK_KEY_TWO
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SeedPhraseManager
import com.pasich.encly.data.database.SecureDatabaseManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingUseCase @Inject constructor(
    private val securityManager: SecurityManager,
    private val seedPhraseManager: SeedPhraseManager,
    private val secureDatabaseManager: SecureDatabaseManager
) {

    /**
     * Generates a seed phrase and returns it for display (user-managed mode).
     */
    fun getMnemonicCode(): Result<String> {
        return try {
            Result.success(String(securityManager.generateMnemonicCode()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    /**
     * Saves the keys to the store and initializes the database.
     */
    fun saveKeysStore(
        target: CharArray = securityManager.generateMnemonicCode(),
        fake: CharArray = securityManager.generateMnemonicCode()
    ): Result<String> {
        return try {
            if (seedPhraseManager.storeSeedHash(
                    target, ENCRYPTED_BLOCK_KEY
                ) && seedPhraseManager.storeSeedHash(fake, ENCRYPTED_BLOCK_KEY_TWO)
            ) {

                // Initialize the database
                seedPhraseManager.useEncryptionKeyForData(SaltData.DATABASE) { key ->
                    secureDatabaseManager.unlockDatabase(key)
                }
                Result.success(
                    value = "Okay"
                )
            } else {
                Result.failure(IllegalStateException("Error cryptoSave"))
            }


        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    /**
     * Completes onboarding and marks the onboarding screen as shown.
     */
    fun completeOnboarding() {
        securityManager.setOnboardingShown()
    }
}
