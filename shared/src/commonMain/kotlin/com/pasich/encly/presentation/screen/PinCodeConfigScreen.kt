package com.pasich.encly.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.passphrase_label
import com.pasich.encly.core.resources.pin_change_bar_title
import com.pasich.encly.core.resources.pin_change_confirm_subtitle
import com.pasich.encly.core.resources.pin_change_confirm_title
import com.pasich.encly.core.resources.pin_change_current_subtitle
import com.pasich.encly.core.resources.pin_change_current_title
import com.pasich.encly.core.resources.pin_change_new_subtitle
import com.pasich.encly.core.resources.pin_changed
import com.pasich.encly.core.resources.pin_create_title
import com.pasich.encly.core.resources.pin_in_use
import com.pasich.encly.core.resources.pin_mismatch_retry
import com.pasich.encly.core.resources.pin_reset_subtitle
import com.pasich.encly.core.resources.pin_update_failed
import com.pasich.encly.core.security.PinChange
import com.pasich.encly.core.security.PinCheck
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.platform.LocalSessionSharedAcrossWindows
import com.pasich.encly.platform.secretString
import com.pasich.encly.presentation.designsystem.EnclyIconTile
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.designsystem.authCardApplies
import com.pasich.encly.presentation.designsystem.authSurfaceColor
import com.pasich.encly.presentation.effects.StepFade
import com.pasich.encly.presentation.effects.screenFade
import com.pasich.encly.presentation.navigation.NavRoutes
import com.pasich.encly.presentation.navigation.popBack
import com.pasich.encly.presentation.screen.pincode.PassphraseInput
import com.pasich.encly.presentation.screen.pincode.PassphraseStep
import com.pasich.encly.presentation.screen.pincode.PassphraseStepActions
import com.pasich.encly.presentation.screen.pincode.PassphraseStepStyle
import com.pasich.encly.presentation.screen.pincode.PassphraseStepText
import com.pasich.encly.presentation.screen.pincode.PinBuffer
import com.pasich.encly.presentation.screen.pincode.PinEntry
import com.pasich.encly.presentation.screen.pincode.PinEntryActions
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.PinSubmit
import com.pasich.encly.presentation.screen.pincode.clearWhenVaultLocks
import com.pasich.encly.presentation.screen.pincode.lockoutSecondsLeft
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import com.pasich.encly.presentation.viewmodel.keyStoreMessage
import com.pasich.encly.presentation.viewmodel.pinCheckError
import com.pasich.encly.ui.theme.EnclyMotion
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val PIN_SUCCESS_DELAY_MS = 1_000L
private const val SUCCESS_SCALE_MS = 500

enum class PinAnimationState {
    Entering,
    SuccessAnimation,
}

/**
 * The three-step PIN change: current PIN (skipped after a recovery-phrase unlock), new PIN,
 * confirmation. Holds the screen state so the composable only renders it. The PINs are
 * CharArrays that are wiped once used, never Strings (see [PinBuffer]). Where the secret is a passphrase ([kind]) the
 * same three steps run with a field in place of the keypad, and the new one is checked against the policy first.
 */
private class PinChangeState(
    val isReset: Boolean,
    val kind: SecretKind = SecretKind.PIN,
    maxPassphraseUnits: Int = PassphraseInput.MAX_TYPED_CHARS,
) {
    var step by mutableIntStateOf(if (isReset) 1 else 0)

    /** The new PIN typed at step 1, until the confirmation is compared with it. */
    private var firstPin: CharArray? = null

    /** The digits of the current step. */
    val input = PinBuffer()

    /** The passphrase of the current step, where the secret is one. */
    val passphrase = PassphraseInput(maxPassphraseUnits)

    /** Counts the locks of the vault: the answer of a check that began before one is dropped (see [lock]). */
    private var generation = 0

    /** A secret is being checked or saved: nothing more is taken until the answer is here. */
    var busy by mutableStateOf(false)
    val isPassphrase: Boolean get() = kind == SecretKind.PASSWORD

    /** Something complete enough to send is typed: a PIN of enough digits, or any passphrase (the policy decides). */
    val canSubmit: Boolean get() = if (isPassphrase) passphrase.canSubmit else input.canSubmit
    var errorText by mutableStateOf<StringResource?>(null)
    var animationState by mutableStateOf(PinAnimationState.Entering)
    var lockoutSeconds by mutableLongStateOf(0L)

    /** Changes on every wrong entry, so the dots shake again. */
    var shakeKey by mutableIntStateOf(0)

    /** Nothing is taken while the current secret is locked out, or while one is checked or saved. */
    val keysEnabled: Boolean get() = !(step == 0 && lockoutSeconds > 0L) && !busy

    /** A digit on the keypad; refused during a lockout. */
    fun typeDigit(digit: Int) {
        if (keysEnabled) input.add(digit)
    }

    /** The PIN of the current step is complete: check it, keep it, or compare it. */
    fun onPinComplete(viewModel: SecuritySettingsViewModel) {
        if (busy || refusedAsNew(viewModel)) return
        // The ViewModel wipes what it is given; everything else is wiped here.
        val pin = if (isPassphrase) passphrase.take() else input.take()
        when (step) {
            0 -> {
                busy = true
                val asked = generation
                viewModel.verifyCurrentPin(pin) { check ->
                    // The vault locked while this was being checked: an answer that comes after it opens nothing.
                    if (asked == generation) onCurrentPinChecked(check, viewModel)
                }
            }

            1 -> {
                firstPin?.let(SensitiveDataCleaner::clear)
                firstPin = pin
                errorText = null
                step = 2
            }

            // A confirmation with nothing to compare it to is no step to be on: start the choice again.
            else -> if (firstPin == null) {
                SensitiveDataCleaner.clear(pin)
                step = 1
            } else {
                confirmNewPin(pin, viewModel)
            }
        }
    }

    /** The vault locked: what is typed and kept is dropped, and the flow goes back to where it asks again. */
    fun lock() {
        clear()
        generation++
        busy = false
        val start = if (isReset) 1 else 0
        if (step != start) {
            errorText = null
            step = start
        }
    }

    /** A passphrase the policy refuses as the new one: say why and leave the text, so it can be fixed by editing. */
    private fun refusedAsNew(viewModel: SecuritySettingsViewModel): Boolean {
        val refusal = if (isPassphrase && step == 1) {
            val typed = passphrase.peek()
            try {
                viewModel.refusalOf(typed)
            } finally {
                SensitiveDataCleaner.clear(typed)
            }
        } else {
            null
        }
        refusal?.let {
            errorText = it.message
            shakeKey++
        }
        return refusal != null
    }

    /** "Suggest a passphrase" on the new-passphrase step: puts the suggestion, in clear, in the field. */
    fun suggest(viewModel: SecuritySettingsViewModel) {
        val suggestion = viewModel.suggestSecret() ?: return
        passphrase.fill(suggestion)
        SensitiveDataCleaner.clear(suggestion)
        errorText = null
    }

    private fun confirmNewPin(pin: CharArray, viewModel: SecuritySettingsViewModel) {
        val first = firstPin
        firstPin = null
        val matches = first != null && viewModel.sameSecret(first, pin)
        first?.let(SensitiveDataCleaner::clear)
        if (matches) {
            busy = true
            viewModel.activationPinAuth(pin) { result ->
                busy = false
                when (result) {
                    PinChange.SET -> animationState = PinAnimationState.SuccessAnimation

                    PinChange.IN_USE -> restartNewPin(Res.string.pin_in_use)

                    PinChange.FAILED -> restartNewPin(Res.string.pin_update_failed)

                    PinChange.KEY_STORE_UNAVAILABLE ->
                        restartNewPin(keyStoreMessage(viewModel.keyStoreProblem()))
                }
            }
        } else {
            SensitiveDataCleaner.clear(pin)
            restartNewPin(Res.string.pin_mismatch_retry)
        }
    }

    /** Typed or kept digits do not outlive the screen. */
    fun clear() {
        input.clear()
        passphrase.clear()
        firstPin?.let(SensitiveDataCleaner::clear)
        firstPin = null
    }

    private fun onCurrentPinChecked(check: PinCheck, viewModel: SecuritySettingsViewModel) {
        busy = false
        val lockout = lockoutSecondsLeft(viewModel.pinLockoutRemainingMillis())
        errorText = null
        when {
            check == PinCheck.OK -> step = 1

            // A locked-out PIN is refused even when right: say so, not "wrong PIN".
            lockout > 0L -> {
                lockoutSeconds = lockout
                shakeKey++
            }

            // A key store that did not answer says what it is missing; only a wrong secret says "wrong".
            else -> {
                errorText = pinCheckError(check, viewModel.keyStoreProblem())
                shakeKey++
            }
        }
    }

    private fun restartNewPin(error: StringResource) {
        errorText = error
        shakeKey++
        firstPin?.let(SensitiveDataCleaner::clear)
        firstPin = null
        step = 1
    }

    companion object {
        /** The change for what the ViewModel says: a reset or a change, the kind of the secret and its cap. */
        fun of(viewModel: SecuritySettingsViewModel) = PinChangeState(
            isReset = !viewModel.requiresCurrentPin(),
            kind = viewModel.secretKind,
            maxPassphraseUnits = viewModel.secretMaxUnits,
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PinCodeConfigScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    securityViewModel: SecuritySettingsViewModel = metroViewModel(),
) {
    // After a recovery-phrase unlock the old PIN is forgotten: start at "new PIN", and do not
    // let the user leave without one.
    val pinState = remember { PinChangeState.of(securityViewModel) }
    val locked = clearWhenVaultLocks { pinState.lock() }

    BackHandler(enabled = pinState.isReset) { }
    if (pinState.isReset && LocalSessionSharedAcrossWindows.current) {
        LeaveWhenAnotherWindowSetThePin(pinState, securityViewModel::requiresCurrentPin, navController)
    }
    PinLockoutTicker(pinState.lockoutSeconds > 0L, securityViewModel::pinLockoutRemainingMillis) {
        pinState.lockoutSeconds = it
    }

    FinishWhenSet(pinState, navController)

    // A forced reset on a desktop window is drawn in the card of the first-run pages, as high as its step.
    val card = pinState.isReset && authCardApplies()
    Surface(
        modifier = if (card) modifier.fillMaxWidth() else modifier.fillMaxSize(),
        color = authSurfaceColor(card),
    ) {
        Column(modifier = (if (card) Modifier.fillMaxWidth() else Modifier.fillMaxSize()).statusBarsPadding()) {
            // From Settings there is a way back; a forgotten-PIN reset must end with a new PIN.
            if (!pinState.isReset) {
                EnclyTopBar(
                    title = secretString(pinState.kind, Res.string.pin_change_bar_title),
                    onBack = { navController.popBack() },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (card) Modifier.weight(1f, fill = false) else Modifier.weight(1f)),
                contentAlignment = Alignment.Center,
            ) {
                if (pinState.animationState == PinAnimationState.Entering) {
                    AnimatedContent(
                        targetState = pinState.step,
                        transitionSpec = {
                            slideIntoContainer(SlideDirection.Start, EnclyMotion.long())
                                .togetherWith(slideOutOfContainer(SlideDirection.Start, EnclyMotion.long()))
                        },
                        label = "StepAnimation",
                    ) { currentStep ->
                        // Drawn per draw call: fadeIn/fadeOut would put the step in an offscreen layer.
                        Box(Modifier.screenFade(this, enter = { StepFade.In }, exit = { StepFade.Out })) {
                            PinStepContent(
                                step = currentStep,
                                pinState = pinState,
                                actions = PinStepActions(
                                    locked = locked,
                                    inCard = card,
                                    onSubmitPin = { pinState.onPinComplete(securityViewModel) },
                                    onSuggest = { pinState.suggest(securityViewModel) },
                                ),
                            )
                        }
                    }
                } else {
                    SuccessAnimation(pinState.kind, card)
                }
            }
        }
    }
}

/** Typed digits do not outlive the screen, and a new PIN that was set is shown for a moment before the way on. */
@Composable
private fun FinishWhenSet(pinState: PinChangeState, navController: NavHostController) {
    DisposableEffect(pinState) { onDispose { pinState.clear() } }

    LaunchedEffect(pinState.animationState) {
        if (pinState.animationState == PinAnimationState.SuccessAnimation) {
            delay(PIN_SUCCESS_DELAY_MS)
            navController.finishPinChange(pinState.isReset)
        }
    }
}

/** What a step of the change does: send what is typed, and (for a passphrase) suggest one. */
private class PinStepActions(
    val locked: Boolean,
    val onSubmitPin: () -> Unit,
    val onSuggest: () -> Unit,
    /** The step stands in the desktop card of the first-run pages. */
    val inCard: Boolean = false,
)

/** The page of [step]: the keypad of the PIN steps, or the passphrase field. */
@Composable
private fun PinStepContent(step: Int, pinState: PinChangeState, actions: PinStepActions) {
    val onSubmitPin = actions.onSubmitPin
    if (pinState.isPassphrase) {
        PassphraseStepContent(step, pinState, actions)
        return
    }
    MainPinContent(
        text = pinStepText(step, pinState),
        entered = pinState.input.length,
        entry = PinEntryState(
            enabled = pinState.keysEnabled && !actions.locked,
            shakeKey = pinState.shakeKey,
            compact = !pinState.isReset,
            canSubmit = pinState.canSubmit,
            busy = pinState.busy,
            onSubmit = {
                if (pinState.animationState == PinAnimationState.Entering && pinState.canSubmit && !pinState.busy) {
                    onSubmitPin()
                }
            },
        ),
        onInput = pinState::typeDigit,
        onDelete = pinState.input::deleteLast,
        inCard = actions.inCard,
    )
}

private const val RESET_STILL_NEEDED_POLL_MS = 1_000L

/** Another window may set the new PIN first: this reset has nothing left to do then, and goes on into the app. */
@Composable
private fun LeaveWhenAnotherWindowSetThePin(
    pinState: PinChangeState,
    requiresCurrentPin: () -> Boolean,
    navController: NavHostController,
) {
    val stillRequired by rememberUpdatedState(requiresCurrentPin)
    LaunchedEffect(pinState) {
        while (pinState.animationState == PinAnimationState.Entering) {
            delay(RESET_STILL_NEEDED_POLL_MS)
            if (pinState.animationState == PinAnimationState.Entering && stillRequired()) {
                navController.finishPinChange(isReset = true)
                break
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
    } else if (currentBackStackEntry?.destination?.route == NavRoutes.PinCodeConfig.name) {
        // Not a Back that already left the screen: that would pop the one beneath it.
        popBack()
    }
}

@Composable
private fun pinStepText(step: Int, state: PinChangeState): PinStepText {
    val lockout = state.lockoutSeconds.takeIf { it > 0L && step == 0 }?.let { pinLockoutText(it) }
    val (title, subtitle) = pinStepTexts(step)
    return PinStepText(
        kind = state.kind,
        ticking = lockout != null,
        title = title,
        subtitle = if (state.isReset && step == 1) Res.string.pin_reset_subtitle else subtitle,
        message = lockout ?: state.errorText?.let { secretString(state.kind, it) },
    )
}

/** The title and the sub-heading of [step]: the current secret, the new one, its confirmation. */
private fun pinStepTexts(step: Int): Pair<StringResource, StringResource> = when (step) {
    0 -> Res.string.pin_change_current_title to Res.string.pin_change_current_subtitle
    1 -> Res.string.pin_create_title to Res.string.pin_change_new_subtitle
    else -> Res.string.pin_change_confirm_title to Res.string.pin_change_confirm_subtitle
}

/** What a PIN step shows: its title and subtitle, and an error or lockout message. */
data class PinStepText(
    val title: StringResource,
    val subtitle: StringResource,
    val message: String?,
    /** The message is a lockout countdown that changes every second. */
    val ticking: Boolean = false,
    /** What the secret is, which words the texts. */
    val kind: SecretKind = SecretKind.PIN,
)

/** How the keypad of a PIN step behaves: off during a lockout, the shake, a compact top. */
private class PinEntryState(
    val enabled: Boolean,
    val shakeKey: Int,
    val compact: Boolean,
    val canSubmit: Boolean,
    val onSubmit: () -> Unit,
    val busy: Boolean,
)

/** The passphrase twin of [MainPinContent]: the same texts, a field and a button in place of the dots and keys. */
@Composable
private fun PassphraseStepContent(step: Int, pinState: PinChangeState, actions: PinStepActions) {
    val text = pinStepText(step, pinState)
    val kind = pinState.kind
    val message = text.message
    PassphraseStep(
        text = PassphraseStepText(
            title = secretString(kind, text.title),
            // An error or the lockout countdown takes the sub-heading slot, as on the lock screen.
            subtitle = message ?: secretString(kind, text.subtitle),
            label = stringResource(Res.string.passphrase_label),
            submit = stringResource(Res.string.action_continue),
            subtitleIsError = message != null,
            subtitleTicks = text.ticking,
        ),
        input = pinState.passphrase,
        actions = PassphraseStepActions(
            onSubmit = {
                if (pinState.animationState == PinAnimationState.Entering && pinState.canSubmit && !pinState.busy) {
                    actions.onSubmitPin()
                }
            },
            onEdit = { pinState.errorText = null },
            // Only where a new passphrase is chosen; never next to the current or the confirming one.
            onSuggest = actions.onSuggest.takeIf { step == 1 },
        ),
        style = PassphraseStepStyle(
            enabled = pinState.keysEnabled && !actions.locked,
            busy = pinState.busy,
            compact = !pinState.isReset,
            inCard = actions.inCard,
        ),
    )
}

@Composable
private fun MainPinContent(
    text: PinStepText,
    entered: Int,
    entry: PinEntryState,
    onInput: (Int) -> Unit,
    onDelete: () -> Unit,
    inCard: Boolean = false,
) {
    val message = text.message
    val kind = text.kind
    PinEntryScaffold(
        title = secretString(kind, text.title),
        // An error or the lockout countdown takes the sub-heading slot, as on the lock screen.
        subtitle = message ?: secretString(kind, text.subtitle),
        subtitleIsError = message != null,
        subtitleTicks = text.ticking,
        compact = entry.compact,
        busy = entry.busy,
        bodyAtBottom = true,
        inCard = inCard,
    ) { fit ->
        PinEntry(
            fit = fit,
            entered = entered,
            error = message != null && entered == 0,
            shakeKey = entry.shakeKey,
            enabled = entry.enabled,
            actions = PinEntryActions(
                onDigit = onInput,
                onBackspace = onDelete,
            ),
            submit = PinSubmit(
                text = stringResource(Res.string.action_continue),
                enabled = entry.canSubmit,
                onClick = entry.onSubmit,
            ),
        )
    }
}

@Composable
private fun SuccessAnimation(kind: SecretKind, inCard: Boolean) {
    val scale = remember { Animatable(0f) }
    val description = secretString(kind, Res.string.pin_changed)

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = SUCCESS_SCALE_MS, easing = EnclyMotion.Enter),
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.m, Alignment.CenterVertically),
        modifier = (
            if (inCard) Modifier.fillMaxWidth().padding(vertical = EnclyTheme.spacing.xxl) else Modifier.fillMaxSize()
            )
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
