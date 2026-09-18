package com.pasich.encly.domain.usecase

import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SeedPhraseManager
import com.pasich.encly.core.security.SensitiveDataCleaner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingUseCase @Inject constructor(
    private val securityManager: SecurityManager,
    private val seedPhraseManager: SeedPhraseManager
) {
    fun getMnemonicCode(): Result<String> = try {
        val chars = securityManager.generateMnemonicCode()
        try {
            Result.success(String(chars))
        } finally {
            SensitiveDataCleaner.clear(chars)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Creates a fresh v2 vault.
     *
     * Existing call sites pass equal arrays for USER_MANAGED and two unrelated generated arrays
     * for AUTO_MANAGED. Equality is used only to choose whether a recovery slot is created.
     */
    fun saveKeysStore(
        target: CharArray = securityManager.generateMnemonicCode(),
        fake: CharArray = securityManager.generateMnemonicCode()
    ): Result<String> {
        val userManaged = target.contentEquals(fake)
        return try {
            val recoverySeed = if (userManaged) target else null
            if (seedPhraseManager.initializeVault(recoverySeed)) {
                Result.success("Okay")
            } else {
                Result.failure(IllegalStateException("Failed to initialize v2 vault"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            SensitiveDataCleaner.clear(target)
            if (fake !== target) SensitiveDataCleaner.clear(fake)
        }
    }

    /**
     * Deliberately does not persist completion. First-run setup becomes durable only after the
     * mandatory PIN slot exists and SQLCipher opens successfully in finishInitialSetup().
     */
    fun completeOnboarding() = Unit
}
