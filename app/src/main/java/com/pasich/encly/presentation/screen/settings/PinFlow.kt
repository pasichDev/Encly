package com.pasich.encly.presentation.screen.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.presentation.designsystem.EnclySnackbarHost
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.screen.pincode.PinBuffer
import com.pasich.encly.presentation.screen.pincode.PinEntry
import com.pasich.encly.presentation.screen.pincode.PinEntryActions
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.lockoutSecondsLeft
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import kotlinx.coroutines.launch

internal enum class PinStep { VERIFY, OPTIONS, NEW, CONFIRM }

/**
 * Settings pages that set a second PIN (the wipe PIN, the duress PIN) share one flow: the real
 * PIN first, then an options page, then the new PIN typed twice. The PINs are CharArrays that
 * are wiped once used, never Strings (see [PinBuffer]).
 */
internal class PinFlowState {
    var step by mutableStateOf(PinStep.VERIFY)

    /** The new PIN typed at [PinStep.NEW], until the confirmation is compared with it. */
    private var firstPin: CharArray? = null

    val input = PinBuffer()
    var errorText by mutableStateOf<Int?>(null)
    var lockoutSeconds by mutableLongStateOf(0L)

    /** Changes on every wrong entry, so the dots shake again. */
    var shakeKey by mutableIntStateOf(0)

    val snackbarHostState = SnackbarHostState()

    /** A finished action to announce on the options page. */
    var message by mutableStateOf<Int?>(null)

    val keysEnabled: Boolean get() = !(step == PinStep.VERIFY && lockoutSeconds > 0L)

    fun typeDigit(digit: Int) {
        if (keysEnabled) input.add(digit)
    }

    /** The PIN of the current step is complete: check it, keep it, or compare it. */
    fun onPinComplete(actions: PinFlowActions) {
        // The ViewModel wipes what it is given; everything else is wiped here.
        val pin = input.take()
        when (step) {
            PinStep.VERIFY -> actions.verify(pin) { ok -> onCurrentPinChecked(ok, actions.lockoutMillis()) }

            PinStep.NEW -> {
                firstPin?.let(SensitiveDataCleaner::clear)
                firstPin = pin
                errorText = null
                step = PinStep.CONFIRM
            }

            PinStep.CONFIRM -> confirm(pin, actions.save)

            PinStep.OPTIONS -> SensitiveDataCleaner.clear(pin)
        }
    }

    fun startNew() {
        errorText = null
        step = PinStep.NEW
    }

    /** The new PIN was saved: announce [text] on the options page. */
    fun saved(@StringRes text: Int) {
        message = text
        step = PinStep.OPTIONS
    }

    /** The new PIN was refused: ask for another, with [error]. */
    fun restartNew(@StringRes error: Int) {
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
        firstPin?.let(SensitiveDataCleaner::clear)
        firstPin = null
    }

    private fun onCurrentPinChecked(ok: Boolean, lockoutMillis: Long) {
        val lockout = lockoutSecondsLeft(lockoutMillis)
        errorText = null
        when {
            ok -> step = PinStep.OPTIONS

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

    private fun confirm(pin: CharArray, save: (CharArray) -> Unit) {
        val first = firstPin
        firstPin = null
        val matches = first != null && pin.contentEquals(first)
        first?.let(SensitiveDataCleaner::clear)
        if (!matches) {
            SensitiveDataCleaner.clear(pin)
            restartNew(R.string.pin_mismatch_retry)
            return
        }
        save(pin)
    }
}

/** What a PIN flow calls out to; [save] takes over the confirmed PIN (and wipes it). */
internal class PinFlowActions(
    val verify: (CharArray, (Boolean) -> Unit) -> Unit,
    val lockoutMillis: () -> Long,
    val save: (CharArray) -> Unit,
    val close: () -> Unit,
)

/** The strings of a PIN flow's steps. */
internal class PinFlowTexts(
    @param:StringRes val title: Int,
    @param:StringRes val currentSubtitle: Int,
    @param:StringRes val newTitle: Int,
    @param:StringRes val newSubtitle: Int,
    @param:StringRes val confirmTitle: Int,
    @param:StringRes val confirmSubtitle: Int,
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
    LaunchedEffect(state.input.length) {
        if (state.input.isFull) state.onPinComplete(actions)
    }
    DisposableEffect(state) { onDispose { state.clear() } }
    PinFlowMessageEffect(state.message, state.snackbarHostState, onShow = { state.message = null })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { EnclyTopBar(title = stringResource(texts.title), onBack = { if (!state.back()) actions.close() }) },
        snackbarHost = { EnclySnackbarHost(state.snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        if (state.step == PinStep.OPTIONS) {
            options(paddingValues)
        } else {
            PinFlowEntry(
                text = pinFlowStepText(state, texts),
                state = state,
                modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues),
            )
        }
    }
}

/** Shows [message] once as a snackbar; [onShow] clears it. */
@Composable
private fun PinFlowMessageEffect(@StringRes message: Int?, snackbarHostState: SnackbarHostState, onShow: () -> Unit) {
    val messageText = message?.let { stringResource(it) }
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
private class PinStepText(@param:StringRes val title: Int, @param:StringRes val subtitle: Int, val message: String?)

@Composable
private fun pinFlowStepText(state: PinFlowState, texts: PinFlowTexts): PinStepText {
    val lockout = state.lockoutSeconds
        .takeIf { it > 0L && state.step == PinStep.VERIFY }
        ?.let { pinLockoutText(it) }
    val error = state.errorText?.let { stringResource(it) }
    return when (state.step) {
        PinStep.VERIFY -> PinStepText(R.string.pin_change_current_title, texts.currentSubtitle, lockout ?: error)
        PinStep.CONFIRM -> PinStepText(texts.confirmTitle, texts.confirmSubtitle, error)
        else -> PinStepText(texts.newTitle, texts.newSubtitle, error)
    }
}

@Composable
private fun PinFlowEntry(text: PinStepText, state: PinFlowState, modifier: Modifier = Modifier) {
    val message = text.message
    PinEntryScaffold(
        title = stringResource(text.title),
        // An error or the lockout countdown takes the sub-heading slot, as on the lock screen.
        subtitle = message ?: stringResource(text.subtitle),
        subtitleIsError = message != null,
        compact = true,
        bodyAtBottom = true,
        modifier = modifier,
    ) {
        PinEntry(
            entered = state.input.length,
            error = message != null && state.input.length == 0,
            shakeKey = state.shakeKey,
            enabled = state.keysEnabled,
            actions = PinEntryActions(onDigit = state::typeDigit, onBackspace = state.input::deleteLast),
        )
    }
}
