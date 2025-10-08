package com.pasich.encly.old.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasich.encly.MainActivity
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.authenticateWithBiometric
import com.pasich.encly.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AuthActivity : FragmentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    @Inject
    lateinit var biometricManager: BiometricManager

    // Для отслеживания, нужно ли показывать биометрию
    private var shouldShowBiometric = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Сбрасываем состояние аутентификации при запуске
        authViewModel.resetAuthState()

        // Сразу показываем биометрию при запуске, если она доступна
        val state = authViewModel.state.value
        if (shouldShowBiometric.value &&
            state.isBiometricEnabled &&
            state.attemptCount < 5 &&
            !state.showPinInput
        ) {
            showBiometricPrompt()
        }

        setContent {
            AppTheme {
                // Отслеживаем состояние аутентификации
                val state by authViewModel.state.collectAsStateWithLifecycle()

                // Показываем биометрию, только если она еще не была показана
                LaunchedEffect(state.isBiometricEnabled, state.attemptCount, state.showPinInput) {
                    if (shouldShowBiometric.value &&
                        state.isBiometricEnabled &&
                        state.attemptCount < 5 &&
                        !state.showPinInput
                    ) {
                        shouldShowBiometric.value = false
                        showBiometricPrompt()
                    }
                }

                // Отслеживаем успешную аутентификацию
                LaunchedEffect(state.isAuthenticated) {
                    if (state.isAuthenticated) {
                        // Возвращаемся в MainActivity с флагом успешной аутентификации
                        val intent = Intent(this@AuthActivity, MainActivity::class.java)
                        intent.putExtra("JUST_AUTHENTICATED", true)
                        startActivity(intent)
                        finish()
                    }
                }

                // Используем наш основной AuthScreen
                AuthScreen(
                    authViewModel = authViewModel,
                    onAuthSuccess = {
                        // Переходим к основному приложению и закрываем AuthActivity
                        val intent = Intent(this@AuthActivity, MainActivity::class.java)
                        intent.putExtra("JUST_AUTHENTICATED", true)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    private fun showBiometricPrompt() {
        if (!biometricManager.isStrongBiometricAvailable()) {
            Log.d("AuthActivity", "Biometric not available")
            authViewModel.onBiometricNotAvailable()
            return
        }

        Log.d("AuthActivity", "Showing BiometricPrompt...")

        authenticateWithBiometric(
            biometricManager = biometricManager,
            type = BiometricManager.BiometricType.APP_UNLOCK,
            onSuccess = {
                authViewModel.onBiometricSuccess()
            },
            onError = { errorMessage ->
                authViewModel.onBiometricError(errorMessage)
            },
            onFailed = {
                authViewModel.onBiometricFailed()
            },
            onCancelled = {
                // Якщо користувач натиснув відміну або негативну кнопку,
                // показуємо екран для введення PIN
                authViewModel.showPinInput()
            }
        )
    }

    override fun onResume() {
        super.onResume()

        // Проверяем, нужно ли показывать биометрию при возвращении в приложение
        val state = authViewModel.state.value
        if (shouldShowBiometric.value &&
            state.isBiometricEnabled &&
            state.attemptCount < 5 &&
            !state.showPinInput
        ) {

            shouldShowBiometric.value = false
            showBiometricPrompt()
        }
    }
}