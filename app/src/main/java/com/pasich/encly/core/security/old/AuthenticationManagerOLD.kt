package com.pasich.encly.core.security.old

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthenticationManagerOLD @Inject constructor(
    private val context: Context,
    private val cryptoManager: CryptoManager
) {
    
    companion object {
        private const val AUTH_PREFS = "auth_preferences"
        private const val PIN_HASH_KEY = "pin_hash"
        private const val PIN_SALT_KEY = "pin_salt"
        private const val BIOMETRIC_ENABLED_KEY = "biometric_enabled"
        private const val AUTH_METHOD_KEY = "auth_method"
        private const val AUTH_ENABLED_KEY = "auth_enabled"
    }

    private val authPrefs = EncryptedSharedPreferences.create(
        context,
        AUTH_PREFS,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _authState = MutableStateFlow(AuthState())
    val authState: Flow<AuthState> = _authState.asStateFlow()

    enum class AuthMethod {
        NONE,
        PIN,
        BIOMETRIC,
        PIN_AND_BIOMETRIC
    }

    data class AuthState(
        val isAuthEnabled: Boolean = false,
        val isPinEnabled: Boolean = false,
        val isBiometricEnabled: Boolean = false,
        val isBiometricAvailable: Boolean = false,
        val authMethod: AuthMethod = AuthMethod.NONE,
        val isAuthenticated: Boolean = false
    )

    sealed class AuthResult {
        object Success : AuthResult()
        object Failed : AuthResult()
        object Cancelled : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    init {
        updateAuthState()
    }

    /**
     * Встановлює PIN-код
     */
    fun setPinCode(pin: String): Boolean {
        return try {
            val salt = cryptoManager.generateSalt()
            val pinHash = hashPin(pin, salt)
            
            authPrefs.edit {
                putString(PIN_HASH_KEY, pinHash)
                putString(PIN_SALT_KEY, Base64.encodeToString(salt, Base64.DEFAULT))
                putBoolean(AUTH_ENABLED_KEY, true)
            }
            
            updateAuthState()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Перевіряє PIN-код
     */
    fun verifyPin(pin: String): Boolean {
        val storedHash = authPrefs.getString(PIN_HASH_KEY, null) ?: return false
        val saltBase64 = authPrefs.getString(PIN_SALT_KEY, null) ?: return false
        
        val salt = Base64.decode(saltBase64, Base64.DEFAULT)
        val inputHash = hashPin(pin, salt)
        
        val isValid = storedHash == inputHash
        if (isValid) {
            _authState.value = _authState.value.copy(isAuthenticated = true)
        }
        return isValid
    }

    /**
     * Видаляє PIN-код
     */
    fun removePinCode(): Boolean {
        return try {
            authPrefs.edit {
                remove(PIN_HASH_KEY)
                remove(PIN_SALT_KEY)
                putBoolean(BIOMETRIC_ENABLED_KEY, false)
                putBoolean(AUTH_ENABLED_KEY, false)
            }
            updateAuthState()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Вмикає/вимикає біометричну авторизацію
     */
    fun setBiometricEnabled(enabled: Boolean): Boolean {
        if (enabled && !isBiometricAvailable()) return false
        if (enabled && !isPinEnabled()) return false
        
        return try {
            authPrefs.edit {
                putBoolean(BIOMETRIC_ENABLED_KEY, enabled)
            }
            updateAuthState()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Перевіряє доступність біометрії
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Перевіряє, чи увімкнена авторизація
     */
    fun isAuthEnabled(): Boolean = authPrefs.getBoolean(AUTH_ENABLED_KEY, false)

    /**
     * Перевіряє, чи увімкнений PIN
     */
    fun isPinEnabled(): Boolean = authPrefs.contains(PIN_HASH_KEY)

    /**
     * Перевіряє, чи увімкнена біометрія
     */
    fun isBiometricEnabled(): Boolean = authPrefs.getBoolean(BIOMETRIC_ENABLED_KEY, false)

    /**
     * Аутентифікація з біометрією
     */
    fun authenticateWithBiometric(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        if (!isBiometricAvailable() || !isBiometricEnabled()) {
            onError("Біометрична автентифікація недоступна")
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    _authState.value = _authState.value.copy(isAuthenticated = true)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Біометрична автентифікація")
            .setSubtitle("Підтвердіть свою особу")
            .setNegativeButtonText("Використати PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Вихід з системи
     */
    fun logout() {
        _authState.value = _authState.value.copy(isAuthenticated = false)
    }

    /**
     * Скидає всі налаштування авторизації
     */
    fun reset() {
        authPrefs.edit().clear().apply()
        _authState.value = AuthState()
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hash = digest.digest(pin.toByteArray())
        return Base64.encodeToString(hash, Base64.DEFAULT)
    }

    private fun updateAuthState() {
        val isAuthEnabled = isAuthEnabled()
        val isPinEnabled = isPinEnabled()
        val isBiometricEnabled = isBiometricEnabled()
        val isBiometricAvailable = isBiometricAvailable()
        
        val authMethod = when {
            isPinEnabled && isBiometricEnabled -> AuthMethod.PIN_AND_BIOMETRIC
            isBiometricEnabled -> AuthMethod.BIOMETRIC
            isPinEnabled -> AuthMethod.PIN
            else -> AuthMethod.NONE
        }

        _authState.value = AuthState(
            isAuthEnabled = isAuthEnabled,
            isPinEnabled = isPinEnabled,
            isBiometricEnabled = isBiometricEnabled,
            isBiometricAvailable = isBiometricAvailable,
            authMethod = authMethod,
            isAuthenticated = _authState.value.isAuthenticated
        )
    }
}
