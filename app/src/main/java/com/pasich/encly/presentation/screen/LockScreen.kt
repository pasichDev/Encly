package com.pasich.encly.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.pasich.encly.core.security.PIN_LENGTH
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.pincode.AuthLoading
import com.pasich.encly.presentation.screen.pincode.PinCodeWidget
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.viewmodel.LockViewModel
import com.pasich.encly.presentation.viewmodel.PinUnlockResult
import com.pasich.encly.presentation.viewmodel.SeedUnlockResult
import kotlinx.coroutines.delay

@Composable
fun LockScreen(
    navController: NavHostController,
    viewModel: LockViewModel = hiltViewModel()
) {
    val activity = LocalContext.current as? FragmentActivity
    val busy by viewModel.busy.collectAsState()
    var useRecovery by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { }

    val biometricEnabled = remember {
        viewModel.biometricEnabled() && viewModel.biometricAvailable()
    }

    fun goHome() {
        navController.navigate(NavRoutes.HomeRoute.name) {
            popUpTo(NavRoutes.LockRoute.name) { inclusive = true }
        }
    }

    fun promptBiometric() {
        if (activity != null && viewModel.lockoutRemainingMillis() <= 0) {
            viewModel.authenticateBiometric(activity) { unlocked ->
                if (unlocked) goHome()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (biometricEnabled) promptBiometric()
    }

    if (busy) {
        AuthLoading("Розблокування…")
        return
    }

    if (useRecovery) {
        SeedLockContent(
            viewModel = viewModel,
            onUnlocked = ::goHome,
            onUsePin = { useRecovery = false }
        )
    } else {
        PinLockContent(
            viewModel = viewModel,
            capabilities = LockCapabilities(
                biometricEnabled = biometricEnabled && activity != null,
                recoveryAvailable = viewModel.recoveryAvailable()
            ),
            onUnlocked = ::goHome,
            onPromptBiometric = ::promptBiometric,
            onUseRecovery = { useRecovery = true }
        )
    }
}

private data class LockCapabilities(
    val biometricEnabled: Boolean,
    val recoveryAvailable: Boolean
)

@Composable
private fun PinLockContent(
    viewModel: LockViewModel,
    capabilities: LockCapabilities,
    onUnlocked: () -> Unit,
    onPromptBiometric: () -> Unit,
    onUseRecovery: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            val remaining = viewModel.lockoutRemainingMillis()
            lockoutSeconds = (remaining + 999) / 1000
            if (remaining <= 0) break
            delay(1000)
        }
    }

    LaunchedEffect(input) {
        if (input.length == PIN_LENGTH) {
            if (viewModel.lockoutRemainingMillis() > 0) {
                input = ""
                return@LaunchedEffect
            }

            val pin = input
            input = ""
            viewModel.authenticatePin(pin) { result ->
                when (result) {
                    PinUnlockResult.SUCCESS -> onUnlocked()
                    PinUnlockResult.WRONG_PIN -> {
                        error = "Невірний PIN-код"
                        lockoutSeconds = (viewModel.lockoutRemainingMillis() + 999) / 1000
                    }
                    PinUnlockResult.DB_ERROR -> error = "Не вдалося відкрити базу даних"
                }
            }
        }
    }

    PinEntryScaffold(
        title = "Розблокуйте нотатки",
        subtitle = if (lockoutSeconds > 0) {
            "Забагато спроб. Спробуйте через ${lockoutSeconds}с"
        } else {
            "Введіть 6-значний PIN для доступу"
        },
        subtitleIsError = lockoutSeconds > 0,
        error = error
    ) {
        PinCodeWidget(
            pinInput = input,
            onPinChange = {
                if (input.length < PIN_LENGTH && lockoutSeconds <= 0L) input += it
            },
            onDelete = { if (input.isNotEmpty()) input = input.dropLast(1) }
        )

        LockAlternativeActions(
            capabilities = capabilities,
            onPromptBiometric = onPromptBiometric,
            onUseRecovery = onUseRecovery
        )
    }
}

@Composable
private fun LockAlternativeActions(
    capabilities: LockCapabilities,
    onPromptBiometric: () -> Unit,
    onUseRecovery: () -> Unit
) {
    if (capabilities.biometricEnabled) {
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onPromptBiometric) {
            Text("Використати біометрію")
        }
    }

    if (capabilities.recoveryAvailable) {
        TextButton(onClick = onUseRecovery) {
            Text("Відновити доступ за recovery seed")
        }
    }
}

@Composable
private fun SeedLockContent(
    viewModel: LockViewModel,
    onUnlocked: () -> Unit,
    onUsePin: () -> Unit
) {
    var phrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    PinEntryScaffold(
        title = "Recovery",
        subtitle = "Введіть 12-слівний recovery seed",
        subtitleIsError = false,
        error = error
    ) {
        OutlinedTextField(
            value = phrase,
            onValueChange = {
                phrase = it
                error = null
            },
            label = { Text("Recovery seed") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                viewModel.authenticateSeed(phrase) { result ->
                    when (result) {
                        SeedUnlockResult.SUCCESS -> onUnlocked()
                        SeedUnlockResult.WRONG_SEED -> error = "Невірний recovery seed"
                        SeedUnlockResult.DB_ERROR -> error = "Не вдалося відкрити базу даних"
                    }
                }
            },
            enabled = phrase.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Відновити доступ")
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onUsePin) {
            Text("Повернутися до PIN")
        }
    }
}
