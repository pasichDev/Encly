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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.pasich.encly.R
import com.pasich.encly.core.security.PIN_LENGTH
import com.pasich.encly.presentation.designsystem.EnclyIconTile
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.screen.pincode.PinEntry
import com.pasich.encly.presentation.screen.pincode.PinEntryActions
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.lockoutSecondsLeft
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import kotlinx.coroutines.delay

private const val PIN_SUCCESS_DELAY_MS = 600L
private const val SUCCESS_SCALE_MS = 500

enum class PinAnimationState {
    Entering,
    SuccessAnimation,
}

/**
 * The three-step PIN change: current PIN (skipped after a recovery-phrase unlock), new PIN,
 * confirmation. Holds the screen state so the composable only renders it.
 */
private class PinChangeState(val isReset: Boolean) {
    var step by mutableIntStateOf(if (isReset) 1 else 0)
    var firstPin by mutableStateOf("")
    var currentInput by mutableStateOf("")
    var errorText by mutableStateOf<Int?>(null)
    var animationState by mutableStateOf(PinAnimationState.Entering)
    var lockoutSeconds by mutableLongStateOf(0L)

    fun onPinComplete(viewModel: SecuritySettingsViewModel) {
        val pin = currentInput
        currentInput = ""
        when (step) {
            0 -> viewModel.verifyCurrentPin(pin) { ok -> onCurrentPinChecked(ok, viewModel) }

            1 -> {
                firstPin = pin
                errorText = null
                step = 2
            }

            else -> if (pin == firstPin) {
                viewModel.activationPinAuth(pin) { ok ->
                    if (ok) {
                        animationState = PinAnimationState.SuccessAnimation
                    } else {
                        restartNewPin(R.string.pin_update_failed)
                    }
                }
            } else {
                restartNewPin(R.string.pin_mismatch_retry)
            }
        }
    }

    private fun onCurrentPinChecked(ok: Boolean, viewModel: SecuritySettingsViewModel) {
        val lockout = lockoutSecondsLeft(viewModel.pinLockoutRemainingMillis())
        errorText = null
        when {
            ok -> step = 1

            // A locked-out PIN is refused even when right: say so, not "wrong PIN".
            lockout > 0L -> lockoutSeconds = lockout

            else -> errorText = R.string.pin_current_wrong
        }
    }

    private fun restartNewPin(@StringRes error: Int) {
        errorText = error
        firstPin = ""
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

    LaunchedEffect(pinState.currentInput) {
        if (pinState.currentInput.length == PIN_LENGTH && pinState.animationState == PinAnimationState.Entering) {
            pinState.onPinComplete(securityViewModel)
        }
    }

    LaunchedEffect(pinState.animationState) {
        if (pinState.animationState == PinAnimationState.SuccessAnimation) {
            delay(PIN_SUCCESS_DELAY_MS)
            navController.finishPinChange(pinState.isReset)
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (pinState.animationState == PinAnimationState.Entering) {
                AnimatedContent(
                    targetState = pinState.step,
                    transitionSpec = {
                        (
                            slideInHorizontally { width ->
                                width
                            } + fadeIn()
                            ).togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                    },
                    label = "StepAnimation",
                ) { currentStep ->
                    MainPinContent(
                        text = pinStepText(currentStep, pinState),
                        currentInput = pinState.currentInput,
                        onInput = {
                            if (pinState.currentInput.length < PIN_LENGTH) pinState.currentInput += it
                        },
                        onDelete = { pinState.currentInput = pinState.currentInput.dropLast(1) },
                    )
                }
            } else {
                SuccessAnimation()
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

@Composable
private fun MainPinContent(text: PinStepText, currentInput: String, onInput: (String) -> Unit, onDelete: () -> Unit) {
    val message = text.message
    PinEntryScaffold(
        title = stringResource(text.title),
        // An error or the lockout countdown takes the sub-heading slot, as on the lock screen.
        subtitle = message ?: stringResource(text.subtitle),
        subtitleIsError = message != null,
    ) {
        PinEntry(
            entered = currentInput.length,
            error = message != null && currentInput.isEmpty(),
            actions = PinEntryActions(
                onDigit = { onInput(it.toString()) },
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

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
    ) {
        EnclyIconTile(icon = Lucide.Check, modifier = Modifier.scale(scale.value))
    }
}
