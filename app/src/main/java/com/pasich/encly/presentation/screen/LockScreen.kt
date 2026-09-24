package com.pasich.encly.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.pasich.encly.R
import com.pasich.encly.core.security.PIN_LENGTH
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.PhraseInput
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.RelockReturn
import com.pasich.encly.presentation.screen.pincode.AuthLoading
import com.pasich.encly.presentation.screen.pincode.PinEntry
import com.pasich.encly.presentation.screen.pincode.PinEntryActions
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.LockViewModel
import com.pasich.encly.presentation.viewmodel.PinUnlockResult
import com.pasich.encly.presentation.viewmodel.SeedUnlockResult
import com.pasich.encly.ui.theme.EnclyTheme

@Composable
fun LockScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = hiltViewModel(),
) {
    val activity = LocalContext.current as? FragmentActivity
    val busy by viewModel.busy.collectAsState()
    // Above the loading view below: the forms' input and errors survive the credential check.
    val form = remember { LockFormState() }

    BackHandler(enabled = true) { }

    val biometricEnabled = remember {
        viewModel.biometricEnabled() && viewModel.biometricAvailable()
    }

    fun goHome() {
        // The note that was open when the app re-locked (see MainActivity) is opened again.
        val returnRoute = navController.currentBackStackEntry?.savedStateHandle?.get<String>(RelockReturn.RETURN_ROUTE)
        navController.navigate(NavRoutes.HomeRoute.name) {
            popUpTo(NavRoutes.LockRoute.name) { inclusive = true }
        }
        if (returnRoute != null) navController.navigate(returnRoute)
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

    Box(modifier = modifier.fillMaxSize()) {
        if (form.useRecovery) {
            SeedLockContent(
                form = form,
                busy = busy,
                authenticateSeed = viewModel::authenticateSeed,
                onUnlock = ::goToPinReset,
            )
        } else {
            PinLockContent(
                form = form,
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
            )
        }
        // Drawn over the forms rather than instead of them, so they keep their state.
        if (busy) AuthLoading(stringResource(R.string.lock_unlocking))
    }
}

private data class LockCapabilities(val biometricEnabled: Boolean, val recoveryAvailable: Boolean)

/** The LockViewModel operations the PIN form needs, so the ViewModel itself stays in [LockScreen]. */
private class PinAuth(
    val lockoutRemainingMillis: () -> Long,
    val authenticate: (pin: String, onResult: (PinUnlockResult) -> Unit) -> Unit,
)

@Composable
private fun PinLockContent(
    form: LockFormState,
    capabilities: LockCapabilities,
    pinAuth: PinAuth,
    onUnlock: () -> Unit,
    onPromptBiometric: () -> Unit,
) {
    PinLockoutTicker(form.lockedOut, pinAuth.lockoutRemainingMillis) { form.lockoutSeconds = it }
    PinAuthenticationEffect(form = form, pinAuth = pinAuth, onUnlock = onUnlock)

    val shownError = form.pinError
    PinEntryScaffold(
        title = stringResource(R.string.lock_title),
        // The sub-heading slot carries the lockout countdown or the last error.
        subtitle = when {
            form.lockedOut -> pinLockoutText(form.lockoutSeconds)
            shownError != null -> stringResource(shownError)
            else -> stringResource(R.string.lock_subtitle)
        },
        subtitleIsError = form.lockedOut || shownError != null,
    ) {
        PinEntry(
            entered = form.pin.length,
            error = shownError != null && form.pin.isEmpty(),
            shakeKey = form.shakeKey,
            enabled = !form.lockedOut,
            actions = PinEntryActions(
                onDigit = form::typeDigit,
                onBackspace = form::deleteDigit,
                onBiometric = onPromptBiometric.takeIf { capabilities.biometricEnabled },
            ),
        )
        if (capabilities.recoveryAvailable) RecoveryLink(onUseRecovery = { form.useRecovery = true })
    }
}

@Composable
private fun PinAuthenticationEffect(form: LockFormState, pinAuth: PinAuth, onUnlock: () -> Unit) {
    val currentOnUnlock by rememberUpdatedState(onUnlock)
    LaunchedEffect(form.pin) {
        if (form.pin.length != PIN_LENGTH) return@LaunchedEffect
        val pin = form.takePin()
        if (pinAuth.lockoutRemainingMillis() > 0) return@LaunchedEffect

        pinAuth.authenticate(pin) { result ->
            if (result == PinUnlockResult.SUCCESS) {
                currentOnUnlock()
            } else {
                form.onPinResult(result, pinAuth.lockoutRemainingMillis())
            }
        }
    }
}

@Composable
private fun SeedLockContent(
    form: LockFormState,
    busy: Boolean,
    authenticateSeed: (phrase: String, onResult: (SeedUnlockResult) -> Unit) -> Unit,
    onUnlock: () -> Unit,
) {
    // The loading view covers this form but not the keyboard: while the phrase is checked, the
    // field lets go of focus, so the keyboard closes and cannot type into the phrase meanwhile.
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(busy) {
        if (busy) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    PinEntryScaffold(
        title = stringResource(R.string.lock_recovery_title),
        subtitle = stringResource(R.string.lock_recovery_subtitle),
        icon = Lucide.KeyRound,
        // Pinned above the keyboard: typing the phrase must never hide the button.
        footer = {
            EnclyButton(
                text = stringResource(R.string.lock_recover_access),
                onClick = {
                    authenticateSeed(form.phrase) { result ->
                        if (result == SeedUnlockResult.SUCCESS) onUnlock() else form.onSeedResult(result)
                    }
                },
                enabled = form.phrase.isNotBlank() && !busy,
            )
            EnclyTextButton(
                text = stringResource(R.string.lock_back_to_pin),
                onClick = { form.useRecovery = false },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EnclyTheme.spacing.gutter)
                .padding(top = EnclyTheme.spacing.stepGap),
        ) {
            Text(
                text = stringResource(R.string.recovery_phrase_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            PhraseInput(
                value = form.phrase,
                onValueChange = form::editPhrase,
                placeholder = stringResource(R.string.onboarding_restore_phrase_placeholder),
                enabled = !busy,
                error = form.phraseError != null,
            )
            form.phraseError?.let { ErrorLine(stringResource(it)) }
        }
    }
}

/** "Forgot your PIN? Use your recovery phrase" under the keypad. */
@Composable
private fun RecoveryLink(onUseRecovery: () -> Unit) {
    EnclyTextButton(
        text = stringResource(R.string.lock_use_recovery_phrase),
        onClick = onUseRecovery,
        modifier = Modifier.padding(
            top = EnclyTheme.spacing.l,
            start = EnclyTheme.spacing.gutter,
            end = EnclyTheme.spacing.gutter,
        ),
    )
}

@Composable
private fun ErrorLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
