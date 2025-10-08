package com.pasich.encly.utils

import android.content.Context
import com.pasich.encly.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsValidator @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun validateScreenProtection(isEnabled: Boolean): ValidationResult {
        return if (isEnabled) {
            ValidationResult.Success
        } else {
            ValidationResult.Warning(
                context.getString(R.string.screen_protection_warning)
            )
        }
    }

    fun validateAuthDisabling(isEnabled: Boolean, hasPinSet: Boolean): ValidationResult {
        return if (!isEnabled && hasPinSet) {
            ValidationResult.Warning(
                context.getString(R.string.auth_disable_warning)
            )
        } else {
            ValidationResult.Success
        }
    }

    fun validateBiometricDisabling(isEnabled: Boolean, isPrimaryAuth: Boolean): ValidationResult {
        return if (!isEnabled && isPrimaryAuth) {
            ValidationResult.Error(
                context.getString(R.string.biometric_disable_error)
            )
        } else {
            ValidationResult.Success
        }
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
