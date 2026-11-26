package com.pasich.encly.presentation.screen.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.presentation.designsystem.RecoveryPhraseState
import com.pasich.encly.presentation.screen.pincode.PassphraseInput
import com.pasich.encly.presentation.viewmodel.OnboardingViewModel

/** What the onboarding screen types into: the recovery words (restore) and the passphrase, both held only here. */
internal class OnboardingFields(val restorePhrase: RecoveryPhraseState, val passphrase: PassphraseInput)

/** The passphrase field's state for the life of the screen; whatever is typed is dropped when the screen goes. */
@Composable
internal fun rememberPassphraseInput(maxUnits: Int): PassphraseInput {
    val passphrase = remember { PassphraseInput(maxUnits) }
    DisposableEffect(passphrase) { onDispose { passphrase.clear() } }
    return passphrase
}

/** "Continue" on the passphrase step: the field is emptied when the ViewModel used what it held. */
internal fun PassphraseInput.sendTo(viewModel: OnboardingViewModel) = submit(viewModel::submitPassphrase)

/** "Suggest a passphrase": the field shows the ViewModel's suggestion in clear, and the array is wiped. */
internal fun PassphraseInput.suggestFrom(viewModel: OnboardingViewModel) {
    val suggestion = viewModel.suggestPassphrase() ?: return
    fill(suggestion)
    SensitiveDataCleaner.clear(suggestion)
}
