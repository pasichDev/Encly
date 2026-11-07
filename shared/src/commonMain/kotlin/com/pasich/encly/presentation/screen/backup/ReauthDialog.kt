package com.pasich.encly.presentation.screen.backup

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.backup_reauth_subtitle
import com.pasich.encly.core.resources.backup_reauth_title
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.lock_password_label
import com.pasich.encly.core.resources.lock_password_subtitle
import com.pasich.encly.core.resources.lock_use_password
import com.pasich.encly.core.resources.lock_use_pin
import com.pasich.encly.platform.LocalBiometricHost
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.ProtectFromCapture
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
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.BackupStep
import com.pasich.encly.presentation.viewmodel.ReauthFlow
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen PIN or password entry (plus the fingerprint key, when enabled) before an export or import, run
 * by [reauth]; [onCancel] closes it. The one re-authentication every export goes through. A vault with both a PIN
 * and a password asks for the PIN and offers the password under it; with only one, that one.
 */
@Composable
internal fun ReauthDialog(reauth: ReauthFlow, step: BackupStep.Reauth, onCancel: () -> Unit) {
    // Before the Dialog: its own window takes the protection from the activity's when it is made.
    ProtectFromCapture()
    var usePassword by remember { mutableStateOf(!step.methods.pin && step.methods.password) }
    var lockoutSeconds by remember { mutableLongStateOf(0L) }
    // Keyed on the step too: a wrong secret (a new step) may just have started a lockout.
    PinLockoutTicker(step to (lockoutSeconds > 0L), reauth::pinLockoutRemainingMillis) {
        lockoutSeconds = it
    }
    val lockedOut = lockoutSeconds > 0L
    val error = step.error?.let { stringResource(it) }
    val subtitle = when {
        lockedOut -> pinLockoutText(lockoutSeconds)
        error != null -> error
        usePassword -> stringResource(Res.string.lock_password_subtitle)
        else -> stringResource(Res.string.backup_reauth_subtitle)
    }
    val entry = ReauthEntry(
        subtitle = subtitle,
        subtitleIsError = lockedOut || error != null,
        hasError = error != null,
        lockedOut = lockedOut,
        // The other way in, offered when the vault has both.
        onSwitch = { usePassword = !usePassword }.takeIf { step.methods.pin && step.methods.password },
        onCancel = onCancel,
    )
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (usePassword) ReauthPasswordEntry(reauth, entry) else ReauthPinEntry(reauth, step, entry)
    }
}

/** What the two entries of [ReauthDialog] share: the sub-heading, a running lockout and where the buttons go. */
private class ReauthEntry(
    val subtitle: String,
    val subtitleIsError: Boolean,
    val hasError: Boolean,
    val lockedOut: Boolean,
    val onSwitch: (() -> Unit)?,
    val onCancel: () -> Unit,
)

@Composable
private fun ReauthPasswordEntry(reauth: ReauthFlow, entry: ReauthEntry) {
    // Never a String once submitted; dropped when the entry goes (see PasswordInput).
    val password = remember { PasswordInput() }
    DisposableEffect(password) { onDispose { password.clear() } }
    PasswordStepScaffold(
        text = PasswordStepText(
            title = stringResource(Res.string.backup_reauth_title),
            subtitle = entry.subtitle,
            label = stringResource(Res.string.lock_password_label),
            subtitleIsError = entry.subtitleIsError,
        ),
        input = password,
        submit = PasswordStepSubmit(stringResource(Res.string.action_continue)) {
            reauth.submitPassword(password.take())
        },
        enabled = !entry.lockedOut,
        modifier = Modifier.fillMaxSize(),
        footerExtra = {
            entry.onSwitch?.let { switch ->
                EnclyTextButton(
                    text = stringResource(Res.string.lock_use_pin),
                    onClick = {
                        password.clear()
                        switch()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            EnclyTextButton(
                text = stringResource(Res.string.cancel),
                onClick = entry.onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun ReauthPinEntry(reauth: ReauthFlow, step: BackupStep.Reauth, entry: ReauthEntry) {
    val host = LocalBiometricHost.current
    // Wiped once submitted or when the dialog closes, never a String (see PinBuffer).
    val input = remember { PinBuffer() }
    DisposableEffect(input) { onDispose { input.clear() } }
    PinEntryScaffold(
        title = stringResource(Res.string.backup_reauth_title),
        subtitle = entry.subtitle,
        subtitleIsError = entry.subtitleIsError,
        bodyAtBottom = true,
        modifier = Modifier.fillMaxSize(),
    ) {
        PinEntry(
            entered = input.length,
            error = entry.hasError && input.length == 0,
            shakeKey = step.failures,
            enabled = !entry.lockedOut,
            actions = PinEntryActions(
                onDigit = { digit -> if (!entry.lockedOut) input.add(digit) },
                onBackspace = input::deleteLast,
                onBiometric = if (step.biometric && host != null) {
                    { reauth.withBiometric(host) }
                } else {
                    null
                },
            ),
            // The flow wipes the PIN it is given. No auto-submit: a PIN is 6 to 12 digits.
            submit = PinSubmit(
                text = stringResource(Res.string.action_continue),
                enabled = input.canSubmit,
                onClick = { if (input.canSubmit) reauth.submitPin(input.take()) },
            ),
        )
        entry.onSwitch?.let { switch ->
            EnclyTextButton(
                text = stringResource(Res.string.lock_use_password),
                onClick = {
                    input.clear()
                    switch()
                },
                modifier = Modifier.padding(top = EnclyTheme.spacing.s),
            )
        }
        EnclyTextButton(
            text = stringResource(Res.string.cancel),
            onClick = entry.onCancel,
            modifier = Modifier.padding(top = EnclyTheme.spacing.s),
        )
    }
}
