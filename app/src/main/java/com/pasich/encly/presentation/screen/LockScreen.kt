package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.pasich.encly.core.security.AuthStrategy
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.pincode.AuthLoading
import com.pasich.encly.presentation.screen.pincode.PinCodeWidget
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.viewmodel.LockViewModel
import com.pasich.encly.presentation.viewmodel.PinUnlockResult
import kotlinx.coroutines.delay

/**
 * App-unlock screen shown when a PIN-based lock is configured. Verifies the PIN
 * (with a progressive lockout) and optionally a biometric prompt, then unlocks the
 * encrypted database and navigates home. Verification and DB unlock run off the main
 * thread and show a loading state. It never accesses note data before unlock.
 */
@Composable
fun LockScreen(
    navController: NavHostController,
    viewModel: LockViewModel = hiltViewModel()
) {
    val activity = LocalContext.current as? FragmentActivity
    val busy by viewModel.busy.collectAsState()

    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableLongStateOf(0L) }

    val biometricEnabled = remember {
        viewModel.strategy() == AuthStrategy.PIN_BIOMETRIC &&
                viewModel.biometricEnabled() &&
                viewModel.biometricAvailable()
    }

    fun goHome() {
        navController.navigate(NavRoutes.HomeRoute.name) {
            popUpTo(NavRoutes.LockRoute.name) { inclusive = true }
        }
    }

    fun promptBiometric() {
        if (activity != null && viewModel.lockoutRemainingMillis() <= 0) {
            viewModel.authenticateBiometric(activity) { authed ->
                if (authed) viewModel.unlock { ok ->
                    if (ok) goHome() else error = "Не вдалося відкрити базу даних"
                }
            }
        }
    }

    // Live lockout countdown
    LaunchedEffect(Unit) {
        while (true) {
            val remaining = viewModel.lockoutRemainingMillis()
            lockoutSeconds = (remaining + 999) / 1000
            if (remaining <= 0) break
            delay(1000)
        }
    }

    // Auto-prompt biometric on first entry (if enabled and not locked out)
    LaunchedEffect(Unit) {
        if (biometricEnabled) promptBiometric()
    }

    // Verify once 4 digits are entered
    LaunchedEffect(input) {
        if (input.length == 4) {
            if (viewModel.lockoutRemainingMillis() > 0) {
                input = ""
                return@LaunchedEffect
            }
            val pin = input
            input = ""
            viewModel.authenticatePin(pin) { result ->
                when (result) {
                    PinUnlockResult.SUCCESS -> goHome()
                    PinUnlockResult.WRONG_PIN -> {
                        error = "Невірний PIN-код"
                        lockoutSeconds = (viewModel.lockoutRemainingMillis() + 999) / 1000
                    }

                    PinUnlockResult.DB_ERROR -> error = "Не вдалося відкрити базу даних"
                }
            }
        }
    }

    if (busy) {
        AuthLoading("Розблокування…")
        return
    }

    PinEntryScaffold(
        title = "Розблокуйте нотатки",
        subtitle = if (lockoutSeconds > 0) "Забагато спроб. Спробуйте через ${lockoutSeconds}с"
        else "Введіть PIN-код для доступу",
        subtitleIsError = lockoutSeconds > 0,
        error = error
    ) {
        PinCodeWidget(
            pinInput = input,
            onPinChange = { if (input.length < 4 && lockoutSeconds <= 0L) input += it },
            onDelete = { if (input.isNotEmpty()) input = input.dropLast(1) }
        )

        if (biometricEnabled && activity != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { promptBiometric() }) {
                Text("Використати біометрію")
            }
        }
    }
}
