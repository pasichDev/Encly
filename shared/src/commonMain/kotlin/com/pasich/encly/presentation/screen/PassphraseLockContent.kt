package com.pasich.encly.presentation.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.lock_biometric_invalidated
import com.pasich.encly.core.resources.lock_subtitle
import com.pasich.encly.core.resources.lock_title
import com.pasich.encly.core.resources.lock_unlock
import com.pasich.encly.core.resources.lock_use_recovery_phrase
import com.pasich.encly.core.resources.passphrase_label
import com.pasich.encly.platform.hostString
import com.pasich.encly.platform.secretString
import com.pasich.encly.presentation.designsystem.EnclyTextButton
import com.pasich.encly.presentation.designsystem.authCardApplies
import com.pasich.encly.presentation.screen.pincode.PassphraseStep
import com.pasich.encly.presentation.screen.pincode.PassphraseStepActions
import com.pasich.encly.presentation.screen.pincode.PassphraseStepStyle
import com.pasich.encly.presentation.screen.pincode.PassphraseStepText
import com.pasich.encly.presentation.screen.pincode.PinLockoutTicker
import com.pasich.encly.presentation.screen.pincode.pinLockoutText
import org.jetbrains.compose.resources.stringResource

/**
 * The lock screen where the vault's secret is a passphrase: a masked field and the Unlock button, in place of the PIN
 * dots and keys. Unlock is on as soon as something is typed, and no length is shown or tested here: whichever secret
 * is typed (the vault's, the wipe one, the duress one), the answer and the error are the same, as they are for a PIN.
 * What is typed is taken out of the field when it is sent, so a wrong attempt leaves an empty field.
 */
@Composable
internal fun PassphraseLockContent(
    form: LockFormState,
    capabilities: LockCapabilities,
    busy: Boolean,
    actions: LockActions,
) {
    val kind = capabilities.secretKind
    PinLockoutTicker(form.lockedOut, actions.lockoutRemainingMillis) { form.lockoutSeconds = it }
    // Text typed but never submitted does not outlive the lock screen.
    DisposableEffect(form) { onDispose { form.clearPin() } }
    // The loading view covers the field but not the keyboard: let go of focus while the passphrase is checked.
    ReleaseFocusWhile(busy)
    val submit = {
        if (form.canSubmitPassphrase && !busy) {
            submitSecret(form.takePassphrase(), actions.checks.pin, actions) { r, ms -> form.onPinResult(r, ms) }
        }
    }
    val shownError = form.pinError
    PassphraseStep(
        text = PassphraseStepText(
            title = stringResource(Res.string.lock_title),
            // The sub-heading slot carries the lockout countdown or the last error, as on the PIN pad.
            subtitle = when {
                form.lockedOut -> pinLockoutText(form.lockoutSeconds)
                shownError != null -> secretString(kind, shownError)
                capabilities.biometricInvalidated -> hostString(Res.string.lock_biometric_invalidated)
                else -> secretString(kind, Res.string.lock_subtitle)
            },
            label = stringResource(Res.string.passphrase_label),
            submit = stringResource(Res.string.lock_unlock),
            subtitleIsError = form.lockedOut || shownError != null,
            subtitleTicks = form.lockedOut,
        ),
        input = form.passphrase,
        actions = PassphraseStepActions(onSubmit = submit, onEdit = form::onPassphraseEdited),
        style = PassphraseStepStyle(
            enabled = !form.lockedOut && !busy,
            busy = busy,
            compact = false,
            inCard = authCardApplies(),
        ),
    ) {
        if (capabilities.recoveryAvailable) {
            EnclyTextButton(
                text = stringResource(Res.string.lock_use_recovery_phrase),
                onClick = { form.useRecovery = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
