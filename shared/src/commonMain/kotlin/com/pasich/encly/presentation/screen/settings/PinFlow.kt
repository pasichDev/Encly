package com.pasich.encly.presentation.screen.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.passphrase_label
import com.pasich.encly.core.resources.pin_change_current_title
import com.pasich.encly.core.resources.pin_mismatch_retry
import com.pasich.encly.core.security.KeyStoreProblem
import com.pasich.encly.core.security.PinCheck
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.platform.secretString
import com.pasich.encly.presentation.designsystem.EnclySnackbarHost
import com.pasich.encly.presentation.designsystem.EnclyTopBar
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
import com.pasich.encly.presentation.viewmodel.SecretRefusal
import com.pasich.encly.presentation.viewmodel.pinCheckError
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal enum class PinStep { VERIFY, OPTIONS, NEW, CONFIRM }

/**
 * Settings pages that set a second PIN (the wipe PIN, the duress PIN) share one flow: the real
 * PIN first, then an options page, then the new PIN typed twice. The PINs are CharArrays that
 * are wiped once used, never Strings (see [PinBuffer]). Where the secret is a passphrase ([kind]) the same steps run
 * with a field in place of the keypad, and the new one is checked against the policy before it is asked for again.
 */
internal class PinFlowState(
    val kind: SecretKind = SecretKind.PIN,
    maxPassphraseUnits: Int = PassphraseInput.MAX_TYPED_CHARS,
) {
    var step by mutableStateOf(PinStep.VERIFY)

    /** The new PIN typed at [PinStep.NEW], until the confirmation is compared with it. */
    private var firstPin: CharArray? = null

    val input = PinBuffer()

    /** The passphrase of the current step, where the secret is one. */
    val passphrase = PassphraseInput(maxPassphraseUnits)

    /** A secret is being checked or saved: nothing more is taken until the answer is here. */
    var busy by mutableStateOf(false)
    val isPassphrase: Boolean get() = kind == SecretKind.PASSWORD

    /** Counts the locks of the vault: the answer of a check that began before one is dropped (see [lock]). */
    private var generation = 0

    /** Something complete enough to send is typed: a PIN of enough digits, or any passphrase (the policy decides). */
    val canSubmit: Boolean get() = if (isPassphrase) passphrase.canSubmit else input.canSubmit
    var errorText by mutableStateOf<StringResource?>(null)
    var lockoutSeconds by mutableLongStateOf(0L)

    /** Changes on every wrong entry, so the dots shake again. */
    var shakeKey by mutableIntStateOf(0)

    val snackbarHostState = SnackbarHostState()

    /** A finished action to announce on the options page. */
    var message by mutableStateOf<StringResource?>(null)

    val keysEnabled: Boolean get() = !(step == PinStep.VERIFY && lockoutSeconds > 0L) && !busy

    fun typeDigit(digit: Int) {
        if (keysEnabled) input.add(digit)
    }

    /** The PIN of the current step is complete: check it, keep it, or compare it. */
    fun onPinComplete(actions: PinFlowActions) {
        if (busy || refusedAsNew(actions)) return
        // The ViewModel wipes what it is given; everything else is wiped here.
        val pin = if (isPassphrase) passphrase.take() else input.take()
        when (step) {
            PinStep.VERIFY -> {
                busy = true
                val asked = generation
                actions.verify(pin) { check ->
                    // The vault locked while this was being checked: an answer that comes after it opens nothing.
                    if (asked == generation) onCurrentPinChecked(check, actions)
                }
            }

            PinStep.NEW -> {
                firstPin?.let(SensitiveDataCleaner::clear)
                firstPin = pin
                errorText = null
                step = PinStep.CONFIRM
            }

            // A confirmation with nothing to compare it to is no step to be on: start the choice again.
            PinStep.CONFIRM -> if (firstPin == null) {
                SensitiveDataCleaner.clear(pin)
                step = PinStep.NEW
            } else {
                confirm(pin, actions)
            }

            PinStep.OPTIONS -> SensitiveDataCleaner.clear(pin)
        }
    }

    fun startNew() {
        errorText = null
        step = PinStep.NEW
    }

    /** The new PIN was saved: announce [text] on the options page. */
    fun saved(text: StringResource) {
        busy = false
        message = text
        step = PinStep.OPTIONS
    }

    /** The new PIN was refused: ask for another, with [error]. */
    fun restartNew(error: StringResource) {
        busy = false
        errorText = error
        shakeKey++
        step = PinStep.NEW
    }

    /** Back from a PIN step to the options; false when there is nothing to go back to here. */
    fun back(): Boolean {
        if (step != PinStep.NEW && step != PinStep.CONFIRM) return false
        clear()
        errorText = null
        step = PinStep.OPTIONS
        return true
    }

    /** Typed or kept digits do not outlive the screen. */
    fun clear() {
        input.clear()
        passphrase.clear()
        firstPin?.let(SensitiveDataCleaner::clear)
        firstPin = null
    }

    /** The vault locked: what is typed and kept is dropped, and a step that asks for a new secret is left. */
    fun lock() {
        clear()
        generation++
        busy = false
        if (step == PinStep.NEW || step == PinStep.CONFIRM) {
            errorText = null
            step = PinStep.OPTIONS
        }
    }

    private fun onCurrentPinChecked(check: PinCheck, actions: PinFlowActions) {
        busy = false
        val lockout = lockoutSecondsLeft(actions.lockoutMillis())
        errorText = null
        when {
            check == PinCheck.OK -> step = PinStep.OPTIONS

            // A locked-out PIN is refused even when right: say so, not "wrong PIN".
            lockout > 0L -> {
                lockoutSeconds = lockout
                shakeKey++
            }

            // A key store that did not answer says what it is missing; only a wrong secret says "wrong".
            else -> {
                errorText = pinCheckError(check, actions.keyStoreProblem())
                shakeKey++
            }
        }
    }

    private fun confirm(pin: CharArray, actions: PinFlowActions) {
        val first = firstPin
        firstPin = null
        val matches = first != null && actions.same(first, pin)
        first?.let(SensitiveDataCleaner::clear)
        if (!matches) {
            SensitiveDataCleaner.clear(pin)
            restartNew(Res.string.pin_mismatch_retry)
            return
        }
        busy = true
        actions.save(pin)
    }
}

/** A passphrase the policy refuses as the new one: say why and leave the text, so it can be fixed by editing. */
private fun PinFlowState.refusedAsNew(actions: PinFlowActions): Boolean {
    val refusal = if (isPassphrase && step == PinStep.NEW) {
        val typed = passphrase.peek()
        try {
            actions.refusal(typed)
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
internal fun PinFlowState.suggest(actions: PinFlowActions) {
    val suggestion = actions.suggest() ?: return
    passphrase.fill(suggestion)
    SensitiveDataCleaner.clear(suggestion)
    errorText = null
}

/** What a PIN flow calls out to; [save] takes over the confirmed PIN (and wipes it). */
internal class PinFlowActions(
    val verify: (CharArray, (PinCheck) -> Unit) -> Unit,
    val lockoutMillis: () -> Long,
    val save: (CharArray) -> Unit,
    val close: () -> Unit,
    /** Why a passphrase typed as the new secret is refused, or null; a PIN never is. */
    val refusal: (CharArray) -> SecretRefusal? = { null },
    /** Whether two entries are the same secret. */
    val same: (CharArray, CharArray) -> Boolean = { a, b -> a.contentEquals(b) },
    /** A passphrase to offer, or null where there is none (a PIN). */
    val suggest: () -> CharArray? = { null },
    /** What the key store was missing when [verify] found it unusable, for the message. */
    val keyStoreProblem: () -> KeyStoreProblem? = { null },
)

/** The strings of a PIN flow's steps. */
internal class PinFlowTexts(
    val title: StringResource,
    val currentSubtitle: StringResource,
    val newTitle: StringResource,
    val newSubtitle: StringResource,
    val confirmTitle: StringResource,
    val confirmSubtitle: StringResource,
)

/**
 * The frame of a PIN flow: top bar, snackbar and the keypad steps. [options] draws the
 * [PinStep.OPTIONS] page.
 */
@Composable
internal fun PinFlowScaffold(
    state: PinFlowState,
    texts: PinFlowTexts,
    actions: PinFlowActions,
    modifier: Modifier = Modifier,
    options: @Composable (PaddingValues) -> Unit,
) {
    BackHandler(enabled = state.step == PinStep.NEW || state.step == PinStep.CONFIRM) { state.back() }
    PinLockoutTicker(state.lockoutSeconds > 0L, actions.lockoutMillis) { state.lockoutSeconds = it }
    DisposableEffect(state) { onDispose { state.clear() } }
    PinFlowMessageEffect(state.message, state.kind, state.snackbarHostState, onShow = { state.message = null })
    // Locked in the middle of the flow: what was typed goes at once, and nothing takes keys under the shield.
    val locked = clearWhenVaultLocks { state.lock() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(title = secretString(state.kind, texts.title), onBack = { if (!state.back()) actions.close() })
        },
        snackbarHost = { EnclySnackbarHost(state.snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        if (state.step == PinStep.OPTIONS) {
            options(paddingValues)
        } else {
            val entryModifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues)
            if (state.isPassphrase) {
                PassphraseFlowEntry(
                    text = pinFlowStepText(state, texts),
                    state = state,
                    actions = actions,
                    locked = locked,
                    modifier = entryModifier,
                )
            } else {
                PinFlowEntry(
                    text = pinFlowStepText(state, texts),
                    state = state,
                    onSubmit = { if (state.canSubmit && !state.busy) state.onPinComplete(actions) },
                    locked = locked,
                    modifier = entryModifier,
                )
            }
        }
    }
}

/** Shows [message] once as a snackbar; [onShow] clears it. */
@Composable
private fun PinFlowMessageEffect(
    message: StringResource?,
    kind: SecretKind,
    snackbarHostState: SnackbarHostState,
    onShow: () -> Unit,
) {
    val messageText = message?.let { secretString(kind, it) }
    val currentOnShow by rememberUpdatedState(onShow)
    // Clearing the message restarts this effect, which must not cancel the snackbar it opened.
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(message, messageText) {
        val text = messageText ?: return@LaunchedEffect
        currentOnShow()
        snackbarScope.launch { snackbarHostState.showSnackbar(text) }
    }
}

/** A PIN step's title and subtitle, and an error or lockout message. */
private class PinStepText(
    val title: StringResource,
    val subtitle: StringResource,
    val message: String?,
    /** The message is a lockout countdown that changes every second. */
    val ticking: Boolean = false,
)

@Composable
private fun pinFlowStepText(state: PinFlowState, texts: PinFlowTexts): PinStepText {
    val lockout = state.lockoutSeconds
        .takeIf { it > 0L && state.step == PinStep.VERIFY }
        ?.let { pinLockoutText(it) }
    val error = state.errorText?.let { secretString(state.kind, it) }
    return when (state.step) {
        PinStep.VERIFY -> PinStepText(
            Res.string.pin_change_current_title,
            texts.currentSubtitle,
            lockout ?: error,
            ticking = lockout != null,
        )

        PinStep.CONFIRM -> PinStepText(texts.confirmTitle, texts.confirmSubtitle, error)

        else -> PinStepText(texts.newTitle, texts.newSubtitle, error)
    }
}

@Composable
private fun PinFlowEntry(
    text: PinStepText,
    state: PinFlowState,
    onSubmit: () -> Unit,
    locked: Boolean,
    modifier: Modifier = Modifier,
) {
    val message = text.message
    PinEntryScaffold(
        title = secretString(state.kind, text.title),
        // An error or the lockout countdown takes the sub-heading slot, as on the lock screen.
        subtitle = message ?: secretString(state.kind, text.subtitle),
        subtitleIsError = message != null,
        subtitleTicks = text.ticking,
        compact = true,
        busy = state.busy,
        bodyAtBottom = true,
        modifier = modifier,
    ) { fit ->
        PinEntry(
            fit = fit,
            entered = state.input.length,
            error = message != null && state.input.length == 0,
            shakeKey = state.shakeKey,
            enabled = state.keysEnabled && !locked,
            actions = PinEntryActions(onDigit = state::typeDigit, onBackspace = state.input::deleteLast),
            submit = PinSubmit(
                text = stringResource(Res.string.action_continue),
                enabled = state.input.canSubmit,
                onClick = onSubmit,
            ),
        )
    }
}

/** The passphrase twin of [PinFlowEntry]: the same texts, a field and a button in place of the dots and keys. */
@Composable
private fun PassphraseFlowEntry(
    text: PinStepText,
    state: PinFlowState,
    actions: PinFlowActions,
    locked: Boolean,
    modifier: Modifier = Modifier,
) {
    val kind = state.kind
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
        input = state.passphrase,
        actions = PassphraseStepActions(
            onSubmit = { if (state.canSubmit && !state.busy) state.onPinComplete(actions) },
            onEdit = { state.errorText = null },
            // Only where the new passphrase is chosen; never next to the current or the confirming one.
            onSuggest = { state.suggest(actions) }.takeIf { state.step == PinStep.NEW },
        ),
        style = PassphraseStepStyle(
            enabled = state.keysEnabled && !locked,
            busy = state.busy,
        ),
        modifier = modifier,
    )
}
