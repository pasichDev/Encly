package com.pasich.encly.presentation.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.lock_password_label
import com.pasich.encly.core.resources.password_change
import com.pasich.encly.core.resources.password_confirm_label
import com.pasich.encly.core.resources.password_current_subtitle
import com.pasich.encly.core.resources.password_current_title
import com.pasich.encly.core.resources.password_current_wrong
import com.pasich.encly.core.resources.password_edit_title
import com.pasich.encly.core.resources.password_failed
import com.pasich.encly.core.resources.password_intro
import com.pasich.encly.core.resources.password_mismatch
import com.pasich.encly.core.resources.password_new_label
import com.pasich.encly.core.resources.password_pin_only_note
import com.pasich.encly.core.resources.password_remove
import com.pasich.encly.core.resources.password_remove_confirm
import com.pasich.encly.core.resources.password_remove_confirm_body
import com.pasich.encly.core.resources.password_remove_confirm_title
import com.pasich.encly.core.resources.password_removed
import com.pasich.encly.core.resources.password_save
import com.pasich.encly.core.resources.password_saved
import com.pasich.encly.core.resources.password_set
import com.pasich.encly.core.resources.password_title
import com.pasich.encly.core.resources.password_too_short
import com.pasich.encly.core.resources.password_turned_off
import com.pasich.encly.core.resources.pin_change_current_subtitle
import com.pasich.encly.core.resources.pin_change_current_title
import com.pasich.encly.core.resources.pin_current_wrong
import com.pasich.encly.core.resources.pin_remove
import com.pasich.encly.core.resources.pin_remove_confirm
import com.pasich.encly.core.resources.pin_remove_confirm_body
import com.pasich.encly.core.resources.pin_remove_confirm_title
import com.pasich.encly.core.resources.pin_remove_failed
import com.pasich.encly.core.resources.pin_removed
import com.pasich.encly.core.security.PasswordChange
import com.pasich.encly.presentation.designsystem.CalloutTone
import com.pasich.encly.presentation.designsystem.DialogAction
import com.pasich.encly.presentation.designsystem.EnclyButton
import com.pasich.encly.presentation.designsystem.EnclyCallout
import com.pasich.encly.presentation.designsystem.EnclyDialog
import com.pasich.encly.presentation.designsystem.EnclyScrollColumn
import com.pasich.encly.presentation.designsystem.EnclySnackbarHost
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.EnclyTopBar
import com.pasich.encly.presentation.screen.pincode.PasswordField
import com.pasich.encly.presentation.screen.pincode.PasswordInput
import com.pasich.encly.presentation.screen.pincode.PasswordStepScaffold
import com.pasich.encly.presentation.screen.pincode.PasswordStepSubmit
import com.pasich.encly.presentation.screen.pincode.PasswordStepText
import com.pasich.encly.presentation.screen.pincode.PinBuffer
import com.pasich.encly.presentation.screen.pincode.PinEntry
import com.pasich.encly.presentation.screen.pincode.PinEntryActions
import com.pasich.encly.presentation.screen.pincode.PinEntryScaffold
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.PinSubmit
import com.pasich.encly.presentation.screen.pincode.lockoutSecondsLeft
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.SecuritySettingsViewModel
import com.pasich.encly.ui.theme.EnclyTheme
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What the password page asks of the Security ViewModel, so the ViewModel itself stays in [PasswordScreen]. */
private class PasswordActions(
    val verifyPin: (CharArray, (Boolean) -> Unit) -> Unit,
    val verifyPassword: (CharArray, (Boolean) -> Unit) -> Unit,
    val lockoutMillis: () -> Long,
    val setPassword: (CharArray, (PasswordChange) -> Unit) -> Unit,
    val removePassword: ((Boolean) -> Unit) -> Unit,
    val removePin: ((Boolean) -> Unit) -> Unit,
)

private enum class PasswordStep { VERIFY, OPTIONS, EDIT, REMOVE_PIN }

/**
 * The state of Settings → Security → Password: who is asking is checked first (the PIN, or the password when the
 * vault has no PIN), then the options page, then the new password typed twice. The typed secrets are wiped once
 * used and do not outlive the screen (see [PinBuffer], [PasswordInput]).
 */
private class PasswordFlowState(val verifyWithPin: Boolean) {
    var step by mutableStateOf(PasswordStep.VERIFY)
    val pin = PinBuffer()
    val current = PasswordInput()
    val newPassword = PasswordInput()
    val confirm = PasswordInput()
    var errorText by mutableStateOf<StringResource?>(null)
    var lockoutSeconds by mutableLongStateOf(0L)

    /** Changes on every wrong entry, so the dots shake again. */
    var shakeKey by mutableIntStateOf(0)
    var message by mutableStateOf<StringResource?>(null)
    val snackbarHostState = SnackbarHostState()

    val lockedOut: Boolean get() = lockoutSeconds > 0L

    fun clear() {
        pin.clear()
        current.clear()
        newPassword.clear()
        confirm.clear()
    }

    /** Back from the edit page (or the PIN removal) to the options; false when there is nothing to go back to here. */
    fun back(): Boolean {
        if (step != PasswordStep.EDIT && step != PasswordStep.REMOVE_PIN) return false
        newPassword.clear()
        confirm.clear()
        current.clear()
        errorText = null
        step = PasswordStep.OPTIONS
        return true
    }

    /** The PIN removal ended: announce [removed] on the options page. */
    fun pinRemoved(removed: Boolean) {
        message = if (removed) Res.string.pin_removed else Res.string.pin_remove_failed
        errorText = null
        step = PasswordStep.OPTIONS
    }

    /** The answer of the check of who is asking. */
    fun onVerified(ok: Boolean, lockoutMillis: Long, wrong: StringResource) {
        val lockout = lockoutSecondsLeft(lockoutMillis)
        errorText = null
        when {
            ok -> step = PasswordStep.OPTIONS

            // A locked-out secret is refused even when right: say so, not "wrong".
            lockout > 0L -> {
                lockoutSeconds = lockout
                shakeKey++
            }

            else -> {
                errorText = wrong
                shakeKey++
            }
        }
    }

    /** The new password was refused: stay on the edit page with [error]. */
    fun refused(error: StringResource) {
        errorText = error
        confirm.clear()
    }

    fun saved() {
        newPassword.clear()
        confirm.clear()
        errorText = null
        message = Res.string.password_saved
        step = PasswordStep.OPTIONS
    }
}

/**
 * Settings → Security → Password: set, change or remove the password, and remove the PIN. The PIN (or, with no PIN, the
 * password) is asked for first, as for every security change. At least one of the two always remains.
 */
@Composable
fun PasswordScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    securityViewModel: SecuritySettingsViewModel = metroViewModel(),
) {
    val state = remember { PasswordFlowState(verifyWithPin = securityViewModel.hasPinSlot()) }
    val securityState by securityViewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(securityViewModel) {
        PasswordActions(
            verifyPin = securityViewModel::verifyCurrentPin,
            verifyPassword = securityViewModel::verifyCurrentPassword,
            lockoutMillis = securityViewModel::pinLockoutRemainingMillis,
            setPassword = securityViewModel::setPassword,
            removePassword = securityViewModel::removePassword,
            removePin = securityViewModel::removePin,
        )
    }
    PinLockoutTicker(state.lockedOut, actions.lockoutMillis) { state.lockoutSeconds = it }
    DisposableEffect(state) { onDispose { state.clear() } }
    BackHandler(enabled = state.step == PasswordStep.EDIT || state.step == PasswordStep.REMOVE_PIN) { state.back() }
    PinFlowMessageEffect(state.message, state.snackbarHostState, onShow = { state.message = null })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            EnclyTopBar(
                title = stringResource(Res.string.password_title),
                onBack = { if (!state.back()) navController.popBackStack() },
            )
        },
        snackbarHost = { EnclySnackbarHost(state.snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        val content = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues)
        when (state.step) {
            PasswordStep.VERIFY -> VerifyStep(state, actions, content)

            PasswordStep.OPTIONS -> PasswordOptions(
                state = state,
                hasPassword = securityState.hasPassword,
                hasPin = securityState.hasPinSlot,
                turnedOff = securityState.passwordTurnedOff,
                actions = actions,
                modifier = content,
            )

            PasswordStep.EDIT -> PasswordEdit(state, actions, content)

            PasswordStep.REMOVE_PIN -> RemovePinStep(state, actions, content)
        }
    }
}

@Composable
private fun VerifyStep(state: PasswordFlowState, actions: PasswordActions, modifier: Modifier = Modifier) {
    val error = state.errorText?.let { stringResource(it) }
    val lockout = if (state.lockedOut) pinLockoutText(state.lockoutSeconds) else null
    if (state.verifyWithPin) {
        val submit = {
            if (state.pin.canSubmit && !state.lockedOut) {
                actions.verifyPin(state.pin.take()) { ok ->
                    state.onVerified(ok, actions.lockoutMillis(), Res.string.pin_current_wrong)
                }
            }
        }
        PinEntryScaffold(
            title = stringResource(Res.string.pin_change_current_title),
            subtitle = lockout ?: error ?: stringResource(Res.string.pin_change_current_subtitle),
            subtitleIsError = lockout != null || error != null,
            compact = true,
            bodyAtBottom = true,
            modifier = modifier,
        ) {
            PinEntry(
                entered = state.pin.length,
                error = error != null && state.pin.length == 0,
                shakeKey = state.shakeKey,
                enabled = !state.lockedOut,
                actions = PinEntryActions(
                    onDigit = { digit ->
                        state.errorText = null
                        state.pin.add(digit)
                    },
                    onBackspace = state.pin::deleteLast,
                ),
                submit = PinSubmit(
                    text = stringResource(Res.string.action_continue),
                    enabled = state.pin.canSubmit,
                    onClick = submit,
                ),
            )
        }
    } else {
        val submit = {
            actions.verifyPassword(state.current.take()) { ok ->
                state.onVerified(ok, actions.lockoutMillis(), Res.string.password_current_wrong)
            }
        }
        PasswordStepScaffold(
            text = PasswordStepText(
                title = stringResource(Res.string.password_current_title),
                subtitle = lockout ?: error ?: stringResource(Res.string.password_current_subtitle),
                label = stringResource(Res.string.lock_password_label),
                subtitleIsError = lockout != null || error != null,
            ),
            input = state.current,
            submit = PasswordStepSubmit(stringResource(Res.string.action_continue), submit),
            enabled = !state.lockedOut,
            onEdit = { state.errorText = null },
            modifier = modifier,
        )
    }
}

@Composable
private fun PasswordEdit(state: PasswordFlowState, actions: PasswordActions, modifier: Modifier = Modifier) {
    val spacing = EnclyTheme.spacing
    val canSave = state.newPassword.canSubmit && state.confirm.canSubmit
    val save = {
        if (canSave) {
            if (state.newPassword.text != state.confirm.text) {
                state.refused(Res.string.password_mismatch)
            } else {
                state.confirm.clear()
                actions.setPassword(state.newPassword.take()) { result ->
                    when (result) {
                        PasswordChange.SET -> state.saved()
                        PasswordChange.INVALID -> state.refused(Res.string.password_too_short)
                        PasswordChange.FAILED -> state.refused(Res.string.password_failed)
                    }
                }
            }
        }
    }
    EnclyScrollColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.m),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.s),
    ) {
        Text(
            text = stringResource(Res.string.password_edit_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        PasswordField(
            input = state.newPassword,
            label = stringResource(Res.string.password_new_label),
            imeAction = ImeAction.Next,
            onEdit = { state.errorText = null },
        )
        PasswordField(
            input = state.confirm,
            label = stringResource(Res.string.password_confirm_label),
            error = state.errorText?.let { stringResource(it) },
            onEdit = { state.errorText = null },
            onDone = save,
        )
        EnclyButton(
            text = stringResource(Res.string.password_save),
            onClick = save,
            enabled = canSave,
            modifier = Modifier.padding(top = spacing.s),
        )
    }
}

@Composable
private fun PasswordOptions(
    state: PasswordFlowState,
    hasPassword: Boolean,
    hasPin: Boolean,
    turnedOff: Boolean,
    actions: PasswordActions,
    modifier: Modifier = Modifier,
) {
    val spacing = EnclyTheme.spacing
    var confirmingRemove by rememberSaveable { mutableStateOf(false) }
    if (confirmingRemove) {
        RemovePasswordDialog(
            onConfirm = {
                confirmingRemove = false
                actions.removePassword { ok ->
                    state.message = if (ok) Res.string.password_removed else Res.string.password_failed
                }
            },
            onDismiss = { confirmingRemove = false },
        )
    }
    EnclyScrollColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.m),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.s),
    ) {
        if (turnedOff && !hasPassword) {
            EnclyCallout(text = stringResource(Res.string.password_turned_off), tone = CalloutTone.WARNING)
        }
        Text(
            text = stringResource(Res.string.password_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        EnclyCallout(text = stringResource(Res.string.password_pin_only_note))
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            modifier = Modifier.fillMaxWidth().padding(top = spacing.s),
        ) {
            EnclyButton(
                text = stringResource(if (hasPassword) Res.string.password_change else Res.string.password_set),
                onClick = { state.step = PasswordStep.EDIT },
            )
            // The last way in stays: each of the two can go only while the other is set.
            if (hasPassword && hasPin) {
                RemoveButton(Res.string.password_remove) { confirmingRemove = true }
                RemoveButton(Res.string.pin_remove) { state.step = PasswordStep.REMOVE_PIN }
            }
        }
    }
}

@Composable
private fun RemoveButton(text: StringResource, onClick: () -> Unit) {
    EnclyTextButton(
        text = stringResource(text),
        onClick = onClick,
        destructive = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RemovePasswordDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    EnclyDialog(
        title = stringResource(Res.string.password_remove_confirm_title),
        text = stringResource(Res.string.password_remove_confirm_body),
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(Res.string.password_remove_confirm), onConfirm, destructive = true),
        dismiss = DialogAction(stringResource(Res.string.cancel), onDismiss),
    )
}

/**
 * Turning the PIN off needs the password typed again: it proves the password opens the vault before the PIN, the
 * other way in, is gone. The wipe and duress PINs go with the PIN.
 */
@Composable
private fun RemovePinStep(state: PasswordFlowState, actions: PasswordActions, modifier: Modifier = Modifier) {
    val error = state.errorText?.let { stringResource(it) }
    val lockout = if (state.lockedOut) pinLockoutText(state.lockoutSeconds) else null
    val submit = {
        actions.verifyPassword(state.current.take()) { ok ->
            if (ok) {
                actions.removePin(state::pinRemoved)
            } else {
                state.onVerified(false, actions.lockoutMillis(), Res.string.password_current_wrong)
            }
        }
    }
    PasswordStepScaffold(
        text = PasswordStepText(
            title = stringResource(Res.string.pin_remove_confirm_title),
            subtitle = lockout ?: error ?: stringResource(Res.string.pin_remove_confirm_body),
            label = stringResource(Res.string.lock_password_label),
            subtitleIsError = lockout != null || error != null,
        ),
        input = state.current,
        submit = PasswordStepSubmit(stringResource(Res.string.pin_remove_confirm), submit),
        enabled = !state.lockedOut,
        onEdit = { state.errorText = null },
        modifier = modifier,
    )
}
