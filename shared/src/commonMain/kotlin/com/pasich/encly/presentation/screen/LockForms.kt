package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.lock_back_to_pin
import com.pasich.encly.core.resources.lock_password_label
import com.pasich.encly.core.resources.lock_password_subtitle
import com.pasich.encly.core.resources.lock_recover_access
import com.pasich.encly.core.resources.lock_recovery_subtitle
import com.pasich.encly.core.resources.lock_recovery_title
import com.pasich.encly.core.resources.lock_subtitle
import com.pasich.encly.core.resources.lock_title
import com.pasich.encly.core.resources.lock_unlock
import com.pasich.encly.core.resources.lock_unlocking
import com.pasich.encly.core.resources.lock_use_password
import com.pasich.encly.core.resources.lock_use_pin
import com.pasich.encly.core.resources.lock_use_recovery_phrase
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.core.security.UnlockMethods
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.RecoveryPhraseInput
import com.pasich.encly.presentation.screen.pincode.AuthLoading
import com.pasich.encly.presentation.screen.pincode.PasswordField
import com.pasich.encly.presentation.screen.pincode.PinEntry
import com.pasich.encly.presentation.screen.pincode.PinEntryActions
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.PinSubmit
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.PinUnlockResult
import com.pasich.encly.presentation.viewmodel.SeedUnlockResult
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

/**
 * What the lock screen offers besides the form on show: the fingerprint key, the recovery phrase, and which of the
 * PIN and the password the vault has ([methods]).
 */
internal data class LockCapabilities(
    val biometricEnabled: Boolean,
    val recoveryAvailable: Boolean,
    val methods: UnlockMethods,
) {
    /**
     * The other way in, as a link under the form on show: null when the vault has one way in only, else whether it
     * is the password (true, from the PIN pad) or the PIN (false, from the password form).
     */
    fun otherMethod(form: LockFormState): Boolean? = if (methods.pin && methods.password) !form.usePassword else null
}

/** The three checks of a secret the lock screen can send; each wipes what it is given. */
internal class LockChecks(
    val pin: (pin: CharArray, onResult: (PinUnlockResult) -> Unit) -> Unit,
    val password: (password: CharArray, onResult: (PinUnlockResult) -> Unit) -> Unit,
    val seed: (phrase: CharArray, onResult: (SeedUnlockResult) -> Unit) -> Unit,
)

/** What the lock screen's forms ask of the view model and of the screen (the view model stays in [LockScreen]). */
internal class LockActions(
    val checks: LockChecks,
    val lockoutRemainingMillis: () -> Long,
    val onUnlock: () -> Unit,
    /** The recovery phrase opened the vault: the PIN is forgotten, set a new one or keep it. */
    val onRecoveryUnlock: () -> Unit,
    /** The device-bound key is gone for good (see [PinUnlockResult.KEY_LOST]). */
    val onKeyLoss: () -> Unit,
    val onPromptBiometric: () -> Unit,
)

/** The form on show: the recovery phrase, the password, or the PIN pad. */
@Composable
internal fun LockForms(
    form: LockFormState,
    busy: Boolean,
    capabilities: LockCapabilities,
    actions: LockActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (form.useRecovery) {
            SeedLockContent(
                form = form,
                busy = busy,
                authenticateSeed = actions.checks.seed,
                onUnlock = actions.onRecoveryUnlock,
            )
            // Drawn over the phrase form rather than instead of it, so it keeps its state. The PIN
            // form shows the check in place (full dots, breathing logo) and hands over to the reveal.
            if (busy) AuthLoading(stringResource(Res.string.lock_unlocking))
        } else if (form.usePassword) {
            PasswordLockContent(form, capabilities, busy, actions)
        } else {
            PinLockContent(form, capabilities, busy, actions)
        }
    }
}

@Composable
private fun PinLockContent(form: LockFormState, capabilities: LockCapabilities, busy: Boolean, actions: LockActions) {
    PinLockoutTicker(form.lockedOut, actions.lockoutRemainingMillis) { form.lockoutSeconds = it }
    // Digits typed but never submitted do not outlive the lock screen.
    DisposableEffect(form) { onDispose { form.clearPin() } }
    val submit = { submitSecret(form.takePin(), actions.checks.pin, actions) { r, ms -> form.onPinResult(r, ms) } }

    val shownError = form.pinError
    PinEntryScaffold(
        title = stringResource(Res.string.lock_title),
        bodyAtBottom = true,
        // The sub-heading slot carries the lockout countdown or the last error.
        subtitle = when {
            form.lockedOut -> pinLockoutText(form.lockoutSeconds)
            shownError != null -> stringResource(shownError)
            else -> stringResource(Res.string.lock_subtitle)
        },
        subtitleIsError = form.lockedOut || shownError != null,
        busy = busy,
    ) {
        PinEntry(
            // The PIN is taken out of the form for the check; keep the dots as they were meanwhile.
            entered = if (busy) form.submittedLength else form.pinLength,
            error = shownError != null && form.pinLength == 0,
            shakeKey = form.shakeKey,
            enabled = !form.lockedOut && !busy,
            actions = PinEntryActions(
                onDigit = form::typeDigit,
                onBackspace = form::deleteDigit,
                onBiometric = actions.onPromptBiometric.takeIf { capabilities.biometricEnabled },
            ),
            // No auto-submit: a PIN is 6 to 12 digits, so the user says when it is complete.
            submit = PinSubmit(
                text = stringResource(Res.string.lock_unlock),
                enabled = form.canSubmitPin && !busy,
                onClick = submit,
            ),
        )
        OtherMethodLinks(form, capabilities, busy)
    }
}

/** The links under the PIN pad: the password (when the vault has one), and the recovery phrase. */
@Composable
private fun OtherMethodLinks(form: LockFormState, capabilities: LockCapabilities, busy: Boolean) {
    capabilities.otherMethod(form)?.let { toPassword ->
        EnclyTextButton(
            text = stringResource(if (toPassword) Res.string.lock_use_password else Res.string.lock_use_pin),
            onClick = { form.switchTo(usePassword = toPassword) },
            modifier = Modifier.padding(horizontal = EnclyTheme.spacing.gutter),
            enabled = !busy,
        )
    }
    if (capabilities.recoveryAvailable) RecoveryLink(onUseRecovery = { form.useRecovery = true })
}

/** The password form: a masked field, the Unlock button pinned above the keyboard, and the same links. */
@Composable
private fun PasswordLockContent(
    form: LockFormState,
    capabilities: LockCapabilities,
    busy: Boolean,
    actions: LockActions,
) {
    PinLockoutTicker(form.lockedOut, actions.lockoutRemainingMillis) { form.lockoutSeconds = it }
    DisposableEffect(form) { onDispose { form.password.clear() } }
    ReleaseFocusWhile(busy)
    val canSubmit = form.password.canSubmit && !form.lockedOut && !busy
    val submit = {
        if (canSubmit) {
            submitSecret(form.takePassword(), actions.checks.password, actions) { r, ms ->
                form.onPasswordResult(r, ms)
            }
        }
    }
    val shownError = form.passwordError
    PinEntryScaffold(
        title = stringResource(Res.string.lock_title),
        subtitle = when {
            form.lockedOut -> pinLockoutText(form.lockoutSeconds)
            shownError != null -> stringResource(shownError)
            else -> stringResource(Res.string.lock_password_subtitle)
        },
        subtitleIsError = form.lockedOut || shownError != null,
        icon = EnclyIcons.Key,
        compact = true,
        footer = {
            EnclyButton(text = stringResource(Res.string.lock_unlock), onClick = submit, enabled = canSubmit)
            capabilities.otherMethod(form)?.let { toPassword ->
                EnclyTextButton(
                    text = stringResource(if (toPassword) Res.string.lock_use_password else Res.string.lock_use_pin),
                    onClick = { form.switchTo(usePassword = toPassword) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
            }
            if (capabilities.recoveryAvailable) {
                EnclyTextButton(
                    text = stringResource(Res.string.lock_use_recovery_phrase),
                    onClick = { form.useRecovery = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
            }
        },
    ) {
        PasswordField(
            input = form.password,
            label = stringResource(Res.string.lock_password_label),
            enabled = !form.lockedOut && !busy,
            onEdit = form::onPasswordEdited,
            onDone = submit,
            modifier = Modifier
                .padding(horizontal = EnclyTheme.spacing.gutter)
                .padding(top = EnclyTheme.spacing.l),
        )
    }
}

/**
 * Sends [secret] (a PIN or a password) to be checked by [authenticate], unless a lockout runs. The ViewModel wipes it
 * once it is checked; [onFailure] shows a result that did not unlock, with the lockout left.
 */
private fun submitSecret(
    secret: CharArray,
    authenticate: (CharArray, (PinUnlockResult) -> Unit) -> Unit,
    actions: LockActions,
    onFailure: (PinUnlockResult, lockoutMillis: Long) -> Unit,
) {
    if (actions.lockoutRemainingMillis() > 0) {
        SensitiveDataCleaner.clear(secret)
        return
    }
    authenticate(secret) { result ->
        when (result) {
            PinUnlockResult.SUCCESS -> actions.onUnlock()

            PinUnlockResult.KEY_LOST -> {
                onFailure(result, actions.lockoutRemainingMillis())
                actions.onKeyLoss()
            }

            else -> onFailure(result, actions.lockoutRemainingMillis())
        }
    }
}

@Composable
private fun SeedLockContent(
    form: LockFormState,
    busy: Boolean,
    authenticateSeed: (phrase: CharArray, onResult: (SeedUnlockResult) -> Unit) -> Unit,
    onUnlock: () -> Unit,
) {
    ReleaseFocusWhile(busy)
    val submit = {
        if (form.phrase.canSubmit && !busy) {
            authenticateSeed(form.phrase.toCharArray()) { result ->
                if (result == SeedUnlockResult.SUCCESS) onUnlock() else form.onSeedResult(result)
            }
        }
    }
    PinEntryScaffold(
        title = stringResource(Res.string.lock_recovery_title),
        subtitle = stringResource(Res.string.lock_recovery_subtitle),
        icon = EnclyIcons.Key,
        // The 12 cells need the room: no 96 dp top, so they stay above the keyboard.
        compact = true,
        // Pinned above the keyboard: typing the phrase must never hide the button.
        footer = {
            EnclyButton(
                text = stringResource(Res.string.lock_recover_access),
                onClick = submit,
                enabled = form.phrase.canSubmit && !busy,
            )
            EnclyTextButton(
                text = stringResource(Res.string.lock_back_to_pin),
                onClick = form::leaveRecovery,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
        },
    ) {
        RecoveryPhraseInput(
            state = form.phrase,
            enabled = !busy,
            error = form.phraseError?.let { stringResource(it) },
            onEdit = form::onPhraseEdited,
            onDone = submit,
            modifier = Modifier
                .padding(horizontal = EnclyTheme.spacing.gutter)
                .padding(top = EnclyTheme.spacing.l),
        )
    }
}

/** "Forgot your PIN? Use your recovery phrase" under the keypad. */
@Composable
private fun RecoveryLink(onUseRecovery: () -> Unit) {
    EnclyTextButton(
        text = stringResource(Res.string.lock_use_recovery_phrase),
        onClick = onUseRecovery,
        modifier = Modifier.padding(
            top = EnclyTheme.spacing.l,
            start = EnclyTheme.spacing.gutter,
            end = EnclyTheme.spacing.gutter,
        ),
    )
}

/**
 * The loading view covers a form but not the keyboard: while a secret is checked (the phrase, the password) the
 * field lets go of focus, so the keyboard closes and cannot type into it meanwhile.
 */
@Composable
private fun ReleaseFocusWhile(busy: Boolean) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(busy) {
        if (busy) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
}
