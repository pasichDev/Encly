package com.pasich.encly.presentation.screen.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.core.security.WipePinChange
import com.pasich.encly.presentation.designsystem.CalloutTone
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclySnackbarHost
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.screen.pincode.PinBuffer
import com.pasich.encly.presentation.screen.pincode.PinEntry
import com.pasich.encly.presentation.screen.pincode.PinEntryActions
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.lockoutSecondsLeft
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.launch

private enum class WipePinStep { VERIFY, OPTIONS, NEW, CONFIRM }

/**
 * Settings → Security → Wipe PIN. The PIN comes first; then the page explains what a wipe PIN
 * does and offers to set one (replacing any earlier one) or turn it off. It never says whether
 * one is set: only the wipe PIN itself could tell (see AuthenticationManager). The PINs are
 * CharArrays that are wiped once used, never Strings (see [PinBuffer]).
 */
private class WipePinState {
    var step by mutableStateOf(WipePinStep.VERIFY)

    /** The wipe PIN typed at [WipePinStep.NEW], until the confirmation is compared with it. */
    private var firstPin: CharArray? = null

    val input = PinBuffer()
    var errorText by mutableStateOf<Int?>(null)
    var lockoutSeconds by mutableLongStateOf(0L)

    /** Changes on every wrong entry, so the dots shake again. */
    var shakeKey by mutableIntStateOf(0)

    /** A finished action to announce on the options page. */
    var message by mutableStateOf<Int?>(null)

    val keysEnabled: Boolean get() = !(step == WipePinStep.VERIFY && lockoutSeconds > 0L)

    fun typeDigit(digit: Int) {
        if (keysEnabled) input.add(digit)
    }

    /** The PIN of the current step is complete: check it, keep it, or compare it. */
    fun onPinComplete(viewModel: SecuritySettingsViewModel) {
        // The ViewModel wipes what it is given; everything else is wiped here.
        val pin = input.take()
        when (step) {
            WipePinStep.VERIFY -> viewModel.verifyCurrentPin(pin) { ok ->
                onCurrentPinChecked(ok, viewModel.pinLockoutRemainingMillis())
            }

            WipePinStep.NEW -> {
                firstPin?.let(SensitiveDataCleaner::clear)
                firstPin = pin
                errorText = null
                step = WipePinStep.CONFIRM
            }

            WipePinStep.CONFIRM -> confirm(pin, viewModel)

            WipePinStep.OPTIONS -> SensitiveDataCleaner.clear(pin)
        }
    }

    fun startNew() {
        errorText = null
        step = WipePinStep.NEW
    }

    /** Back from a PIN step to the options; false when there is nothing to go back to here. */
    fun back(): Boolean {
        if (step != WipePinStep.NEW && step != WipePinStep.CONFIRM) return false
        clear()
        errorText = null
        step = WipePinStep.OPTIONS
        return true
    }

    /** Typed or kept digits do not outlive the screen. */
    fun clear() {
        input.clear()
        firstPin?.let(SensitiveDataCleaner::clear)
        firstPin = null
    }

    private fun onCurrentPinChecked(ok: Boolean, lockoutMillis: Long) {
        val lockout = lockoutSecondsLeft(lockoutMillis)
        errorText = null
        when {
            ok -> step = WipePinStep.OPTIONS

            // A locked-out PIN is refused even when right: say so, not "wrong PIN".
            lockout > 0L -> {
                lockoutSeconds = lockout
                shakeKey++
            }

            else -> {
                errorText = R.string.pin_current_wrong
                shakeKey++
            }
        }
    }

    private fun confirm(pin: CharArray, viewModel: SecuritySettingsViewModel) {
        val first = firstPin
        firstPin = null
        val matches = first != null && pin.contentEquals(first)
        first?.let(SensitiveDataCleaner::clear)
        if (!matches) {
            SensitiveDataCleaner.clear(pin)
            restartNew(R.string.pin_mismatch_retry)
            return
        }
        viewModel.setWipePin(pin) { result ->
            when (result) {
                WipePinChange.SET -> {
                    message = R.string.wipe_pin_saved
                    step = WipePinStep.OPTIONS
                }

                WipePinChange.SAME_AS_PIN -> restartNew(R.string.wipe_pin_same_as_pin)

                WipePinChange.FAILED -> restartNew(R.string.wipe_pin_failed)
            }
        }
    }

    private fun restartNew(@StringRes error: Int) {
        errorText = error
        shakeKey++
        step = WipePinStep.NEW
    }
}

@Composable
fun WipePinScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    securityViewModel: SecuritySettingsViewModel = hiltViewModel(),
) {
    val state = remember { WipePinState() }
    val securityState by securityViewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = state.step == WipePinStep.NEW || state.step == WipePinStep.CONFIRM) { state.back() }
    PinLockoutTicker(state.lockoutSeconds > 0L, securityViewModel::pinLockoutRemainingMillis) {
        state.lockoutSeconds = it
    }
    LaunchedEffect(state.input.length) {
        if (state.input.isFull) state.onPinComplete(securityViewModel)
    }
    DisposableEffect(state) { onDispose { state.clear() } }
    WipePinMessageEffect(state.message, snackbarHostState, onShow = { state.message = null })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(
                title = stringResource(R.string.wipe_pin_title),
                onBack = { if (!state.back()) navController.popBackStack() },
            )
        },
        snackbarHost = { EnclySnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        if (state.step == WipePinStep.OPTIONS) {
            WipePinOptions(
                info = WipePinInfo(
                    turnedOff = securityState.wipePinTurnedOff,
                    biometricOn = securityState.biometricEnable,
                ),
                actions = WipePinActions(
                    onSet = state::startNew,
                    onRemove = {
                        securityViewModel.removeWipePin { ok ->
                            state.message = if (ok) R.string.wipe_pin_removed else R.string.wipe_pin_failed
                        }
                    },
                    onBiometricOff = {
                        if (activity != null) securityViewModel.toggleBiometric(activity, enable = false)
                    },
                ),
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            WipePinEntry(
                text = wipePinStepText(state),
                entered = state.input.length,
                entry = WipePinEntryState(enabled = state.keysEnabled, shakeKey = state.shakeKey),
                actions = PinEntryActions(onDigit = state::typeDigit, onBackspace = state.input::deleteLast),
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}

/** Shows [message] once as a snackbar; [onShow] clears it. */
@Composable
private fun WipePinMessageEffect(@StringRes message: Int?, snackbarHostState: SnackbarHostState, onShow: () -> Unit) {
    val context = LocalContext.current
    val currentOnShow by rememberUpdatedState(onShow)
    // Clearing the message restarts this effect, which must not cancel the snackbar it opened.
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(message) {
        val id = message ?: return@LaunchedEffect
        currentOnShow()
        snackbarScope.launch { snackbarHostState.showSnackbar(context.getString(id)) }
    }
}

/** What the options page shows besides the actions. */
private class WipePinInfo(val turnedOff: Boolean, val biometricOn: Boolean)

private class WipePinActions(val onSet: () -> Unit, val onRemove: () -> Unit, val onBiometricOff: () -> Unit)

@Composable
private fun WipePinOptions(info: WipePinInfo, actions: WipePinActions, modifier: Modifier = Modifier) {
    val spacing = EnclyTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.m),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.gutter, vertical = spacing.s),
    ) {
        if (info.turnedOff) {
            EnclyCallout(text = stringResource(R.string.wipe_pin_turned_off), tone = CalloutTone.WARNING)
        }
        Text(
            text = stringResource(R.string.wipe_pin_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        EnclyCallout(text = stringResource(R.string.wipe_pin_backups))
        if (info.biometricOn) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                EnclyCallout(text = stringResource(R.string.wipe_pin_biometric_warning), tone = CalloutTone.WARNING)
                EnclyTextButton(
                    text = stringResource(R.string.wipe_pin_biometric_off),
                    onClick = actions.onBiometricOff,
                )
            }
        }
        Text(
            text = stringResource(R.string.wipe_pin_limits),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            modifier = Modifier.fillMaxWidth().padding(top = spacing.s),
        ) {
            EnclyButton(text = stringResource(R.string.wipe_pin_set), onClick = actions.onSet)
            EnclyTextButton(
                text = stringResource(R.string.wipe_pin_remove),
                onClick = actions.onRemove,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A PIN step's title and subtitle, and an error or lockout message. */
private class WipePinStepText(@param:StringRes val title: Int, @param:StringRes val subtitle: Int, val message: String?)

/** How the keypad of a PIN step behaves: off during a lockout, and the shake. */
private class WipePinEntryState(val enabled: Boolean, val shakeKey: Int)

@Composable
private fun wipePinStepText(state: WipePinState): WipePinStepText {
    val lockout = state.lockoutSeconds
        .takeIf { it > 0L && state.step == WipePinStep.VERIFY }
        ?.let { pinLockoutText(it) }
    return when (state.step) {
        WipePinStep.VERIFY -> WipePinStepText(
            title = R.string.pin_change_current_title,
            subtitle = R.string.wipe_pin_current_subtitle,
            message = lockout ?: state.errorText?.let { stringResource(it) },
        )

        WipePinStep.CONFIRM -> WipePinStepText(
            title = R.string.wipe_pin_confirm_title,
            subtitle = R.string.wipe_pin_confirm_subtitle,
            message = state.errorText?.let { stringResource(it) },
        )

        else -> WipePinStepText(
            title = R.string.wipe_pin_new_title,
            subtitle = R.string.wipe_pin_new_subtitle,
            message = state.errorText?.let { stringResource(it) },
        )
    }
}

@Composable
private fun WipePinEntry(
    text: WipePinStepText,
    entered: Int,
    entry: WipePinEntryState,
    actions: PinEntryActions,
    modifier: Modifier = Modifier,
) {
    val message = text.message
    PinEntryScaffold(
        title = stringResource(text.title),
        // An error or the lockout countdown takes the sub-heading slot, as on the lock screen.
        subtitle = message ?: stringResource(text.subtitle),
        subtitleIsError = message != null,
        compact = true,
        modifier = modifier,
    ) {
        PinEntry(
            entered = entered,
            error = message != null && entered == 0,
            shakeKey = entry.shakeKey,
            enabled = entry.enabled,
            actions = actions,
        )
    }
}
