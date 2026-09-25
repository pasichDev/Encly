package com.pasich.encly.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.pasich.encly.R
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.presentation.designsystem.EnclyIconTile
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.pincode.PinBuffer
import com.pasich.encly.presentation.screen.pincode.PinEntry
import com.pasich.encly.presentation.screen.pincode.PinEntryActions
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.lockoutSecondsLeft
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.delay

private const val PIN_SUCCESS_DELAY_MS = 1_000L
private const val SUCCESS_SCALE_MS = 500

enum class PinAnimationState {
    Entering,
    SuccessAnimation,
}

/**
 * The three-step PIN change: current PIN (skipped after a recovery-phrase unlock), new PIN,
 * confirmation. Holds the screen state so the composable only renders it. The PINs are
 * CharArrays that are wiped once used, never Strings (see [PinBuffer]).
 */
private class PinChangeState(val isReset: Boolean) {
    var step by mutableIntStateOf(if (isReset) 1 else 0)

    /** The new PIN typed at step 1, until the confirmation is compared with it. */
    private var firstPin: CharArray? = null

    /** The digits of the current step. */
    val input = PinBuffer()
    var errorText by mutableStateOf<Int?>(null)
    var animationState by mutableStateOf(PinAnimationState.Entering)
    var lockoutSeconds by mutableLongStateOf(0L)

    /** Changes on every wrong entry, so the dots shake again. */
    var shakeKey by mutableIntStateOf(0)

    /** Digits are refused while the current PIN is locked out. */
    val keysEnabled: Boolean get() = !(step == 0 && lockoutSeconds > 0L)

    /** A digit on the keypad; refused during a lockout. */
    fun typeDigit(digit: Int) {
        if (keysEnabled) input.add(digit)
    }

    /** The PIN of the current step is complete: check it, keep it, or compare it. */
    fun onPinComplete(viewModel: SecuritySettingsViewModel) {
        // The ViewModel wipes what it is given; everything else is wiped here.
        val pin = input.take()
        when (step) {
            0 -> viewModel.verifyCurrentPin(pin) { ok -> onCurrentPinChecked(ok, viewModel) }

            1 -> {
                firstPin?.let(SensitiveDataCleaner::clear)
                firstPin = pin
                errorText = null
                step = 2
            }

            else -> confirmNewPin(pin, viewModel)
        }
    }

    private fun confirmNewPin(pin: CharArray, viewModel: SecuritySettingsViewModel) {
        val first = firstPin
        firstPin = null
        val matches = first != null && pin.contentEquals(first)
        first?.let(SensitiveDataCleaner::clear)
        if (matches) {
            viewModel.activationPinAuth(pin) { ok ->
                if (ok) {
                    animationState = PinAnimationState.SuccessAnimation
                } else {
                    restartNewPin(R.string.pin_update_failed)
                }
            }
        } else {
            SensitiveDataCleaner.clear(pin)
            restartNewPin(R.string.pin_mismatch_retry)
        }
    }

    /** Typed or kept digits do not outlive the screen. */
    fun clear() {
        input.clear()
        firstPin?.let(SensitiveDataCleaner::clear)
        firstPin = null
    }

    private fun onCurrentPinChecked(ok: Boolean, viewModel: SecuritySettingsViewModel) {
        val lockout = lockoutSecondsLeft(viewModel.pinLockoutRemainingMillis())
        errorText = null
        when {
            ok -> step = 1

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

    private fun restartNewPin(@StringRes error: Int) {
        errorText = error
        shakeKey++
        firstPin?.let(SensitiveDataCleaner::clear)
        firstPin = null
        step = 1
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PinCodeConfigScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    securityViewModel: SecuritySettingsViewModel = hiltViewModel(),
) {
    // After a recovery-phrase unlock the old PIN is forgotten: start at "new PIN", and do not
    // let the user leave without one.
    val pinState = remember { PinChangeState(isReset = !securityViewModel.requiresCurrentPin()) }

    BackHandler(enabled = pinState.isReset) { }
    PinLockoutTicker(pinState.lockoutSeconds > 0L, securityViewModel::pinLockoutRemainingMillis) {
        pinState.lockoutSeconds = it
    }

    LaunchedEffect(pinState.input.length) {
        if (pinState.input.isFull && pinState.animationState == PinAnimationState.Entering) {
            pinState.onPinComplete(securityViewModel)
        }
    }
    DisposableEffect(pinState) { onDispose { pinState.clear() } }

    LaunchedEffect(pinState.animationState) {
        if (pinState.animationState == PinAnimationState.SuccessAnimation) {
            delay(PIN_SUCCESS_DELAY_MS)
            navController.finishPinChange(pinState.isReset)
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // From Settings there is a way back; a forgotten-PIN reset must end with a new PIN.
            if (!pinState.isReset) {
                EnclyTopBar(
                    title = stringResource(R.string.pin_change_bar_title),
                    onBack = { navController.popBackStack() },
                )
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (pinState.animationState == PinAnimationState.Entering) {
                    AnimatedContent(
                        targetState = pinState.step,
                        transitionSpec = {
                            (slideInHorizontally { width -> width } + fadeIn())
                                .togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                        },
                        label = "StepAnimation",
                    ) { currentStep ->
                        MainPinContent(
                            text = pinStepText(currentStep, pinState),
                            entered = pinState.input.length,
                            entry = PinEntryState(
                                enabled = pinState.keysEnabled,
                                shakeKey = pinState.shakeKey,
                                compact = !pinState.isReset,
                            ),
                            onInput = pinState::typeDigit,
                            onDelete = pinState.input::deleteLast,
                        )
                    }
                } else {
                    SuccessAnimation()
                }
            }
        }
    }
}

/** A reset (forgotten PIN) ends in the app; a change from Settings goes back there. */
private fun NavHostController.finishPinChange(isReset: Boolean) {
    if (isReset) {
        navigate(NavRoutes.HomeRoute.name) {
            popUpTo(NavRoutes.PinCodeConfig.name) { inclusive = true }
        }
    } else {
        popBackStack()
    }
}

@Composable
private fun pinStepText(step: Int, state: PinChangeState): PinStepText {
    val lockout = state.lockoutSeconds.takeIf { it > 0L && step == 0 }?.let { pinLockoutText(it) }
    return PinStepText(
        title = pinStepTitle(step),
        subtitle = if (state.isReset && step == 1) R.string.pin_reset_subtitle else pinStepSubtitle(step),
        message = lockout ?: state.errorText?.let { stringResource(it) },
    )
}

@StringRes
private fun pinStepTitle(step: Int): Int = when (step) {
    0 -> R.string.pin_change_current_title
    1 -> R.string.pin_create_title
    else -> R.string.pin_change_confirm_title
}

@StringRes
private fun pinStepSubtitle(step: Int): Int = when (step) {
    0 -> R.string.pin_change_current_subtitle
    1 -> R.string.pin_change_new_subtitle
    else -> R.string.pin_change_confirm_subtitle
}

/** What a PIN step shows: its title and subtitle, and an error or lockout message. */
data class PinStepText(@param:StringRes val title: Int, @param:StringRes val subtitle: Int, val message: String?)

/** How the keypad of a PIN step behaves: off during a lockout, the shake, a compact top. */
private class PinEntryState(val enabled: Boolean, val shakeKey: Int, val compact: Boolean)

@Composable
private fun MainPinContent(
    text: PinStepText,
    entered: Int,
    entry: PinEntryState,
    onInput: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val message = text.message
    PinEntryScaffold(
        title = stringResource(text.title),
        // An error or the lockout countdown takes the sub-heading slot, as on the lock screen.
        subtitle = message ?: stringResource(text.subtitle),
        subtitleIsError = message != null,
        compact = entry.compact,
    ) {
        PinEntry(
            entered = entered,
            error = message != null && entered == 0,
            shakeKey = entry.shakeKey,
            enabled = entry.enabled,
            actions = PinEntryActions(
                onDigit = onInput,
                onBackspace = onDelete,
            ),
        )
    }
}

@Composable
private fun SuccessAnimation() {
    val scale = remember { Animatable(0f) }
    val description = stringResource(R.string.pin_changed)

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = SUCCESS_SCALE_MS, easing = FastOutSlowInEasing),
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.m, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        EnclyIconTile(icon = EnclyIcons.Check, modifier = Modifier.scale(scale.value))
        Text(
            text = description,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alpha(scale.value.coerceIn(0f, 1f)),
        )
    }
}
