package com.pasich.encly.presentation.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.core.security.PIN_LENGTH
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
    modifier: Modifier = Modifier,
    viewModel: AuthSetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val busy by viewModel.busy.collectAsState()

    var step by remember { mutableStateOf(SetupStep.CREATE_PIN) }
    var firstPin by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var restoreFailed by remember { mutableStateOf(false) }

    fun goHome() {
        navController.navigate(NavRoutes.HomeRoute.name) {
            popUpTo(NavRoutes.AuthSetupRoute.name) { inclusive = true }
        }
    }

    fun onFinished(result: AuthSetupViewModel.FinishResult) {
        restoreFailed = result.restoreFailed
        when {
            result.ok -> goHome()

            // Re-locked while in the background; MainActivity routes to the lock screen.
            result.backgrounded -> Unit

            // Nothing committed yet: the dialog below offers a retry or an empty vault.
            result.restoreFailed -> Unit

            else -> error = R.string.error_database_open
        }
    }

    fun finish() = viewModel.finishSetup(::onFinished)

    fun afterPinSet() {
        if (viewModel.biometricAvailable() && activity != null) step = SetupStep.BIOMETRIC else finish()
    }

    // PIN entry handling
    LaunchedEffect(input, step) {
        if (input.length == PIN_LENGTH && step != SetupStep.BIOMETRIC) {
            when (step) {
                SetupStep.CREATE_PIN -> {
                    firstPin = input
                    input = ""
                    error = null
                    step = SetupStep.CONFIRM_PIN
                }

                SetupStep.CONFIRM_PIN -> {
                    if (input != firstPin) {
                        error = R.string.pin_mismatch
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
                                error = R.string.pin_save_failed
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
        AuthLoading(stringResource(R.string.please_wait))
        return
    }

    if (restoreFailed) {
        RestoreFailedDialog(
            onRetry = {
                restoreFailed = false
                finish()
            },
            onSkip = {
                restoreFailed = false
                viewModel.skipRestore(::onFinished)
            },
        )
    }

    val title = when (step) {
        SetupStep.CREATE_PIN -> R.string.pin_create_title
        SetupStep.CONFIRM_PIN -> R.string.auth_setup_confirm_title
        SetupStep.BIOMETRIC -> R.string.auth_setup_biometric_title
    }
    val subtitle = when (step) {
        SetupStep.CREATE_PIN -> R.string.auth_setup_create_subtitle
        SetupStep.CONFIRM_PIN -> R.string.auth_setup_confirm_subtitle
        SetupStep.BIOMETRIC -> R.string.auth_setup_biometric_subtitle
    }

    PinEntryScaffold(
        modifier = modifier,
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        error = error?.let { stringResource(it) },
    ) {
        if (step == SetupStep.BIOMETRIC) {
            TextButton(onClick = {
                if (activity != null) viewModel.enableBiometric(activity) { ok -> if (ok) finish() }
            }) {
                Text(stringResource(R.string.auth_setup_retry_biometric))
            }
            TextButton(onClick = { finish() }) {
                Text(stringResource(R.string.auth_setup_continue_pin_only))
            }
        } else {
            PinCodeWidget(
                pinInput = input,
                onPinChange = { if (input.length < PIN_LENGTH) input += it },
                onDelete = { if (input.isNotEmpty()) input = input.dropLast(1) },
            )
        }
    }
}

/** The onboarding restore did not import; nothing is committed until the user chooses. */
@Composable
private fun RestoreFailedDialog(onRetry: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.auth_setup_restore_failed_title)) },
        text = { Text(stringResource(R.string.auth_setup_restore_failed)) },
        confirmButton = {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.auth_setup_restore_retry)) }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.auth_setup_restore_skip)) }
        },
    )
}
