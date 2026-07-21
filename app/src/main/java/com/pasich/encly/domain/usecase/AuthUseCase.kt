package com.pasich.encly.domain.usecase

import com.pasich.encly.core.security.AuthType
import com.pasich.encly.core.security.AuthenticationManager
import com.pasich.encly.core.security.SeedPhraseManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthUseCase @Inject constructor(
    private val authenticationManager: AuthenticationManager,
    private val seedPhraseManager: SeedPhraseManager
) {

    /**
     * Saves the PIN code as the authentication configuration via authenticationManager.
     *
     * @param pinCode the PIN code to save
     * @return Result.success("Okay") if saving succeeded,
     *         Result.failure if an error occurred during saving or encryption.
     */

    fun saveAuthConfigPinCode(
        pinCode: String,
    ): Result<Boolean> {
        return try {
            if (authenticationManager.getAuthType() == AuthType.PIN) {
                return Result.failure(
                    IllegalStateException("PIN authentication is already enabled")
                )
            }

            if (authenticationManager.activatePinAuth(pinCode)) {
                Result.success(true)
            } else {
                Result.failure(
                    IllegalStateException("Не вдалося активувати PIN-код: помилка під час збереження або шифрування даних.")
                )

            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


}