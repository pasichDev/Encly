package com.pasich.encly.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.core.security.PIN_LENGTH
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.pincode.AuthLoading
import com.pasich.encly.presentation.screen.pincode.PinCodeWidget
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.lockoutSecondsLeft
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.LockViewModel
import com.pasich.encly.presentation.viewmodel.PinUnlockResult
import com.pasich.encly.presentation.viewmodel.SeedUnlockResult

@Composable
fun LockScreen(navController: NavHostController, viewModel: LockViewModel = hiltViewModel()) {
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

    // A recovery-phrase unlock means the PIN was forgotten: set a new one before going on.
    fun goToPinReset() {
        navController.navigate(NavRoutes.PinCodeConfig.name) {
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
        AuthLoading(stringResource(R.string.lock_unlocking))
        return
    }

    if (useRecovery) {
        SeedLockContent(
            authenticateSeed = viewModel::authenticateSeed,
            onUnlock = ::goToPinReset,
            onUsePin = { useRecovery = false },
        )
    } else {
        PinLockContent(
            capabilities = LockCapabilities(
                biometricEnabled = biometricEnabled && activity != null,
                recoveryAvailable = viewModel.recoveryAvailable(),
            ),
            pinAuth = PinAuth(
                lockoutRemainingMillis = viewModel::lockoutRemainingMillis,
                authenticate = viewModel::authenticatePin,
            ),
            onUnlock = ::goHome,
            onPromptBiometric = ::promptBiometric,
            onUseRecovery = { useRecovery = true },
        )
    }
}

private data class LockCapabilities(val biometricEnabled: Boolean, val recoveryAvailable: Boolean)

/** The LockViewModel operations the PIN form needs, so the ViewModel itself stays in [LockScreen]. */
private class PinAuth(
    val lockoutRemainingMillis: () -> Long,
    val authenticate: (pin: String, onResult: (PinUnlockResult) -> Unit) -> Unit,
)

private data class PinAuthCallbacks(
    val onInputConsumed: () -> Unit,
    val onUnlocked: () -> Unit,
    val onWrongPin: () -> Unit,
    val onDatabaseError: () -> Unit,
)

@Composable
private fun PinLockContent(
    capabilities: LockCapabilities,
    pinAuth: PinAuth,
    onUnlock: () -> Unit,
    onPromptBiometric: () -> Unit,
    onUseRecovery: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var lockoutSeconds by remember { mutableLongStateOf(0L) }

    PinLockoutTicker(lockoutSeconds > 0L, pinAuth.lockoutRemainingMillis) { lockoutSeconds = it }
    PinAuthenticationEffect(
        pinAuth = pinAuth,
        input = input,
        callbacks = PinAuthCallbacks(
            onInputConsumed = { input = "" },
            onUnlocked = onUnlock,
            onWrongPin = {
                error = R.string.lock_wrong_pin
                lockoutSeconds = lockoutSecondsLeft(pinAuth.lockoutRemainingMillis())
            },
            onDatabaseError = { error = R.string.error_database_open },
        ),
    )

    PinEntryScaffold(
        title = stringResource(R.string.lock_title),
        subtitle = lockSubtitle(lockoutSeconds),
        subtitleIsError = lockoutSeconds > 0,
        error = error?.let { stringResource(it) },
    ) {
        PinCodeWidget(
            pinInput = input,
            onPinChange = {
                if (input.length < PIN_LENGTH && lockoutSeconds <= 0L) input += it
            },
            onDelete = { if (input.isNotEmpty()) input = input.dropLast(1) },
        )
        LockAlternativeActions(
            capabilities = capabilities,
            onPromptBiometric = onPromptBiometric,
            onUseRecovery = onUseRecovery,
        )
    }
}

@Composable
private fun PinAuthenticationEffect(pinAuth: PinAuth, input: String, callbacks: PinAuthCallbacks) {
    LaunchedEffect(input) {
        if (input.length != PIN_LENGTH) return@LaunchedEffect
        if (pinAuth.lockoutRemainingMillis() > 0) {
            callbacks.onInputConsumed()
            return@LaunchedEffect
        }

        val pin = input
        callbacks.onInputConsumed()
        pinAuth.authenticate(pin) { result ->
            when (result) {
                PinUnlockResult.SUCCESS -> callbacks.onUnlocked()
                PinUnlockResult.WRONG_PIN -> callbacks.onWrongPin()
                PinUnlockResult.DB_ERROR -> callbacks.onDatabaseError()
                PinUnlockResult.BACKGROUNDED -> Unit
            }
        }
    }
}

@Composable
private fun lockSubtitle(lockoutSeconds: Long): String = if (lockoutSeconds > 0) {
    pinLockoutText(lockoutSeconds)
} else {
    stringResource(R.string.lock_subtitle)
}

@Composable
private fun LockAlternativeActions(
    capabilities: LockCapabilities,
    onPromptBiometric: () -> Unit,
    onUseRecovery: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (capabilities.biometricEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onPromptBiometric) {
                Text(stringResource(R.string.lock_use_biometric))
            }
        }

        if (capabilities.recoveryAvailable) {
            TextButton(onClick = onUseRecovery) {
                Text(stringResource(R.string.lock_use_recovery_phrase))
            }
        }
    }
}

@Composable
private fun SeedLockContent(
    authenticateSeed: (phrase: String, onResult: (SeedUnlockResult) -> Unit) -> Unit,
    onUnlock: () -> Unit,
    onUsePin: () -> Unit,
) {
    var phrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }

    PinEntryScaffold(
        title = stringResource(R.string.lock_recovery_title),
        subtitle = stringResource(R.string.lock_recovery_subtitle),
        subtitleIsError = false,
        error = error?.let { stringResource(it) },
    ) {
        OutlinedTextField(
            value = phrase,
            onValueChange = {
                phrase = it
                error = null
            },
            label = { Text(stringResource(R.string.recovery_phrase_label)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                authenticateSeed(phrase) { result ->
                    when (result) {
                        SeedUnlockResult.SUCCESS -> onUnlock()
                        SeedUnlockResult.WRONG_SEED -> error = R.string.lock_wrong_recovery_phrase
                        SeedUnlockResult.DB_ERROR -> error = R.string.error_database_open
                        SeedUnlockResult.BACKGROUNDED -> Unit
                    }
                }
            },
            enabled = phrase.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.lock_recover_access))
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onUsePin) {
            Text(stringResource(R.string.lock_back_to_pin))
        }
    }
}
