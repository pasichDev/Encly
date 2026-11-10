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
import androidx.compose.ui.window.DialogProperties
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.action_continue
import com.pasich.encly.core.resources.backup_reauth_subtitle
import com.pasich.encly.core.resources.backup_reauth_title
import com.pasich.encly.core.resources.cancel
import com.pasich.encly.platform.LocalBiometricHost
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.ProtectFromCapture
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
 * [onCancel] closes it. The one re-authentication every export goes through.
 */
@Composable
internal fun ReauthDialog(reauth: ReauthFlow, step: BackupStep.Reauth, onCancel: () -> Unit) {
    // Before the Dialog: its own window takes the protection from the activity's when it is made.
    ProtectFromCapture()
    var lockoutSeconds by remember { mutableLongStateOf(0L) }
    // Keyed on the step too: a wrong PIN (a new step) may just have started a lockout.
    PinLockoutTicker(step to (lockoutSeconds > 0L), reauth::pinLockoutRemainingMillis) {
        lockoutSeconds = it
    }
    val lockedOut = lockoutSeconds > 0L
    val error = step.error?.let { stringResource(it) }
    val subtitle = when {
        lockedOut -> pinLockoutText(lockoutSeconds)
        error != null -> error
        else -> stringResource(Res.string.backup_reauth_subtitle)
    }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ReauthPinEntry(
            reauth = reauth,
            step = step,
            entry = ReauthEntry(
                subtitle = subtitle,
                subtitleIsError = lockedOut || error != null,
                hasError = error != null,
                lockedOut = lockedOut,
                onCancel = onCancel,
            ),
        )
    }
}

/** What the entry of [ReauthDialog] shows: the sub-heading, a running lockout and where the cancel button goes. */
private class ReauthEntry(
    val subtitle: String,
    val subtitleIsError: Boolean,
    val hasError: Boolean,
    val lockedOut: Boolean,
    val onCancel: () -> Unit,
)

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
        EnclyTextButton(
            text = stringResource(Res.string.cancel),
            onClick = entry.onCancel,
            modifier = Modifier.padding(top = EnclyTheme.spacing.s),
        )
    }
}
