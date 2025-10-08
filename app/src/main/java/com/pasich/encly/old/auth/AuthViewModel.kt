package com.pasich.encly.old.auth

import android.content.Context
import androidx.biometric.BiometricManager as AndroidBiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.BiometricManager
// TODO: Replace with new security architecture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val isAuthEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val hasPinCode: Boolean = false,
    val attemptCount: Int = 0,
    val isAuthenticated: Boolean = false,
    val showPinInput: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val securityWarning: String? = null,
    val isSecurityBlocked: Boolean = false,
    val pinVerificationResult: Boolean? = null // null = не перевірявся, true = успішно, false = помилка
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    // TODO: Replace with new security architecture
    private val biometricManager: BiometricManager
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    // Дебаунс період для запобігання частих оновлень
    private var lastUpdateTime = 0L
    private val updateDebounceMs = 100L


    // Кеш для уменьшения количества запросов к DataStore
    private var lastCheckTime = 0L
    private var cachedNeedAuth = false


    /**
     * Перевіряє доступність біометричної автентифікації
     */
    private fun isBiometricAvailable(): Boolean {
        return biometricManager.isStrongBiometricAvailable()
    }

    init {
        _state.value =
            _state.value.copy(isBiometricAvailable = isBiometricAvailable())

        // Виконуємо початкову перевірку безпеки
        viewModelScope.launch {
            performSecurityCheck()
        }

        viewModelScope.launch {
            // Використовуємо distinctUntilChanged для зменшення кількості оновлень
      /*      combine(
                getAuthSettingsUseCase().distinctUntilChanged(),
                getAuthAttemptCountUseCase().distinctUntilChanged()
            ) { (authEnabled, biometricEnabled, pinHash), attemptCount ->
                // Оновлюємо стан тільки якщо значення дійсно змінилися
                val currentState = _state.value
                if (currentState.isAuthEnabled != authEnabled ||
                    currentState.isBiometricEnabled != biometricEnabled ||
                    currentState.hasPinCode != pinHash.isNotEmpty() ||
                    currentState.attemptCount != attemptCount
                ) {

                    _state.value = currentState.copy(
                        isAuthEnabled = authEnabled,
                        isBiometricEnabled = biometricEnabled,
                        hasPinCode = pinHash.isNotEmpty(),
                        attemptCount = attemptCount
                    )
                }
            }.collect()

       */
        }
    }


    fun resetAuthState() {
        _state.value = _state.value.copy(
            isAuthenticated = false,
            showPinInput = false,
            errorMessage = null,
            isLoading = false
        )
    }

    fun setupAuth(isAuthEnabled: Boolean, isBiometricEnabled: Boolean, pinCode: String?) {
     //   setupAuthUseCase(isAuthEnabled, isBiometricEnabled, pinCode, viewModelScope)
    }

    fun verifyPin(pin: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < updateDebounceMs) {
            return // Запобігаємо занадто частим оновленням
        }
        lastUpdateTime = currentTime

        _state.value = _state.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            // TODO: Replace with new security architecture
            // Перевіряємо безпеку перед авторизацією
            // val securityResult = authSecurityManager.validateSecurityForAuth()

            // Тимчасово припускаємо, що безпека не заблокована
            val securityBlocked = false

            if (securityBlocked) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Авторизація заблокована з міркувань безпеки",
                    isSecurityBlocked = true
                )
                return@launch
            }

            // TODO: Replace with new security architecture
            // Застосовуємо rate limiting
            // authSecurityManager.applyRateLimit(_state.value.attemptCount)

          /*  val isValid = verifyPinUseCase(pin)
            if (isValid) {
                _state.value = _state.value.copy(
                    isAuthenticated = true,
                    isLoading = false,
                    showPinInput = false,
                    securityWarning = null
                )
                resetAttempts()
            } else {
                val newAttemptCount = _state.value.attemptCount + 1
                updateAuthAttemptUseCase(newAttemptCount, viewModelScope)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Невірний PIN-код. Спроб залишилось: ${5 - newAttemptCount}"
                )

                if (newAttemptCount >= 5) {
                    _state.value = _state.value.copy(showPinInput = true)
                }
            }

           */
        }
    }


    fun onBiometricSuccess() {
        _state.value = _state.value.copy(
            isAuthenticated = true,
            errorMessage = null
        )
        resetAttempts()
    }

    fun onBiometricError(error: String) {
        val newAttemptCount = _state.value.attemptCount + 1
      //  updateAuthAttemptUseCase(newAttemptCount, viewModelScope)

        _state.value = _state.value.copy(
            errorMessage = error,
            showPinInput = newAttemptCount >= 5
        )
    }

    fun onBiometricFailed() {
        val newAttemptCount = _state.value.attemptCount + 1
     //   updateAuthAttemptUseCase(newAttemptCount, viewModelScope)

        _state.value = _state.value.copy(
            errorMessage = "Біометрична авторизація не вдалася",
            showPinInput = newAttemptCount >= 5
        )
    }

    fun onBiometricNotAvailable() {
        _state.value = _state.value.copy(
            showPinInput = true,
            errorMessage = "Біометрична авторизація недоступна"
        )
    }

    fun showPinInput() {
        _state.value = _state.value.copy(showPinInput = true)
    }

    fun resetAttempts() {
     //   updateAuthAttemptUseCase(0, viewModelScope)
    }

    fun setBackgroundTime() {
     //   setBackgroundTimeUseCase(System.currentTimeMillis(), viewModelScope)
    }

    // Оптимизированный метод с кешированием результата
    fun checkAuthRequired(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            // Используем кеш, если минуло менш 1 секунди с последньої перевірки
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCheckTime < 1000) {                callback(cachedNeedAuth)
                return@launch
            }

          /*  getAuthSettingsUseCase().first().let { (authEnabled, _, _) ->
                // Кешируем результат и время проверки
                lastCheckTime = currentTime
                cachedNeedAuth = authEnabled

                callback(authEnabled)
            }

           */
        }
    }


    fun onAppResumed(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            // Используем кеш, если минуло менш 1 секунди с последньої перевірки
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCheckTime < 1000) {
                callback(cachedNeedAuth)
                return@launch
            }

         /*   val lastBackgroundTime = getLastBackgroundTimeUseCase().first()
            val authSettings = getAuthSettingsUseCase().first()

            val timeDiff = currentTime - lastBackgroundTime
            val fourMinutesInMillis = 4 * 60 * 1000L

            // Якщо авторизація увімкнена і пройшло більше 4 хвилин
            val isAuthRequired =
                authSettings.first && timeDiff > fourMinutesInMillis && lastBackgroundTime > 0

            // Кешуємо результат
            lastCheckTime = currentTime
            cachedNeedAuth = isAuthRequired

            if (isAuthRequired) {
                // Скидаємо лічильник спроб при поверненні з background
                resetAttempts()
            }

          */

          //  callback(isAuthRequired)
        }
    }

    /**
     * Перевіряє PIN-код для підтвердження дій (без автентифікації)
     */
    fun verifyPinForConfirmation(pin: String) {
        viewModelScope.launch {
          /*  val isValid = verifyPinUseCase(pin)
            _state.value = _state.value.copy(
                pinVerificationResult = isValid
            )

           */
        }
    }

    /**
     * Скидає результат перевірки PIN
     */
    fun clearPinVerificationResult() {
        _state.value = _state.value.copy(pinVerificationResult = null)
    }

    /**
     * Перевіряє доступність біометричної авторизації для налаштувань
     */
    fun checkBiometricAvailabilityForSettings(callback: (Boolean, String?) -> Unit) {
        if (biometricManager.isStrongBiometricAvailable()) {
            callback(true, null)
            return
        }

        val status = biometricManager.getBiometricStatus()
        val errorMessage = when (status) {
            BiometricManager.BiometricStatus.NO_HARDWARE -> 
                "Пристрій не підтримує біометричну авторизацію"
            BiometricManager.BiometricStatus.HARDWARE_UNAVAILABLE -> 
                "Біометричний сканер недоступний"
            BiometricManager.BiometricStatus.NONE_ENROLLED -> 
                "Не налаштовано жодного відбитка. Будь ласка, додайте відбиток в налаштуваннях пристрою"
            BiometricManager.BiometricStatus.SECURITY_UPDATE_REQUIRED -> 
                "Потрібне оновлення безпеки для використання біометрії"
            BiometricManager.BiometricStatus.UNSUPPORTED -> 
                "Біометрична авторизація не підтримується"
            BiometricManager.BiometricStatus.UNKNOWN -> 
                "Невідомий статус біометричної авторизації"
            else -> "Біометрична авторизація недоступна"
        }
        
        callback(false, errorMessage)
    }


    private suspend fun performSecurityCheck() {
        try {
            // TODO: Replace with new security architecture
            // val securityResult = authSecurityManager.validateSecurityForAuth()

            // Тимчасово припускаємо, що безпека в порядку
            val securityResult = "OK" // AuthSecurityResult.OK

            val warningMessage = when (securityResult) {
                // TODO: Replace with new security architecture
                // AuthSecurityResult.WARNING_DEBUG -> "Попередження: Додаток запущений у режимі налагодження"
                // AuthSecurityResult.WARNING_ROOTED -> "Попередження: Пристрій має root доступ"
                // AuthSecurityResult.WARNING_EMULATOR -> "Попередження: Додаток запущений на емуляторі"
                // AuthSecurityResult.WARNING_TAMPERED -> "Попередження: Цілісність додатку порушена"
                // AuthSecurityResult.BLOCKED_DEBUGGING -> "Заблоковано: Підключений налагоджувач"
                // AuthSecurityResult.BLOCKED_ROOTED_EMULATOR -> "Заблоковано: Небезпечне середовище"
                else -> null
            }

            _state.value = _state.value.copy(
                securityWarning = warningMessage,
                // TODO: Replace with new security architecture
                isSecurityBlocked = false, // securityResult == AuthSecurityResult.BLOCKED_DEBUGGING || securityResult == AuthSecurityResult.BLOCKED_ROOTED_EMULATOR,
                isBiometricAvailable = isBiometricAvailable() // && authSecurityManager.canUseBiometrics()
            )
        } catch (_: Exception) {
            // Не логуємо помилки в production
        }
    }

    fun toggleSwitchAuth(enabled: Boolean): DialogAuthType {
        if (!enabled) {
            // Вимикаємо авторизацію - спочатку запитуємо PIN
            if (_state.value.hasPinCode) {
                return DialogAuthType.PIN_CONFIRM_FOR_DISABLE
            } else {
                // Якщо PIN немає, просто вимикаємо
                setupAuth(
                    false,
                    isBiometricEnabled = false,
                    pinCode = ""
                )
            }
        } else {
            // Вмикаємо авторизацію - перевіряємо, чи є PIN
            if (!_state.value.hasPinCode) {
                // Якщо PIN немає, показуємо діалог для створення
                return DialogAuthType.PIN_SETUP
            } else {
                // Якщо PIN є, просто активуємо авторизацію
                setupAuth(
                    true,
                    _state.value.isBiometricEnabled,
                    null
                )
            }
        }
        return DialogAuthType.NONE
    }

    fun toggleBiometric(
        context: Context,
        fragmentActivity: FragmentActivity?,
        enable: Boolean,
        onError: (String) -> Unit
    ) {
        checkBiometricAvailabilityForSettings { isAvailable, errorMessage ->
            if (!isAvailable) {
                if (!enable) {
                    setupAuth(_state.value.isAuthEnabled, false, null)
                } else {
                    onError(errorMessage ?: "Біометрія недоступна")
                }
                return@checkBiometricAvailabilityForSettings
            }

            if (fragmentActivity == null) {
                if (!enable) {
                    setupAuth(_state.value.isAuthEnabled, false, null)
                } else {
                    onError("Помилка: неможливо показати біометричний діалог")
                }
                return@checkBiometricAvailabilityForSettings
            }

            try {
                val subtitle = if (enable) {
                    "Підтвердіть відбитком пальця для увімкнення біометричної авторизації"
                } else {
                    "Підтвердіть відбитком пальця для вимкнення біометричної авторизації"
                }

                val customConfig = BiometricManager.PromptConfig(
                    title = "Підтвердження",
                    subtitle = subtitle,
                    negativeButtonText = "Скасувати"
                )

                val callback = object : BiometricManager.BiometricCallback {
                    override fun onSuccess() {
                        setupAuth(_state.value.isAuthEnabled, enable, null)
                    }

                    override fun onError(errorCode: Int, errorMessage: String) {
                        if (!enable) {
                            setupAuth(_state.value.isAuthEnabled, false, null)
                        } else {
                            onError("Помилка біометричної автентифікації: $errorMessage")
                        }
                    }

                    override fun onFailed() {
                        if (!enable) {
                            setupAuth(_state.value.isAuthEnabled, false, null)
                        } else {
                            onError("Біометрична автентифікація не вдалася")
                        }
                    }
                }

                biometricManager.authenticate(
                    fragmentActivity,
                    BiometricManager.BiometricType.SETTINGS_TOGGLE,
                    callback,
                    customConfig
                )
            } catch (e: Exception) {
                if (!enable) {
                    setupAuth(_state.value.isAuthEnabled, false, null)
                } else {
                    onError("Помилка біометричної автентифікації")
                }
            }
        }
    }

    fun authenticateWithBiometric(context: Context) {
        if (context !is FragmentActivity) return

        val biometricPrompt = BiometricPrompt(
            context,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onBiometricSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        onBiometricError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onBiometricError("Біометрична автентифікація не вдалася")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Розблокувати My Notes")
            .setSubtitle("Використайте ваш відбиток пальця для доступу")
            .setNegativeButtonText("Скасувати")
            .setAllowedAuthenticators(AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

enum class DialogAuthType {
    PIN_SETUP,
    PIN_CONFIRM_FOR_DISABLE,
    NONE
}