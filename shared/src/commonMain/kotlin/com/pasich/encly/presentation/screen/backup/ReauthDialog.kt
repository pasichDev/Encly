package com.pasich.encly.presentation.screen.backup

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.backup_reauth_subtitle
import com.pasich.encly.core.resources.backup_reauth_title
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.core.resources.passphrase_label
import com.pasich.encly.core.security.SecretKind
import com.pasich.encly.editor.ui.components.ProtectDialogWindow
import com.pasich.encly.platform.LocalBiometricHost
import com.pasich.encly.platform.secretString
import com.pasich.encly.presentation.designsystem.DialogSystemBarsLikeApp
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.ProtectFromCapture
import com.pasich.encly.presentation.designsystem.edgeToEdgeDialogProperties
import com.pasich.encly.presentation.navigation.LocalVaultLocked
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
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import com.pasich.encly.presentation.viewmodel.BackupStep
import com.pasich.encly.presentation.viewmodel.ReauthFlow
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen PIN entry (plus the fingerprint key, when enabled) before an export or import, run by [reauth];
 * where the secret is a passphrase ([ReauthFlow.secretKind]) it is a field instead. [onCancel] closes it. The one
 * re-authentication every export goes through.
 */
@Composable
internal fun ReauthDialog(reauth: ReauthFlow, step: BackupStep.Reauth, onCancel: () -> Unit, busy: Boolean = false) {
    // Its own window, above the lock shield: nothing of it is composed while the vault is locked.
    if (LocalVaultLocked.current) return
    // Before the Dialog: its own window takes the protection from the activity's when it is made.
    ProtectFromCapture()
    var lockoutSeconds by remember { mutableLongStateOf(0L) }
    // Keyed on the step too: a wrong PIN (a new step) may just have started a lockout.
    PinLockoutTicker(step to (lockoutSeconds > 0L), reauth::pinLockoutRemainingMillis) {
        lockoutSeconds = it
    }
    val lockedOut = lockoutSeconds > 0L
    val kind = reauth.secretKind
    val error = step.error?.let { secretString(kind, it) }
    val subtitle = when {
        lockedOut -> pinLockoutText(lockoutSeconds)
        error != null -> error
        else -> secretString(kind, Res.string.backup_reauth_subtitle)
    }
    Dialog(
        onDismissRequest = onCancel,
        // Edge to edge: the scaffold paints its own surface under the system bars and pads its content by the insets.
        properties = edgeToEdgeDialogProperties(),
    ) {
        ProtectDialogWindow()
        DialogSystemBarsLikeApp()
        ReauthPinEntry(
            reauth = reauth,
            step = step,
            entry = ReauthEntry(
                subtitle = subtitle,
                subtitleIsError = lockedOut || error != null,
                hasError = error != null,
                lockedOut = lockedOut,
                busy = busy,
                onCancel = onCancel,
            ),
        )
    }
}

/** What the entry of [ReauthDialog] shows: the sub-heading, a running lockout or check, and the cancel button. */
private class ReauthEntry(
    val subtitle: String,
    val subtitleIsError: Boolean,
    val hasError: Boolean,
    val lockedOut: Boolean,
    /** A secret is being checked: the keys, the field and the button wait for the answer. */
    val busy: Boolean,
    val onCancel: () -> Unit,
)

@Composable
private fun ReauthPinEntry(reauth: ReauthFlow, step: BackupStep.Reauth, entry: ReauthEntry) {
    if (reauth.secretKind == SecretKind.PASSWORD) {
        ReauthPassphraseEntry(reauth, entry)
        return
    }
    val host = LocalBiometricHost.current
    // Wiped once submitted or when the dialog closes, never a String (see PinBuffer).
    val input = remember { PinBuffer() }
    DisposableEffect(input) { onDispose { input.clear() } }
    PinEntryScaffold(
        title = stringResource(Res.string.backup_reauth_title),
        subtitle = entry.subtitle,
        subtitleIsError = entry.subtitleIsError,
        subtitleTicks = entry.lockedOut,
        busy = entry.busy,
        bodyAtBottom = true,
        modifier = Modifier.fillMaxSize(),
    ) { fit ->
        PinEntry(
            fit = fit,
            entered = input.length,
            error = entry.hasError && input.length == 0,
            shakeKey = step.failures,
            enabled = !entry.lockedOut && !entry.busy,
            actions = PinEntryActions(
                onDigit = { digit ->
                    if (!entry.lockedOut && !entry.busy) {
                        // A new digit takes the last error away, so a repeated wrong PIN is announced again.
                        reauth.clearError()
                        input.add(digit)
                    }
                },
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
                onClick = { if (input.canSubmit && !entry.busy) reauth.submitPin(input.take()) },
            ),
        )
        EnclyTextButton(
            text = stringResource(Res.string.cancel),
            onClick = entry.onCancel,
            modifier = Modifier.padding(top = EnclyTheme.spacing.s),
        )
    }
}

/** The passphrase twin of [ReauthPinEntry]: a field and a button; the flow wipes what it is given. */
@Composable
private fun ReauthPassphraseEntry(reauth: ReauthFlow, entry: ReauthEntry) {
    // Emptied once submitted or when the dialog closes (see PassphraseInput).
    val input = remember { PassphraseInput(reauth.secretMaxUnits) }
    DisposableEffect(input) { onDispose { input.clear() } }
    PassphraseStep(
        text = PassphraseStepText(
            title = stringResource(Res.string.backup_reauth_title),
            subtitle = entry.subtitle,
            label = stringResource(Res.string.passphrase_label),
            submit = stringResource(Res.string.action_continue),
            subtitleIsError = entry.subtitleIsError,
            subtitleTicks = entry.lockedOut,
        ),
        input = input,
        actions = PassphraseStepActions(
            // An edit takes the last error away, so the next wrong passphrase is announced again.
            onEdit = reauth::clearError,
            onSubmit = {
                if (!entry.lockedOut && !entry.busy) {
                    input.submit { typed ->
                        reauth.submitPin(typed)
                        true
                    }
                }
            },
        ),
        style = PassphraseStepStyle(enabled = !entry.lockedOut && !entry.busy, busy = entry.busy, compact = false),
        modifier = Modifier.fillMaxSize(),
    ) {
        EnclyTextButton(
            text = stringResource(Res.string.cancel),
            onClick = entry.onCancel,
            modifier = Modifier.padding(top = EnclyTheme.spacing.s),
        )
    }
}
