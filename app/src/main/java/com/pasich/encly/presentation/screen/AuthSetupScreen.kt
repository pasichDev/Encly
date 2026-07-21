package com.pasich.encly.presentation.screen

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.compose.ui.platform.LocalContext
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.pincode.AuthLoading
import com.pasich.encly.presentation.screen.pincode.PinCodeWidget
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.viewmodel.AuthSetupViewModel

private enum class SetupStep { CREATE_PIN, CONFIRM_PIN, BIOMETRIC }

/**
 * Mandatory security setup after onboarding: create a PIN and, when available, enable
 * biometric unlock. On completion the database is unlocked and the app opens. PIN
 * hashing and DB unlock run off the main thread and show a loading state.
 */
@Composable
fun AuthSetupScreen(
    navController: NavHostController,
    viewModel: AuthSetupViewModel = hiltViewModel()
) {
    val activity = LocalContext.current as? FragmentActivity
    val busy by viewModel.busy.collectAsState()

    var step by remember { mutableStateOf(SetupStep.CREATE_PIN) }
    var firstPin by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun goHome() {
        navController.navigate(NavRoutes.HomeRoute.name) {
            popUpTo(NavRoutes.AuthSetupRoute.name) { inclusive = true }
        }
    }

    fun finish() {
        viewModel.finishSetup { ok ->
            if (ok) goHome() else error = "Не вдалося відкрити базу даних"
        }
    }

    fun afterPinSet() {
        if (viewModel.biometricAvailable() && activity != null) step = SetupStep.BIOMETRIC else finish()
    }

    // PIN entry handling
    LaunchedEffect(input, step) {
        if (input.length == 4 && step != SetupStep.BIOMETRIC) {
            when (step) {
                SetupStep.CREATE_PIN -> {
                    firstPin = input
                    input = ""
                    error = null
                    step = SetupStep.CONFIRM_PIN
                }

                SetupStep.CONFIRM_PIN -> {
                    if (input != firstPin) {
                        error = "PIN-коди не збігаються"
                        input = ""
                        firstPin = ""
                        step = SetupStep.CREATE_PIN
                    } else {
                        val pin = input
                        input = ""
                        viewModel.setPin(pin) { ok ->
                            if (ok) {
                                afterPinSet()
                            } else {
                                error = "Не вдалося зберегти PIN"
                                firstPin = ""
                                step = SetupStep.CREATE_PIN
                            }
                        }
                    }
                }

                SetupStep.BIOMETRIC -> Unit
            }
        }
    }

    // Auto-launch the biometric prompt when entering that step
    LaunchedEffect(step) {
        if (step == SetupStep.BIOMETRIC && activity != null) {
            viewModel.enableBiometric(activity) { ok -> if (ok) finish() }
        }
    }

    if (busy) {
        AuthLoading("Зачекайте…")
        return
    }

    val title = when (step) {
        SetupStep.CREATE_PIN -> "Створіть PIN-код"
        SetupStep.CONFIRM_PIN -> "Підтвердіть PIN-код"
        SetupStep.BIOMETRIC -> "Увімкніть біометрію"
    }
    val subtitle = when (step) {
        SetupStep.CREATE_PIN -> "PIN-код обов'язковий для захисту нотаток"
        SetupStep.CONFIRM_PIN -> "Повторіть PIN-код для підтвердження"
        SetupStep.BIOMETRIC -> "Підтвердіть біометрію для швидкого входу"
    }

    PinEntryScaffold(title = title, subtitle = subtitle, error = error) {
        if (step == SetupStep.BIOMETRIC) {
            TextButton(onClick = {
                if (activity != null) viewModel.enableBiometric(activity) { ok -> if (ok) finish() }
            }) {
                Text("Повторити біометрію")
            }
            TextButton(onClick = { finish() }) {
                Text("Продовжити лише з PIN")
            }
        } else {
            PinCodeWidget(
                pinInput = input,
                onPinChange = { if (input.length < 4) input += it },
                onDelete = { if (input.isNotEmpty()) input = input.dropLast(1) }
            )
        }
    }
}
